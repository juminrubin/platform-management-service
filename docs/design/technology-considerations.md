# Backend Framework Design Considerations

| Field | Value |
|-------|--------|
| **Document** | Backend framework comparison (JVM/Kotlin vs Python) and in-process background work |
| **Date** | 2026-08-13 |
| **Status** | Decision record (rev 3 — JobExecutor; not Akka) |
| **Audience** | Engineers evaluating a rewrite, a second implementation, or a background-runtime change |
| **Related** | Current backend: Kotlin 2.3 + Spring Boot 4.1 (`backend/`) |
| **Related design** | [connectors-entra-blob-eventhub.md](./connectors-entra-blob-eventhub.md) |
| **Related code** | `backend/src/main/kotlin/org/jrtech/platformmanagement/jobs/JobExecutor.kt` |

---

## 1. Purpose

This note records why the Platform Management Service API is implemented in **Kotlin / Spring Boot**, and how that choice compares to:

- other **Java / Kotlin** frameworks (Quarkus, Micronaut, Ktor);
- a **Python / FastAPI** implementation, with emphasis on **security**, **latency**, **scalability**, and **concurrent users**.

Rev 3 also records how **in-process background work** is scheduled: a process-wide [`JobExecutor`](../../backend/src/main/kotlin/org/jrtech/platformmanagement/jobs/JobExecutor.kt), Spring `TaskScheduler` for periodic ticks, and the Azure Event Hub processor for live ingest — **not** Akka / Pekko.

It is not a proposal to rewrite the service.

---

## 2. What the backend actually is

The comparison only makes sense against this workload, not against “REST APIs in general.”

| Concern | Reality in this repo |
|---------|----------------------|
| Role | Entra ID **OAuth2 resource server** + catalog CRUD + entitlement check |
| Auth | JWT on every `/api/**` call: signature, issuer, expiry, **audience** (`APP_API_CLIENT_ID` and `api://…`) |
| Authorization | Entra **app roles**, scopes, group → role mapping (`JwtAuthorityMapper`, method security) |
| Hot path | `GET /api/v1/entitlements/check` from an **in-process** concurrent map |
| Background work | Connectors: datasource load, Entra Graph cache, Blob Capture backfill, **Event Hub** live ingest |
| Data plane | Azure Table (prod catalog), Blob, Event Hubs, Microsoft Graph — Managed Identity in production |
| Deploy | Long-lived process on AKS (or Azure Web App), not a per-request function |

Audience validation lives in Spring Boot’s JWT resource-server auto-configuration (`application.yml` → `spring.security.oauth2.resourceserver.jwt.audiences`), not in custom Kotlin. See `backend/src/main/resources/application.yml` and `SecurityConfig.kt`.

The architecture assumes **one process per pod**: one entitlement/group cache, one Event Hub consumer runtime, many concurrent requests.

---

## 3. Decision

**Keep Kotlin + Spring Boot 4.**

A rewrite (JVM or Python) would re-implement the resource-server contract, connector lifecycle, and in-process cache for little product gain. Invest in domain, connectors, and Azure Table — not a framework change.

In-process background work: **`JobExecutor` + `TaskScheduler` + Azure Event Hub processor — not Akka.** See §7.

---

## 4. Why Spring Boot fits

| Need | What Spring Boot provides here |
|------|--------------------------------|
| Entra access tokens | `oauth2ResourceServer().jwt()` + `issuer-uri` / `audiences` |
| Roles, scopes, groups | Filter chain + `@PreAuthorize` + `JwtAuthorityMapper` |
| Actuator, CORS, RFC 7807 | First-class |
| Azure SDKs + Managed Identity | Official Java libraries as beans |
| Event Hub / Blob connectors | Always-on process, not a per-invocation function |
| Tests / JaCoCo / Maven | Already the project’s quality gate |

Boot’s heavier baseline (memory, startup) is small next to Entra, Table, and Event Hubs for this service.

---

## 5. Java / Kotlin alternatives

These stay on the JVM (or Kotlin native I/O) and could reuse Azure Java SDKs and similar JWT libraries. None justify replacing Spring Boot in this repo.

### 5.1 Quarkus / Micronaut

| | vs Spring Boot here |
|--|---------------------|
| Strength | Faster startup, lower idle RSS; good for many tiny pods |
| Security | JWT / OIDC extensions exist; Entra resource-server + method security is a thinner ecosystem than Spring Security |
| Latency | Steady-state request path similar (same JVM, same Nimbus/Azure clients if chosen) |
| Scalability | Same “one process, one cache” model — a good match *if rewritten* |
| Concurrent users | Event loop / virtual threads: many in-flight requests per process, same shared cache |
| Cost of switch | Full rewrite of security, config, and connector lifecycle |

Worth considering only if the constraint is **hundreds of tiny replicas** or aggressive cold-start. This API is a handful of always-on pods.

### 5.2 Ktor

| | vs Spring Boot here |
|--|---------------------|
| Strength | More Kotlin-idiomatic; small core |
| Security | DIY resource server: JWKS, `aud`, filter/plugin coverage, OpenAPI, actuator |
| Latency | Fine; you own the thread/coroutine model |
| Scalability | Same JVM process model as Spring if you keep one engine per pod |
| Concurrent users | Coroutines: high connection concurrency; you own thread-pool vs event-loop trade-offs |
| Cost of switch | You rebuild what `SecurityConfig` + Boot auto-config already do |

Ktor is a language-preference choice, not a security or scale win.

### 5.3 Other JVM-adjacent options (out of scope)

- **ASP.NET Core** — strongest first-party Entra/Azure story, wrong language for this codebase.
- **Azure Functions** — poor fit: entitlement cache and Event Hub ingest want a long-lived process.

---

## 6. Python / FastAPI comparison

FastAPI (Starlette + uvicorn) is a capable async API stack. It can implement this product. It is a **worse default** for *this* architecture on security process and on how the cache/connectors scale.

A competent port would use `azure-identity`, an Entra-aware JWT dependency (`fastapi-azure-auth` or Authlib/PyJWT + JWKS), Pydantic models, and the Azure Python SDKs. That is assumed below — not a naive Flask script.

### 6.1 Security

Spring Boot is the safer default. FastAPI can reach the same bar; more of the resource-server story is a checklist.

| Concern | Spring Boot (current) | FastAPI |
|---------|------------------------|---------|
| Signature, `iss`, `exp` | `NimbusJwtDecoder` from OIDC `issuer-uri` (JWKS + `kid` rotation) | Wire PyJWT/Authlib + JWKS fetch, cache, and rotation |
| Audience | `jwt.audiences`: `${APP_API_CLIENT_ID}` and `api://${APP_API_CLIENT_ID}` — reject **before** controllers | Easy to check `aud` in a dependency; also easy to omit or accept any `aud` |
| Algorithm attacks (`alg=none`, unexpected algs) | Decoder allowlists from metadata | Must pin RS256/ES256 explicitly |
| Route coverage | Resource-server filter on `/api/**`; missing Bearer → 401 | `Depends(auth)` is **opt-in** unless a global dependency is forced |
| Roles / groups / required scope | Filter + `@PreAuthorize` + `JwtAuthorityMapper` | Custom dependencies; no compile-time proof every route applied them |
| Entra resource-server libraries | Spring Security’s model is the industry default for this pattern | `azure-identity` is excellent; **resource-server** libraries are thinner |

**Failure mode to avoid in FastAPI:** missing `aud`, not pinning algorithms, stale JWKS, or a single router that dropped `Depends`. Those are the usual Entra/FastAPI incidents — not “Python cannot do JWT.”

Neither framework encrypts data or replaces Entra. Both are only as good as how the access token is validated.

**Verdict (security):** Spring Boot wins on secure defaults and on not skipping a route. FastAPI matches only with disciplined global auth and the same `iss` / `aud` / JWKS rules Spring already encodes in `application.yml`.

### 6.2 Latency

After warmup, Spring Boot usually wins on **per-request CPU** (JWT verify + JSON). FastAPI is often as good on **I/O wait** (Table, Blob, Graph).

| Path | Spring Boot | FastAPI |
|------|-------------|---------|
| Warm `GET /entitlements/check` | In-process `ConcurrentHashMap` — microseconds | In-process `dict` — also microseconds. **Not a differentiator.** |
| Every authenticated request | RS256 verify in Java/Nimbus; typically faster at high RPS | `cryptography` (C) closes much of the gap; pure-Python JWT will not |
| Connectors (Blob, Event Hub, Graph) | Servlet/MVC + Java 21 virtual threads cover I/O | `asyncio` is a natural fit; not inherently faster Event Hubs |
| Cold start | Slower to first request (JVM) | Usually quicker to first request |
| Steady-state CRUD + JWT | Often lower p50/p99 after JIT | A few ms typical; gap widens under CPU saturation |

**Order-of-magnitude (not a bench):** warm entitlement check ≪ 1 ms in both; JWT + small JSON is ~1–3 ms on a warm JVM vs a few ms on uvicorn, widening when cores are saturated.

If the SLA is “entitlement check in the low milliseconds,” both work. If the need is **high QPS of JWT-authenticated checks on few cores**, Spring Boot has more headroom.

**Verdict (latency):** Tie on the cached check. Spring Boot for JWT-heavy request rate. FastAPI acceptable for Azure I/O.

### 6.3 Scalability

This is where FastAPI is a worse match for **this design**, not for APIs in general.

Horizontal scale is “add AKS pods.” Each pod holds its own entitlement/group cache and runs connectors. That assumes **one process, one cache, many threads**.

| | Spring Boot (1 JVM / pod) | FastAPI (usual production) |
|--|---------------------------|----------------------------|
| Concurrency | Threads / virtual threads, one heap, one cache | `asyncio` is strong for I/O; CPU-bound JWT hits the CPython GIL |
| Using more cores | Larger JVM or more pods | Several **uvicorn workers** = several processes |
| Entitlement cache | One map per pod | One map **per worker** — more RAM, more Table/Graph hydration on start |
| Event Hub consumers | One runtime; partition leases stay coherent | Multiple workers on one pod can compete for the same partitions unless `workers=1` |
| Memory | Higher baseline (this repo already budgets ~512 Mi–1 Gi) | Lower per process; **× worker count** |
| Horizontal scale | Add pods; each loads Table once | Same, but each pod may be N workers |

FastAPI scales well as **many small I/O workers** behind a load balancer. It scales poorly as a drop-in for “in-process cache + Event Hub ingest in one process.”

To copy this architecture in Python you would run **one worker per pod**. Then FastAPI’s usual multi-process advantage disappears, and throughput per pod would likely sit below a tuned Spring instance doing JWT + cache lookups.

Spring’s cost is a larger pod. At a handful of replicas that is cheaper than multiplying cache warm-up and complicating Event Hub leases.

**Verdict (scalability):** Spring Boot matches the single-process cache and connectors. FastAPI’s normal scale-out fights that design.

### 6.4 Concurrent users

“Concurrent users” here means **overlapping in-flight HTTP requests**, not logged-in sessions. The API is **stateless JWT**: MSAL holds tokens in the browser; the backend does not keep a session per human. A technical caller (MI / app registration) is the same — one token, many parallel `fetch`es.

Two populations matter:

| Population | Typical shape | What each request costs |
|------------|---------------|-------------------------|
| Humans (SPA) | Tens to low hundreds of people, bursty clicks | JWT verify + CRUD or cache read; occasional Graph/Table I/O |
| Machines (entitlement check, connectors’ callers) | Many parallel clients, steady or spiky RPS | JWT verify + in-process map lookup (warm path) |

Connectors (Event Hub, Blob, Graph refresh) share the **same process**. They count against the same thread pool / event loop as user requests.

#### How each stack admits concurrency

| | Spring Boot (current) | Other JVM (Quarkus / Micronaut / Ktor) | FastAPI |
|--|------------------------|----------------------------------------|---------|
| Model | One JVM: servlet threads today; Java 21 **virtual threads** available | Often event-loop (Vert.x) or coroutines | One **asyncio** loop **per worker** |
| What “1,000 concurrent users” means | 1,000 parked/running requests in one heap, **one** entitlement cache | Same idea, fewer OS threads if event-loop | 1,000 tasks on one loop **or** split across N workers (N caches) |
| JWT under overlap | Verifies in parallel on many threads; no GIL | Parallel on event-loop workers / VT | Sync verify **blocks that worker’s loop** unless offloaded to a thread pool |
| Shared cache | `ConcurrentHashMap` — many readers, atomic swap on refresh | Same if you use a concurrent map | `dict` is fine for asyncio (one thread) — **not** safe if you add threads without a lock |
| Slow Azure I/O | Platform thread occupied (classic Tomcat) or VT unmounts | Event-loop yields | `async` yields; a **sync** SDK call stalls **all** users on that worker |
| Default ceiling (order of magnitude, one replica) | Classic Tomcat ~200 threads then queue; VT: thousands of I/O-bound requests | Thousands of connections per process | Hundreds–thousands of I/O-wait connections per loop; **JWT CPU** is the real cap |

These are capacity sketches, not load-test results. Measure before setting SLOs.

#### What that implies for this API

- **Tens of concurrent SPA users:** all three stacks are fine. JWT + CRUD will not be the limiter; Azure Table and Entra will.
- **Hundreds of overlapping entitlement checks:** Spring Boot (and other JVM options) keep one cache and burn CPU on JWT in parallel. FastAPI stays smooth only if JWT verify is not sync-on-the-loop; otherwise p99 rises for **every** user on that worker. Scaling FastAPI with more workers duplicates the cache (see §6.3).
- **Thousands of concurrent callers:** both languages need **more pods**. Spring typically needs **fewer** replicas for the same JWT + check throughput because one process uses all cores without cloning state. FastAPI with `workers=1` (required to keep Event Hub leases and a single cache) leaves cores idle unless you run more pods.
- **Mixed load (users + Event Hub ingest):** Spring isolates connector work on other threads/VTs. FastAPI must not put blocking ingest on the API event loop, or interactive users stall when a batch of Avro blobs is decoded.

#### Fairness

A single slow `PUT` that waits on Table should not freeze entitlement checks.

- **Spring Boot:** other requests keep their threads (or other virtual threads). The slow call does not stop JWT + cache hits.
- **FastAPI:** true if the slow call is `await`ed. A blocking Azure or JWT library on the loop becomes a **convoy**: every concurrent user on that worker waits.

**Verdict (concurrent users):** Spring Boot (and Quarkus/Micronaut/Ktor) fit “many overlapping JWTs against one in-process cache.” FastAPI is strong at many *waiting* connections, weaker at many *CPU-bound* authentications in one process, and pays extra when you add workers to recover cores.

---

## 7. In-process background work (JobExecutor vs Akka)

Connectors share the **same JVM** as the HTTP API (one process per pod). Background work must not invent a second runtime, clone the entitlement cache, or fight Event Hub partition leases.

### 7.1 What actually runs in the background

| Work | Shape | Runtime in this repo |
|------|--------|----------------------|
| Avro → Parquet per input file | Discrete, parallel, finite | [`JobExecutor`](../../backend/src/main/kotlin/org/jrtech/platformmanagement/jobs/JobExecutor.kt) — one `ConsumptionBlobFilePipeline` per file (not a Spring bean) |
| Entra Graph group/member refresh | Fixed-delay tick | Spring `TaskScheduler` (`EntraDirectoryConnector`) |
| Datasource / entitlement-check cache rebuild | Fixed-delay tick | Spring `TaskScheduler` (`DatasourceLoadingConnector`) |
| Event Hub live ingest | Long-lived partition consumer | Azure Event Hubs processor + blob checkpoint store |

Two different clocks: **ticks** (run every *N* minutes) and **jobs** (run this file / this chunk, then finish). Mixing them onto one abstraction (actors, or a single scheduler) hides that split.

### 7.2 Decision

**Keep a process-wide `JobExecutor` for discrete parallel work. Keep `TaskScheduler` for periodic connector ticks. Keep the Azure Event Hub processor for streaming. Do not introduce Akka / Pekko.**

`JobExecutor` lives in `org.jrtech.platformmanagement.jobs`. It is a JVM singleton `object`, not a Spring bean. Callers construct their own job/pipeline and `JobExecutor.submit { … }`. Spring only **starts** the pool (`app.jobs.pool-size`, env `APP_JOBS_POOL_SIZE`, default 8) and **stops** it on context shutdown (`JobExecutorConfig`).

A blob job still caps itself with `app.connectors.consumption-blob.max-concurrent-pipelines` (default 4) so one large date range cannot fill the shared pool by itself. Waves of submits stay at that cap; other connectors can still use remaining workers.

### 7.3 Why a shared pool (not a pool per job)

The blob runner originally created `Executors.newFixedThreadPool` for each start and shut it down when the batch finished. That works for one connector. It does not scale as a pattern:

| | Per-job pool | Process-wide `JobExecutor` |
|--|--------------|----------------------------|
| Threads | New set of OS threads every start | One pool for the JVM |
| Other connectors | Each would grow its own pool | Same workers, queue behind in-flight work |
| Shutdown | Easy to leak if a job forgets `shutdown` | One `@PreDestroy` |
| Fairness | A 500-file job can spawn 500 threads if uncapped | Pool size is a process limit; blob adds its own submit cap |
| Tests / ops | Pool identity is per call | `JobExecutor.isRunning()` / `poolSize()` are process facts |

A shared pool matches “one process per pod”: one heap, one cache, one Event Hub consumer, **one** background worker set.

### 7.4 Why not Akka / Pekko

Akka Typed (or Pekko) is a strong fit for **many long-lived entities**, mailbox isolation, supervision trees, and cluster/sharding. This process does not have that shape.

| Need in *this* service | `JobExecutor` + `TaskScheduler` + Azure EH | Akka / Pekko |
|------------------------|---------------------------------------------|--------------|
| Run *N* Avro files in parallel | Submit *N* callables; join | Actor per file, then still wait |
| Cancel a blob job | Cooperative flag between files | Poison / stop messages; extra protocol |
| Hourly Graph / cache refresh | `TaskScheduler` fixed delay | Scheduler + actor; no extra safety |
| Event Hub partitions | Azure processor already owns leases and checkpoints | Actor in front of the SDK is a second runtime |
| Entitlement check hot path | In-process map; no mailbox | Irrelevant |
| Supervision / restart | Connector `start` / `stop` + last-error | Actor supervisor; overkill for four connectors |
| Tests | Submit + assert; JaCoCo already gates this | TestKit, actor system lifecycle next to Spring |
| Dependencies | `java.util.concurrent` | Pekko artifacts, dispatcher config, another mental model |

A stuck Azure Blob or Graph call is still stuck inside an actor. Isolation does not make Table/Blob/Graph faster or safer here.

**Cost of switching:** new dependency, actor-system lifecycle beside Spring Boot, harder connector tests, and no change to JWT, cache, or Event Hub lease semantics.

**When Akka would become relevant:** thousands of **per-tenant or per-partition stateful** processors, with supervision and possibly clustering — not “one Avro file in, one Parquet file out” and not “refresh Graph every 15 minutes.” That is not this API.

### 7.5 Alternatives considered (and left)

| Option | Why not here |
|--------|----------------|
| Spring `@Async` / `ThreadPoolTaskExecutor` bean | Fine for `@Service` methods. Blob pipelines are deliberately **not** Spring beans so a runner can `new` them per file. |
| Java 21 virtual-thread-per-task executor | Good for many blocking I/O tasks. A small **fixed** pool is the better default until we measure Blob/Parquet CPU vs I/O; unbounded VT submit of 500 Avro jobs can still stampede Azure. Revisit if jobs are clearly I/O-wait and the cap is enforced **before** submit. |
| Quartz / ShedLock | Job *scheduling* and cluster locks. We have at most a handful of ticks per process; Spring `TaskScheduler` is enough. Multi-pod coordination for blob is “don’t run the same range twice,” which is an operator/config problem, not a distributed scheduler. |
| WebFlux / Reactor | Event-loop for HTTP. Does not replace a job pool (see §6.2: the hot path is JWT + cache). Not a background-runtime upgrade. |

### 7.6 Verdict (background work)

| Axis | Choice |
|------|--------|
| Discrete parallel jobs | **`JobExecutor`** (`org.jrtech.platformmanagement.jobs`) |
| Periodic connector ticks | **Spring `TaskScheduler`** |
| Live Event Hub | **Azure Event Hubs processor** |
| Actor system (Akka / Pekko) | **Do not add** |
| Pool lifetime | One per JVM; Spring starts/stops only |
| Job / pipeline types | Plain objects; not Spring beans |

**Verdict:** a common `JobExecutor` is the right centralization. Akka would be a new framework for a workload that is already “submit, run, finish” or “tick on a schedule.”

---

## 8. Summary

| Axis | Spring Boot (current) | Other JVM (Quarkus / Micronaut / Ktor) | FastAPI |
|------|------------------------|----------------------------------------|---------|
| Security (Entra resource server) | Strong defaults; `aud` in config | Similar capability; more rewrite | Same bar possible; easier to miss `aud` / a route |
| Latency (warm check) | µs | µs | µs |
| Latency (JWT + CRUD @ high QPS) | Strong | Strong | Weaker per core unless carefully tuned |
| Latency (Azure I/O) | Good (virtual threads) | Good | Good (`asyncio`) |
| Scalability (this cache + EH model) | One process / pod | Same model | Multi-worker conflicts; force `workers=1` to clone this design |
| Concurrent users (stateless JWT) | Many overlapping requests, one cache; JWT in parallel | Event-loop / VT: high in-flight count, same cache | Great at I/O wait; JWT/sync I/O can stall a worker; more workers ⇒ more caches |
| Idle memory / startup | Heavier | Quarkus/Micronaut better | Lighter process; multiplied by workers |
| Fit for this repo | **Chosen** | No rewrite payoff | No rewrite payoff unless the team constraint is Python |
| Background jobs | `JobExecutor` + `TaskScheduler` + Azure EH processor (see §7) | Same split if rewritten | `asyncio` tasks / workers; do not put blocking ingest on the API loop |

---

## 9. Key decisions

1. **Stay on Kotlin + Spring Boot 4.** The service is already a resource server with in-process cache and long-lived Azure connectors — Boot’s model.
2. **Do not treat FastAPI as a security, latency, or concurrency upgrade.** It can be correct and fast enough for modest SPA concurrency; it does not improve Entra validation or the cached check, and many overlapping JWT callers plus workers complicate scale-out.
3. **Do not treat Quarkus/Micronaut/Ktor as required.** They are valid JVM alternatives for greenfield or extreme density; they are not justified mid-flight.
4. **Keep one process per pod** for entitlement cache and Event Hub consumers, regardless of language. Concurrent users share that process; do not multiply workers to absorb them if it clones cache and Event Hub consumers.
5. **Audience remains a framework-level JWT decoder rule** (`APP_API_CLIENT_ID` / `api://…`), not ad-hoc controller code — any alternative must preserve that.
6. **Use a process-wide `JobExecutor`** (`org.jrtech.platformmanagement.jobs`) for discrete parallel work (one Avro file → one Parquet file). It is a JVM singleton, not a Spring bean. Spring only starts (`app.jobs.pool-size`) and stops the pool. Callers `new` the job and `submit`.
7. **Keep Spring `TaskScheduler` for periodic ticks** (Entra Graph, datasource cache). Do not move those onto `JobExecutor` or Akka.
8. **Do not introduce Akka / Pekko.** This workload is submit-run-finish and scheduled ticks, not thousands of long-lived supervised entities. An actor system would add a second runtime next to Spring without changing JWT, cache, or Event Hub leases.
9. **Cap a single blob job** with `app.connectors.consumption-blob.max-concurrent-pipelines` so one date range cannot occupy the entire shared pool.

---

## 10. Implications

No implementation work follows from the framework comparison. Background work already follows §7 (`JobExecutor`, `TaskScheduler`, Azure Event Hub processor).

A future Python or alternate-JVM service would be a **new** codebase that must re-specify:

- JWT `iss` / `aud` / JWKS parity with `application.yml`;
- global (not opt-in) authentication on `/api/**`;
- single worker/process per replica if the in-process cache and Event Hub consumer are retained;
- the same background split: one process-wide job pool for discrete work, a scheduler for ticks, and the Event Hub SDK for live ingest — not an actor system unless the product becomes many long-lived stateful processors.
