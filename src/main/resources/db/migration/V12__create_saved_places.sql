CREATE TABLE saved_places (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_saved_places_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_places_place FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT uk_saved_places_member_id_place_id UNIQUE (member_id, place_id)
);

COMMENT ON TABLE saved_places IS '회원이 저장(즐겨찾기)한 장소 정보를 관리하는 엔티티';
COMMENT ON COLUMN saved_places.id IS '저장 장소 고유 식별자 (PK)';
COMMENT ON COLUMN saved_places.member_id IS '저장한 회원 엔티티 (N:1 관계)';
COMMENT ON COLUMN saved_places.place_id IS '저장된 장소 엔티티 (N:1 관계)';
COMMENT ON COLUMN saved_places.created_at IS '장소를 저장한 일시 (엔티티 최초 생성 일시)';
COMMENT ON COLUMN saved_places.updated_at IS '엔티티 최종 수정 일시';
