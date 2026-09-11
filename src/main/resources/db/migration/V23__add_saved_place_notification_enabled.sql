-- 저장 장소별 상태 변경 알림 on/off (저장 화면 · 화면기획 17.2)
-- 저장 = 장소 상세·검색 결과의 하트이고, 저장하면 그 장소의 변화 알림을 기본으로 받는다.
-- 받을 알림 종류는 내 정보 › 알림 설정이 정하고, 이 컬럼은 장소마다 알림을 끄는 스위치다.
ALTER TABLE saved_places
    ADD COLUMN notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN saved_places.notification_enabled IS '이 장소의 상태 변경 알림을 받을지 여부 (기본값 true)';
