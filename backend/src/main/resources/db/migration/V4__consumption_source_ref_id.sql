-- Source Reference Identification: unique key supplied by consumption reporters
-- (e.g. request UUID) to identify a particular consumption event.
ALTER TABLE participant_call_consumption
    ADD COLUMN source_ref_id VARCHAR(255);

-- Unique when present; multiple NULLs allowed (legacy / optional reporters).
CREATE UNIQUE INDEX uq_consumption_source_ref_id
    ON participant_call_consumption (source_ref_id);

-- Backfill sample seed rows (ids from V2) so demos have stable source refs.
UPDATE participant_call_consumption
SET source_ref_id = 'req-' || CAST(id AS VARCHAR)
WHERE source_ref_id IS NULL
  AND id IN (
      'd1111111-1111-1111-1111-111111111111',
      'd2222222-2222-2222-2222-222222222222',
      'd3333333-3333-3333-3333-333333333333'
  );
