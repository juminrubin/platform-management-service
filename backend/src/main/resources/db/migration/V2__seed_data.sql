-- Sample participants (business string IDs)
INSERT INTO participant (id, name, contact, status, created_at, updated_at) VALUES
    ('acme-corp', 'Acme Corporation', 'ops@acme.example', 'ACTIVE',
     TIMESTAMP '2024-01-15 10:00:00+00', TIMESTAMP '2024-01-15 10:00:00+00'),
    ('beta-industries', 'Beta Industries', 'admin@beta.example', 'ACTIVE',
     TIMESTAMP '2024-02-01 09:30:00+00', TIMESTAMP '2024-02-01 09:30:00+00'),
    ('gamma-partners', 'Gamma Partners', 'contact@gamma.example', 'INACTIVE',
     TIMESTAMP '2024-03-10 14:00:00+00', TIMESTAMP '2024-06-01 08:00:00+00');

-- Sample service offerings (id = model / deployment key)
INSERT INTO service_offering (id, name, description, category, config, active, created_at, updated_at) VALUES
    ('gpt-5.1-mini', 'GPT 5.1 Mini',
     'Cost-efficient chat completion model',
     'LLM',
     '{"deployment_endpoint":"https://aoai.example/gpt-5.1-mini","deployment_id":"gpt-5.1-mini","default_max_tpm":60000,"default_max_rpm":60}',
     TRUE,
     TIMESTAMP '2024-01-01 00:00:00+00', TIMESTAMP '2024-01-01 00:00:00+00'),
    ('gpt-5.1', 'GPT 5.1',
     'Flagship chat completion model',
     'LLM',
     '{"deployment_endpoint":"https://aoai.example/gpt-5.1","deployment_id":"gpt-5.1","default_max_tpm":120000,"default_max_rpm":120}',
     TRUE,
     TIMESTAMP '2024-01-01 00:00:00+00', TIMESTAMP '2024-01-01 00:00:00+00'),
    ('az-whisper-stt', 'Azure Whisper STT',
     'Speech-to-text transcription',
     'SPEECH',
     '{"deployment_endpoint":"https://aoai.example/whisper","deployment_id":"az-whisper-stt","default_max_tpm":null,"default_max_rpm":30}',
     TRUE,
     TIMESTAMP '2024-01-01 00:00:00+00', TIMESTAMP '2024-01-01 00:00:00+00'),
    ('legacy-batch', 'Legacy Batch Processing',
     'Deprecated batch job runner',
     'PLATFORM',
     '{"deployment_endpoint":null,"deployment_id":"legacy-batch"}',
     FALSE,
     TIMESTAMP '2023-06-01 00:00:00+00', TIMESTAMP '2024-05-01 00:00:00+00');

-- Sample entitlements
INSERT INTO participant_service_entitlement
    (id, participant_id, service_offering_id, status, valid_from, valid_to, config, notes, created_at, updated_at)
VALUES
    ('e1111111-1111-1111-1111-111111111111',
     'acme-corp', 'gpt-5.1',
     'ACTIVE', DATE '2024-01-15', DATE '2025-12-31',
     '{"max_tpm":100000,"max_rpm":100}',
     'Enterprise tier chat',
     TIMESTAMP '2024-01-15 10:05:00+00', TIMESTAMP '2024-01-15 10:05:00+00'),
    ('e2222222-2222-2222-2222-222222222222',
     'acme-corp', 'az-whisper-stt',
     'ACTIVE', DATE '2024-01-15', NULL,
     '{"max_rpm":60}',
     'Unlimited STT during pilot',
     TIMESTAMP '2024-01-15 10:06:00+00', TIMESTAMP '2024-01-15 10:06:00+00'),
    ('e3333333-3333-3333-3333-333333333333',
     'beta-industries', 'gpt-5.1-mini',
     'ACTIVE', DATE '2024-02-01', DATE '2025-06-30',
     '{"max_tpm":10000,"max_rpm":20}',
     'Standard mini package',
     TIMESTAMP '2024-02-01 09:35:00+00', TIMESTAMP '2024-02-01 09:35:00+00'),
    ('e4444444-4444-4444-4444-444444444444',
     'beta-industries', 'gpt-5.1',
     'PENDING', DATE '2025-01-01', DATE '2025-12-31',
     '{"max_tpm":50000,"max_rpm":50}',
     'Awaiting contract signature',
     TIMESTAMP '2024-11-01 12:00:00+00', TIMESTAMP '2024-11-01 12:00:00+00'),
    ('e5555555-5555-5555-5555-555555555555',
     'gamma-partners', 'az-whisper-stt',
     'REVOKED', DATE '2024-03-10', DATE '2024-05-31',
     '{"max_rpm":10}',
     'Revoked due to inactivity',
     TIMESTAMP '2024-03-10 14:10:00+00', TIMESTAMP '2024-06-01 08:00:00+00');

-- Sample caller identities
INSERT INTO participant_caller_id
    (id, participant_id, caller_id, status, created_at, updated_at)
VALUES
    ('c1111111-1111-1111-1111-111111111111',
     'acme-corp', 'alice@acme.example', 'ACTIVE',
     TIMESTAMP '2024-01-16 08:00:00+00', TIMESTAMP '2024-01-16 08:00:00+00'),
    ('c2222222-2222-2222-2222-222222222222',
     'acme-corp', '11111111-2222-3333-4444-555555555555', 'ACTIVE',
     TIMESTAMP '2024-01-16 08:05:00+00', TIMESTAMP '2024-01-16 08:05:00+00'),
    ('c3333333-3333-3333-3333-333333333333',
     'beta-industries', 'bob@beta.example', 'ACTIVE',
     TIMESTAMP '2024-02-02 10:00:00+00', TIMESTAMP '2024-02-02 10:00:00+00'),
    ('c4444444-4444-4444-4444-444444444444',
     'gamma-partners', 'carol@gamma.example', 'INACTIVE',
     TIMESTAMP '2024-03-11 09:00:00+00', TIMESTAMP '2024-06-01 08:00:00+00');

-- Sample consumption records
INSERT INTO participant_call_consumption
    (id, participant_caller_id, service_offering_id, consumption_data, created_at)
VALUES
    ('d1111111-1111-1111-1111-111111111111',
     'c1111111-1111-1111-1111-111111111111',
     'gpt-5.1',
     '{"endpoint_url":"https://aoai.example/gpt-5.1/chat","input_token":1200,"output_token":340,"cache_token":50}',
     TIMESTAMP '2024-06-01 12:00:00+00'),
    ('d2222222-2222-2222-2222-222222222222',
     'c2222222-2222-2222-2222-222222222222',
     'gpt-5.1',
     '{"endpoint_url":"https://aoai.example/gpt-5.1/chat","input_token":5000,"output_token":800,"cache_token":0}',
     TIMESTAMP '2024-06-01 12:05:00+00'),
    ('d3333333-3333-3333-3333-333333333333',
     'c3333333-3333-3333-3333-333333333333',
     'gpt-5.1-mini',
     '{"endpoint_url":"https://aoai.example/gpt-5.1-mini/chat","input_token":900,"output_token":200,"cache_token":10}',
     TIMESTAMP '2024-06-02 09:15:00+00');
