CREATE TABLE help_request_kinds
(
    help_request_id UUID        NOT NULL,
    kind            VARCHAR(30) NOT NULL,
    CONSTRAINT pk_help_request_kinds PRIMARY KEY (help_request_id, kind),
    CONSTRAINT fk_help_request_kinds_help_request FOREIGN KEY (help_request_id)
        REFERENCES help_requests (id) ON DELETE CASCADE
);

CREATE INDEX idx_help_request_kinds_help_request ON help_request_kinds (help_request_id);

COMMENT ON TABLE help_request_kinds IS '도움 요청에서 요청자가 선택한 도움 유형. 한 요청에 여러 유형을 붙일 수 있다';
COMMENT ON COLUMN help_request_kinds.help_request_id IS '대상 도움 요청 (N:1 관계)';
COMMENT ON COLUMN help_request_kinds.kind IS '도움 유형 코드. HelpKind enum 이름을 저장한다';
