#!/usr/bin/env python3
"""
Seed Platform Management catalog data from a datasource JSON file.

Catalog shape matches scripts/fixtures/datasource.json:

  {
    "services": [ { id, name, category, description?, tags?, provider?, config?, active? } ],
    "participants": [
      {
        id, name, contact?, status?,
        callers: [ { id, label?, status? } ],
        entitlements: [ { serviceId, status?, validFrom, validTo?, config?, notes? } ]
      }
    ]
  }

Two backends:

  table  Write Azure Table Storage entities (production multi-node store).
  api    Call the running Platform Management REST API (local in-memory or Table-backed).

Examples:

  # Azure Table (connection string — Azurite or account key)
  export AZURE_STORAGE_CONNECTION_STRING='...'
  ./scripts/seed-datasource.py --mode table \\
      --file scripts/fixtures/datasource.json

  # Azure Table (account URL + DefaultAzureCredential / env credentials)
  export APP_AZURE_TABLE_ENDPOINT=https://myaccount.table.core.windows.net
  ./scripts/seed-datasource.py --mode table --file scripts/fixtures/datasource.json

  # REST API (running backend; requires System.Maintainer token)
  export TOKEN=$(./scripts/get-token-human.sh)
  ./scripts/seed-datasource.py --mode api \\
      --file scripts/fixtures/datasource.json \\
      --base-url http://localhost:8080 \\
      --token "$TOKEN"

  # Dry-run (parse + print plan only)
  ./scripts/seed-datasource.py --mode table --file scripts/fixtures/datasource.json --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional


# Partition keys / table logical names (must match backend AzureTable* repositories)
PK_SERVICE = "service"
PK_PARTICIPANT = "participant"
PK_CALLER = "caller"

DEFAULT_TABLE_PREFIX = "pms"
DEFAULT_SERVICES_TABLE = "services"
DEFAULT_PARTICIPANTS_TABLE = "participants"
DEFAULT_CALLERS_TABLE = "callers"
DEFAULT_ENTITLEMENTS_TABLE = "entitlements"

ACTOR = "SYSTEM-seed"


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def normalize_config(raw: Any) -> str:
    """Empty → {}; leave non-empty strings as-is (fixture may use single-quoted pseudo-JSON)."""
    if raw is None:
        return "{}"
    s = str(raw).strip()
    return s if s else "{}"


def normalize_provider(raw: Any) -> str:
    s = (str(raw).strip().upper() if raw is not None else "") or "SYSTEM"
    return s


def table_name(prefix: str, logical: str) -> str:
    p = (prefix or "").strip().rstrip()
    n = logical.strip()
    return n if not p else f"{p}{n}"


@dataclass
class Counters:
    services_created: int = 0
    services_skipped: int = 0
    participants_created: int = 0
    participants_skipped: int = 0
    callers_created: int = 0
    callers_skipped: int = 0
    entitlements_created: int = 0
    entitlements_skipped: int = 0
    errors: list[str] = field(default_factory=list)

    def summary(self) -> str:
        return (
            f"services +{self.services_created}/skip {self.services_skipped}, "
            f"participants +{self.participants_created}/skip {self.participants_skipped}, "
            f"callers +{self.callers_created}/skip {self.callers_skipped}, "
            f"entitlements +{self.entitlements_created}/skip {self.entitlements_skipped}"
            + (f", errors={len(self.errors)}" if self.errors else "")
        )


def load_document(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        doc = json.load(f)
    if not isinstance(doc, dict):
        raise SystemExit(f"Root JSON must be an object: {path}")
    doc.setdefault("services", [])
    doc.setdefault("participants", [])
    if not isinstance(doc["services"], list) or not isinstance(doc["participants"], list):
        raise SystemExit("services and participants must be arrays")
    return doc


# ─── Azure Table backend ─────────────────────────────────────────────────────


def build_table_service_client(
    connection_string: Optional[str],
    endpoint: Optional[str],
):
    try:
        from azure.data.tables import TableServiceClient
    except ImportError as ex:
        raise SystemExit(
            "Missing dependency: azure-data-tables\n"
            "  pip install -r scripts/requirements-seed.txt"
        ) from ex

    conn = (connection_string or os.environ.get("AZURE_STORAGE_CONNECTION_STRING")
            or os.environ.get("APP_AZURE_TABLE_CONNECTION_STRING") or "").strip()
    ep = (endpoint or os.environ.get("APP_AZURE_TABLE_ENDPOINT") or "").strip()

    if conn:
        return TableServiceClient.from_connection_string(conn)
    if ep:
        try:
            from azure.identity import DefaultAzureCredential
        except ImportError as ex:
            raise SystemExit(
                "Missing dependency: azure-identity (needed for endpoint + MI/credential auth)\n"
                "  pip install -r scripts/requirements-seed.txt"
            ) from ex
        return TableServiceClient(endpoint=ep, credential=DefaultAzureCredential())
    raise SystemExit(
        "Azure Table mode requires either:\n"
        "  AZURE_STORAGE_CONNECTION_STRING / APP_AZURE_TABLE_CONNECTION_STRING, or\n"
        "  APP_AZURE_TABLE_ENDPOINT (+ DefaultAzureCredential env / az login)"
    )


def ensure_tables(service, names: list[str], create: bool) -> None:
    if not create:
        return
    for name in names:
        try:
            service.create_table_if_not_exists(name)
            print(f"  table ensured: {name}")
        except Exception as ex:  # noqa: BLE001
            print(f"  warning: could not create table {name}: {ex}", file=sys.stderr)


def seed_table(
    doc: dict[str, Any],
    *,
    connection_string: Optional[str],
    endpoint: Optional[str],
    table_prefix: str,
    create_tables: bool,
    skip_existing: bool,
    dry_run: bool,
) -> Counters:
    counters = Counters()
    now = utc_now_iso()

    t_services = table_name(table_prefix, DEFAULT_SERVICES_TABLE)
    t_participants = table_name(table_prefix, DEFAULT_PARTICIPANTS_TABLE)
    t_callers = table_name(table_prefix, DEFAULT_CALLERS_TABLE)
    t_entitlements = table_name(table_prefix, DEFAULT_ENTITLEMENTS_TABLE)

    if dry_run:
        print("[dry-run] would write to Azure tables:")
        for t in (t_services, t_participants, t_callers, t_entitlements):
            print(f"  - {t}")
        print(f"  services={len(doc['services'])} participants={len(doc['participants'])}")
        return counters

    from azure.data.tables import TableEntity, UpdateMode
    from azure.core.exceptions import ResourceExistsError, ResourceNotFoundError, HttpResponseError

    service = build_table_service_client(connection_string, endpoint)
    ensure_tables(service, [t_services, t_participants, t_callers, t_entitlements], create_tables)

    services_client = service.get_table_client(t_services)
    participants_client = service.get_table_client(t_participants)
    callers_client = service.get_table_client(t_callers)
    entitlements_client = service.get_table_client(t_entitlements)

    def exists(client, pk: str, rk: str) -> bool:
        try:
            client.get_entity(pk, rk)
            return True
        except ResourceNotFoundError:
            return False
        except HttpResponseError as ex:
            if ex.status_code == 404:
                return False
            raise

    def upsert(client, entity: TableEntity, *, is_new_check_pk: str, is_new_check_rk: str) -> bool:
        """Returns True if created (or upserted when not skip_existing)."""
        if skip_existing and exists(client, is_new_check_pk, is_new_check_rk):
            return False
        client.upsert_entity(entity, mode=UpdateMode.MERGE)
        return True

    # Services
    for item in doc["services"]:
        sid = str(item.get("id", "")).strip()
        if not sid:
            counters.errors.append("skip service with blank id")
            continue
        entity = TableEntity(partition_key=PK_SERVICE, row_key=sid)
        entity["name"] = str(item.get("name", sid)).strip()
        entity["description"] = (str(item["description"]).strip() if item.get("description") else None)
        entity["category"] = str(item.get("category", "UNCATEGORIZED")).strip()
        entity["provider"] = normalize_provider(item.get("provider"))
        entity["config"] = normalize_config(item.get("config"))
        entity["active"] = bool(item.get("active", True))
        entity["createdBy"] = ACTOR
        entity["updatedBy"] = ACTOR
        entity["createdAt"] = now
        entity["updatedAt"] = now
        try:
            if upsert(services_client, entity, is_new_check_pk=PK_SERVICE, is_new_check_rk=sid):
                counters.services_created += 1
                print(f"  service + {sid}")
            else:
                counters.services_skipped += 1
                print(f"  service = {sid} (exists)")
        except Exception as ex:  # noqa: BLE001
            counters.errors.append(f"service {sid}: {ex}")
            print(f"  service ! {sid}: {ex}", file=sys.stderr)

    # Participants + nested callers / entitlements
    for p in doc["participants"]:
        pid = str(p.get("id", "")).strip()
        if not pid:
            counters.errors.append("skip participant with blank id")
            continue
        entity = TableEntity(partition_key=PK_PARTICIPANT, row_key=pid)
        entity["name"] = str(p.get("name", pid)).strip()
        entity["contact"] = (str(p["contact"]).strip() if p.get("contact") else None)
        entity["status"] = str(p.get("status", "ACTIVE")).strip().upper() or "ACTIVE"
        entity["createdBy"] = ACTOR
        entity["updatedBy"] = ACTOR
        entity["createdAt"] = now
        entity["updatedAt"] = now
        try:
            if upsert(participants_client, entity, is_new_check_pk=PK_PARTICIPANT, is_new_check_rk=pid):
                counters.participants_created += 1
                print(f"  participant + {pid}")
            else:
                counters.participants_skipped += 1
                print(f"  participant = {pid} (exists)")
        except Exception as ex:  # noqa: BLE001
            counters.errors.append(f"participant {pid}: {ex}")
            print(f"  participant ! {pid}: {ex}", file=sys.stderr)
            continue

        for c in p.get("callers") or []:
            cid = str(c.get("id", "")).strip()
            if not cid:
                counters.errors.append(f"skip blank caller under {pid}")
                continue
            ce = TableEntity(partition_key=PK_CALLER, row_key=cid)
            ce["participantId"] = pid
            ce["status"] = str(c.get("status", "ACTIVE")).strip().upper() or "ACTIVE"
            ce["createdBy"] = ACTOR
            ce["updatedBy"] = ACTOR
            ce["createdAt"] = now
            ce["updatedAt"] = now
            try:
                if upsert(callers_client, ce, is_new_check_pk=PK_CALLER, is_new_check_rk=cid):
                    counters.callers_created += 1
                    print(f"  caller + {cid} → {pid}")
                else:
                    counters.callers_skipped += 1
                    print(f"  caller = {cid} (exists)")
            except Exception as ex:  # noqa: BLE001
                counters.errors.append(f"caller {cid}: {ex}")
                print(f"  caller ! {cid}: {ex}", file=sys.stderr)

        for e in p.get("entitlements") or []:
            soid = str(e.get("serviceId", "")).strip()
            if not soid:
                counters.errors.append(f"skip blank serviceId entitlement under {pid}")
                continue
            ee = TableEntity(partition_key=pid, row_key=soid)
            ee["id"] = str(uuid.uuid4())
            ee["status"] = str(e.get("status", "ACTIVE")).strip().upper() or "ACTIVE"
            ee["validFrom"] = str(e.get("validFrom", "")).strip()
            if not ee["validFrom"]:
                counters.errors.append(f"entitlement {pid}/{soid}: validFrom required")
                continue
            valid_to = e.get("validTo")
            ee["validTo"] = str(valid_to).strip() if valid_to not in (None, "") else None
            ee["config"] = normalize_config(e.get("config"))
            notes = e.get("notes")
            ee["notes"] = str(notes).strip() if notes not in (None, "") else None
            ee["createdBy"] = ACTOR
            ee["updatedBy"] = ACTOR
            ee["createdAt"] = now
            ee["updatedAt"] = now
            try:
                if upsert(entitlements_client, ee, is_new_check_pk=pid, is_new_check_rk=soid):
                    counters.entitlements_created += 1
                    print(f"  entitlement + {pid}/{soid}")
                else:
                    counters.entitlements_skipped += 1
                    print(f"  entitlement = {pid}/{soid} (exists)")
            except Exception as ex:  # noqa: BLE001
                counters.errors.append(f"entitlement {pid}/{soid}: {ex}")
                print(f"  entitlement ! {pid}/{soid}: {ex}", file=sys.stderr)

    # silence unused import warnings for ResourceExistsError if any
    _ = ResourceExistsError
    return counters


# ─── REST API backend ────────────────────────────────────────────────────────


def seed_api(
    doc: dict[str, Any],
    *,
    base_url: str,
    token: str,
    skip_existing: bool,
    dry_run: bool,
) -> Counters:
    try:
        import urllib.error
        import urllib.request
    except ImportError as ex:
        raise SystemExit(f"urllib unavailable: {ex}") from ex

    counters = Counters()
    base = base_url.rstrip("/")

    def request(method: str, path: str, body: Optional[dict] = None) -> tuple[int, Any]:
        url = f"{base}{path}"
        data = None
        headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
        }
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read().decode("utf-8")
                payload = json.loads(raw) if raw.strip() else None
                return resp.status, payload
        except urllib.error.HTTPError as ex:
            err_body = ex.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(err_body) if err_body else None
            except json.JSONDecodeError:
                payload = err_body
            return ex.code, payload

    if dry_run:
        print(f"[dry-run] would POST to API {base}")
        print(f"  services={len(doc['services'])} participants={len(doc['participants'])}")
        return counters

    # Services
    for item in doc["services"]:
        sid = str(item.get("id", "")).strip()
        if not sid:
            continue
        body = {
            "id": sid,
            "name": str(item.get("name", sid)).strip(),
            "description": (str(item["description"]).strip() if item.get("description") else None),
            "category": str(item.get("category", "UNCATEGORIZED")).strip(),
            "provider": normalize_provider(item.get("provider")),
            "config": normalize_config(item.get("config")),
            "active": bool(item.get("active", True)),
        }
        status, _ = request("POST", "/api/v1/service-offerings", body)
        if status in (200, 201):
            counters.services_created += 1
            print(f"  service + {sid}")
        elif status == 409 and skip_existing:
            counters.services_skipped += 1
            print(f"  service = {sid} (exists)")
        else:
            counters.errors.append(f"service {sid}: HTTP {status}")
            print(f"  service ! {sid}: HTTP {status}", file=sys.stderr)

    # Participants
    for p in doc["participants"]:
        pid = str(p.get("id", "")).strip()
        if not pid:
            continue
        body = {
            "id": pid,
            "name": str(p.get("name", pid)).strip(),
            "contact": (str(p["contact"]).strip() if p.get("contact") else None),
            "status": str(p.get("status", "ACTIVE")).strip().upper() or "ACTIVE",
        }
        status, _ = request("POST", "/api/v1/participants", body)
        if status in (200, 201):
            counters.participants_created += 1
            print(f"  participant + {pid}")
        elif status == 409 and skip_existing:
            counters.participants_skipped += 1
            print(f"  participant = {pid} (exists)")
        else:
            counters.errors.append(f"participant {pid}: HTTP {status}")
            print(f"  participant ! {pid}: HTTP {status}", file=sys.stderr)
            # still try nested if participant already exists
            if status != 409:
                continue

        for c in p.get("callers") or []:
            cid = str(c.get("id", "")).strip()
            if not cid:
                continue
            cbody = {
                "participantId": pid,
                "callerId": cid,
                "status": str(c.get("status", "ACTIVE")).strip().upper() or "ACTIVE",
            }
            status, _ = request("POST", "/api/v1/caller-registrations", cbody)
            if status in (200, 201):
                counters.callers_created += 1
                print(f"  caller + {cid} → {pid}")
            elif status == 409 and skip_existing:
                counters.callers_skipped += 1
                print(f"  caller = {cid} (exists)")
            else:
                counters.errors.append(f"caller {cid}: HTTP {status}")
                print(f"  caller ! {cid}: HTTP {status}", file=sys.stderr)

        for e in p.get("entitlements") or []:
            soid = str(e.get("serviceId", "")).strip()
            if not soid:
                continue
            ebody = {
                "participantId": pid,
                "serviceOfferingId": soid,
                "status": str(e.get("status", "ACTIVE")).strip().upper() or "ACTIVE",
                "validFrom": str(e.get("validFrom", "")).strip(),
                "validTo": (str(e["validTo"]).strip() if e.get("validTo") not in (None, "") else None),
                "config": normalize_config(e.get("config")),
                "notes": (str(e["notes"]).strip() if e.get("notes") not in (None, "") else None),
            }
            if not ebody["validFrom"]:
                counters.errors.append(f"entitlement {pid}/{soid}: validFrom required")
                continue
            status, _ = request("POST", "/api/v1/entitlements", ebody)
            if status in (200, 201):
                counters.entitlements_created += 1
                print(f"  entitlement + {pid}/{soid}")
            elif status == 409 and skip_existing:
                counters.entitlements_skipped += 1
                print(f"  entitlement = {pid}/{soid} (exists)")
            else:
                counters.errors.append(f"entitlement {pid}/{soid}: HTTP {status}")
                print(f"  entitlement ! {pid}/{soid}: HTTP {status}", file=sys.stderr)

    # Optionally refresh check cache so checks see new data immediately
    status, _ = request("POST", "/api/v1/entitlements/cache/refresh")
    if status == 200:
        print("  cache refresh OK")
    else:
        print(f"  cache refresh skipped/failed: HTTP {status}", file=sys.stderr)

    return counters


# ─── CLI ─────────────────────────────────────────────────────────────────────


def default_file() -> Path:
    root = Path(__file__).resolve().parent
    return root / "fixtures" / "datasource.json"


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Seed Platform Management catalog from a datasource JSON file.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    p.add_argument(
        "--file", "-f",
        type=Path,
        default=default_file(),
        help=f"Path to datasource JSON (default: {default_file()})",
    )
    p.add_argument(
        "--mode", "-m",
        choices=("table", "api"),
        default="table",
        help="Backend: Azure Table (table) or REST API (api). Default: table",
    )
    p.add_argument(
        "--skip-existing",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Skip entities that already exist (default: true). Use --no-skip-existing to upsert/overwrite where supported.",
    )
    p.add_argument("--dry-run", action="store_true", help="Parse JSON and print plan only")

    # Table options
    p.add_argument(
        "--connection-string",
        default=None,
        help="Azure Storage connection string (else AZURE_STORAGE_CONNECTION_STRING / APP_AZURE_TABLE_CONNECTION_STRING)",
    )
    p.add_argument(
        "--endpoint",
        default=None,
        help="Azure Table endpoint URL (else APP_AZURE_TABLE_ENDPOINT)",
    )
    p.add_argument(
        "--table-prefix",
        default=os.environ.get("APP_AZURE_TABLE_PREFIX", DEFAULT_TABLE_PREFIX),
        help=f"Table name prefix matching app.azure-table.table-prefix (default: {DEFAULT_TABLE_PREFIX})",
    )
    p.add_argument(
        "--create-tables",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Create tables if missing (table mode; default: true)",
    )

    # API options
    p.add_argument(
        "--base-url",
        default=os.environ.get("API_BASE_URL", "http://localhost:8080"),
        help="API base URL for --mode api (default: http://localhost:8080 or API_BASE_URL)",
    )
    p.add_argument(
        "--token",
        default=os.environ.get("TOKEN"),
        help="Bearer token for --mode api (else TOKEN env). Needs System.Maintainer.",
    )
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    path = args.file.expanduser().resolve()
    if not path.is_file():
        print(f"File not found: {path}", file=sys.stderr)
        return 2

    print(f"Loading {path}")
    doc = load_document(path)
    print(
        f"Document: {len(doc['services'])} service(s), "
        f"{len(doc['participants'])} participant(s)"
    )

    if args.mode == "table":
        print(f"Mode: Azure Table (prefix={args.table_prefix!r})")
        counters = seed_table(
            doc,
            connection_string=args.connection_string,
            endpoint=args.endpoint,
            table_prefix=args.table_prefix,
            create_tables=args.create_tables,
            skip_existing=args.skip_existing,
            dry_run=args.dry_run,
        )
    else:
        if not args.token and not args.dry_run:
            print(
                "API mode requires --token or TOKEN env (System.Maintainer JWT).\n"
                "  eval \"$(./scripts/get-token-human.sh --export)\"",
                file=sys.stderr,
            )
            return 2
        print(f"Mode: REST API ({args.base_url})")
        counters = seed_api(
            doc,
            base_url=args.base_url,
            token=args.token or "",
            skip_existing=args.skip_existing,
            dry_run=args.dry_run,
        )

    print(f"Done: {counters.summary()}")
    if counters.errors:
        print("Errors:", file=sys.stderr)
        for e in counters.errors:
            print(f"  - {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
