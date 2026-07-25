ALTER TABLE floor_maps
    ADD CONSTRAINT uk_floor_maps_place_id_floor_level UNIQUE (place_id, floor_level);
