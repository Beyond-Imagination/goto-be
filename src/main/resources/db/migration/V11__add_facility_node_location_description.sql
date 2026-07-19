ALTER TABLE facility_nodes
    ADD COLUMN location_description VARCHAR(255);

COMMENT ON COLUMN facility_nodes.location_description IS '시설물의 사람이 읽을 수 있는 위치 설명 (예: 신라역사관 동쪽 복도 끝, 로비에서 30m)';
