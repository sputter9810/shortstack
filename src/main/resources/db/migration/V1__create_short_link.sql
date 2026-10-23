CREATE TABLE short_link (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    code VARCHAR(32) COLLATE "C" NOT NULL,
    destination_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_short_link PRIMARY KEY (id),
    CONSTRAINT uq_short_link_code UNIQUE (code),
    CONSTRAINT ck_short_link_code_format
        CHECK (code ~ '^[A-Za-z0-9_-]{3,32}$'),
    CONSTRAINT ck_short_link_reserved_code
        CHECK (lower(code) NOT IN (
            'api', 'error', 'actuator', 'health', 'metrics', 'docs',
            'swagger-ui', 'v3', 'favicon.ico', 'robots.txt'
        )),
    CONSTRAINT ck_short_link_destination_nonempty
        CHECK (char_length(destination_url) BETWEEN 1 AND 2048),
    CONSTRAINT ck_short_link_expiration
        CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT ck_short_link_created_range
        CHECK (created_at >= TIMESTAMPTZ '0001-01-01 00:00:00+00'
           AND created_at < TIMESTAMPTZ '10000-01-01 00:00:00+00'),
    CONSTRAINT ck_short_link_expiration_range
        CHECK (expires_at IS NULL OR (
            expires_at >= TIMESTAMPTZ '0001-01-01 00:00:00+00'
            AND expires_at < TIMESTAMPTZ '10000-01-01 00:00:00+00'
        ))
);
