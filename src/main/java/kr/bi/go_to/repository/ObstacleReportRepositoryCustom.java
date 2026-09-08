package kr.bi.go_to.repository;

public interface ObstacleReportRepositoryCustom {

    /**
     * 특정 회원이 작성한 제보들이 받은 확인 수의 총합.
     * 확인 기록이 없으면 0을 반환한다. (내 정보 01 화면의 「도움 된 사람」 통계)
     */
    long sumConfirmedCountByReporter(Long reporterId);
}
