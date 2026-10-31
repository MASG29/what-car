CREATE TABLE search_run (
    id uuid PRIMARY KEY,
    limits_json text NOT NULL,
    preset text NOT NULL,
    custom_weights_json text,
    status text NOT NULL,
    message_pt text,
    collected integer NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL,
    finished_at timestamptz
);

CREATE TABLE search_run_site (
    run_id uuid NOT NULL REFERENCES search_run (id) ON DELETE CASCADE,
    site text NOT NULL,
    PRIMARY KEY (run_id, site)
);

CREATE TABLE search_run_listing (
    run_id uuid NOT NULL REFERENCES search_run (id) ON DELETE CASCADE,
    listing_id bigint NOT NULL REFERENCES listing (id),
    PRIMARY KEY (run_id, listing_id)
);

CREATE INDEX search_run_status_idx ON search_run (status);
