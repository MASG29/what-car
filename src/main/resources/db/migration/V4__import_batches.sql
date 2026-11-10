CREATE TABLE import_batch (
    id bigserial PRIMARY KEY,
    site text NOT NULL,
    imported_at timestamptz NOT NULL,
    read integer NOT NULL,
    added integer NOT NULL
);

CREATE TABLE import_batch_listing (
    batch_id bigint NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    listing_id bigint NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    PRIMARY KEY (batch_id, listing_id)
);

CREATE INDEX import_batch_listing_listing_idx ON import_batch_listing (listing_id);

-- Deleting an ad (one import, or all data) drops it from saved runs too.
ALTER TABLE search_run_listing DROP CONSTRAINT search_run_listing_listing_id_fkey;
ALTER TABLE search_run_listing ADD CONSTRAINT search_run_listing_listing_id_fkey
    FOREIGN KEY (listing_id) REFERENCES listing (id) ON DELETE CASCADE;

-- Ads imported before batches existed become one batch, so they can be managed too.
INSERT INTO import_batch (site, imported_at, read, added)
SELECT 'MARKETPLACE', min(first_seen), count(*), count(*) FROM listing WHERE site = 'MARKETPLACE'
HAVING count(*) > 0;

INSERT INTO import_batch_listing (batch_id, listing_id)
SELECT b.id, l.id FROM import_batch b JOIN listing l ON l.site = 'MARKETPLACE';
