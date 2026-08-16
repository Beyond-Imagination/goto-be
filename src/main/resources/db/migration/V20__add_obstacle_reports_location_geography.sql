ALTER TABLE obstacle_reports
    ADD COLUMN location_point_geography GEOGRAPHY(Point, 4326)
        GENERATED ALWAYS AS (location_point::geography) STORED;

CREATE INDEX idx_obstacle_reports_location_point_geography ON obstacle_reports USING GIST (location_point_geography);

COMMENT ON COLUMN obstacle_reports.location_point_geography IS '반경(미터) 기반 조회(ST_DWithin)를 위한 location_point의 geography 캐스팅. location_point의 GiST 인덱스는 geometry opclass라 ::geography로 인라인 캐스팅하면 활용되지 않으므로, GENERATED STORED 컬럼 + 전용 GiST 인덱스로 인덱스 활용을 보장한다 (help_requests.location과 동일한 패턴, V7 참고)';
