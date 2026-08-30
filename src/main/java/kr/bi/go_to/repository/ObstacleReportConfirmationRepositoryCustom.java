package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;

public interface ObstacleReportConfirmationRepositoryCustom {

    /**
     * 특정 회원이 확인한 제보 기록을 최신순으로 조회한다.
     * 목록에서 제보 본문을 함께 쓰므로 fetch join으로 N+1을 피한다. (내 정보 05 화면)
     */
    List<ObstacleReportConfirmation> findMineWithReport(Long memberId);

    /**
     * 특정 회원이 확인해 준 제보 중 해결(RESOLVED)된 건수.
     * (내 정보 01 화면의 「해결 확인」 통계)
     */
    long countResolvedByMember(Long memberId);
}
