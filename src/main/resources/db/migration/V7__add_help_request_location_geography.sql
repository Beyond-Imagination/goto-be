ALTER TABLE help_requests
    ADD COLUMN location GEOGRAPHY(Point, 4326)
        GENERATED ALWAYS AS (
            ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision), 4326)::geography
        ) STORED;

DROP INDEX IF EXISTS idx_help_requests_location_geography;

CREATE INDEX idx_help_requests_location ON help_requests USING GIST (location);

COMMENT ON COLUMN help_requests.location IS '도움 요청 발생 위치의 위도/경도 기반 PostGIS geography Point';
