package kr.bi.go_to.model.obstaclereport;

public enum ObstacleIssueType {
    STAIRS,
    HIGH_CURB,
    STEEP_SLOPE,
    NARROW_PASSAGE,
    CONSTRUCTION,
    SIDEWALK_DAMAGE,
    LONG_WALKING_DISTANCE,
    /** 적치물 — 보도에 쌓인 물건/자재로 통행이 막히는 경우 */
    OBSTRUCTION,
    /** 불법 주차 — 보도나 횡단보도를 막은 차량 */
    ILLEGAL_PARKING,
    /** 점자블록 훼손 — 파손·이탈·덧칠 등으로 유도 기능을 잃은 경우 */
    BRAILLE_BLOCK_DAMAGE,
    /** 미끄러운 길 — 결빙, 물기, 마감재로 미끄러운 노면 */
    SLIPPERY_SURFACE,
    /** 기타 — 위 분류에 없는 장애물 */
    OTHER,
}
