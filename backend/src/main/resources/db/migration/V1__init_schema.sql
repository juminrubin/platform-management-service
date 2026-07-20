-- Participants: billing groups for one or more caller registrations
CREATE TABLE participant (
    id              VARCHAR(40)     NOT NULL PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    contact         VARCHAR(255),
    status          VARCHAR(32)     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_participant_name UNIQUE (name)
);

CREATE INDEX idx_participant_status ON participant (status);

-- Catalog of services that can be offered / entitled
-- id is a stable business key, e.g. gpt-5.1-mini, gpt-5.1, az-whisper-stt
CREATE TABLE service_offering (
    id              VARCHAR(100)    NOT NULL PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    description     VARCHAR(1000),
    category        VARCHAR(64)     NOT NULL,
    -- JSON: deployment_endpoint, deployment_id, default_max_tpm, default_max_rpm, ...
    config          VARCHAR(5000)   NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_service_offering_category ON service_offering (category);
CREATE INDEX idx_service_offering_active ON service_offering (active);

-- Entitlement linking a participant to a service offering
CREATE TABLE participant_service_entitlement (
    id                      UUID            NOT NULL PRIMARY KEY,
    participant_id          VARCHAR(40)     NOT NULL,
    service_offering_id     VARCHAR(100)    NOT NULL,
    status                  VARCHAR(32)     NOT NULL,
    valid_from              DATE            NOT NULL,
    valid_to                DATE,
    -- JSON: entitlement specific max_tpm, max_rpm, ...
    config                  VARCHAR(5000)   NOT NULL,
    notes                   VARCHAR(500),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_entitlement_participant
        FOREIGN KEY (participant_id) REFERENCES participant (id),
    CONSTRAINT fk_entitlement_service_offering
        FOREIGN KEY (service_offering_id) REFERENCES service_offering (id),
    CONSTRAINT uq_entitlement_participant_service UNIQUE (participant_id, service_offering_id)
);

CREATE INDEX idx_entitlement_participant ON participant_service_entitlement (participant_id);
CREATE INDEX idx_entitlement_service ON participant_service_entitlement (service_offering_id);
CREATE INDEX idx_entitlement_status ON participant_service_entitlement (status);

-- Caller registration: principal (email, SP client id, SAMI/UAMI, …) under a participant.
-- caller_id is the unique key; each principal maps to exactly one participant (billing group).
CREATE TABLE participant_caller_registration (
    caller_id       VARCHAR(255)    NOT NULL PRIMARY KEY,
    participant_id  VARCHAR(40)     NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_caller_registration_participant
        FOREIGN KEY (participant_id) REFERENCES participant (id)
);

CREATE INDEX idx_caller_registration_participant ON participant_caller_registration (participant_id);
CREATE INDEX idx_caller_registration_status ON participant_caller_registration (status);

-- Consumption records per caller registration and service offering
CREATE TABLE participant_call_consumption (
    id                      UUID            NOT NULL PRIMARY KEY,
    caller_id               VARCHAR(255)    NOT NULL,
    service_offering_id     VARCHAR(100)    NOT NULL,
    -- JSON: endpoint_url, input_token, output_token, cache_token, ...
    consumption_data        TEXT            NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_consumption_caller_id
        FOREIGN KEY (caller_id) REFERENCES participant_caller_registration (caller_id),
    CONSTRAINT fk_consumption_service_offering
        FOREIGN KEY (service_offering_id) REFERENCES service_offering (id),
    CONSTRAINT uq_consumption_caller_service_ts UNIQUE (caller_id, service_offering_id, created_at)
);

CREATE INDEX idx_consumption_caller ON participant_call_consumption (caller_id);
CREATE INDEX idx_consumption_service ON participant_call_consumption (service_offering_id);
CREATE INDEX idx_consumption_ts ON participant_call_consumption (created_at);
