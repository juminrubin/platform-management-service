-- Placeholder retained for migration history continuity.
-- Status CHECK constraints were removed from V1 (H2 2.4 + Hibernate 7 Instant binding
-- could report spurious CHECK failures). Enum validity is enforced in the application layer.
SELECT 1;
