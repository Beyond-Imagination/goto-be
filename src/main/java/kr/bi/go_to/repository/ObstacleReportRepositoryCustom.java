package kr.bi.go_to.repository;

import java.time.Instant;
import java.util.List;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;

public interface ObstacleReportRepositoryCustom {

    /**
     * 특정 회원이 작성한 제보들이 받은 확인 수의 총합.
     * 확인 기록이 없으면 0을 반환한다. (내 정보 01 화면의 「도움 된 사람」 통계)
     */
    long sumConfirmedCountByReporter(Long reporterId);

    /**
     * 내가 작성한 장애물 제보를 최신순으로 한 페이지 조회한다. (내 정보 03 화면)
     * afterCreatedAt/afterId가 있으면 그 항목 "다음"부터 읽는다.
     */
    List<ObstacleReport> findMinePage(Long memberId, Instant afterCreatedAt, Long afterId, int limit);
}
