CREATE TABLE floor_map (
    id           BIGSERIAL PRIMARY KEY,
    place_id     BIGINT NOT NULL REFERENCES place(id),
    floor_level  INT NOT NULL,
    geojson_data JSONB,
    created_by   BIGINT REFERENCES member(id),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE facility_node (
    id                BIGSERIAL PRIMARY KEY,
    floor_map_id      BIGINT NOT NULL REFERENCES floor_map(id),
    target_feature_id VARCHAR,
    node_type         VARCHAR NOT NULL,
    name              VARCHAR,
    geojson_point     geometry(Point, 4326),
    is_checkpoint     BOOLEAN DEFAULT FALSE,
    snap_radius       INT,
    created_by        BIGINT REFERENCES member(id),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);
