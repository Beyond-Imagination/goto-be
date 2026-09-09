package kr.bi.go_to.repository;

import java.time.Instant;
import java.util.List;
import kr.bi.go_to.model.report.Report;

public interface ReportRepositoryCustom {

    /**
     * 내가 작성한 시설 상태 제보를 최신순으로 한 페이지 조회한다. (내 정보 03 화면의 「시설」 분류)
     * 목록이 시설명·층·장소명을 함께 쓰므로 노드 → 층 도면 → 장소를 fetch join 하고,
     * 커서가 있으면 그 항목 다음부터 읽는다.
     */
    List<Report> findMinePage(Long memberId, Instant afterCreatedAt, Long afterId, int limit);
}
