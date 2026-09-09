package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "MyConfirmedReportPageResponse", description = "내가 확인한 리포트 한 페이지")
public record MyConfirmedReportPageResponse(
        @Schema(description = "최신순 항목 목록") List<MyConfirmedReportResponse> items,
        @Schema(description = "다음 페이지 커서. 다음 페이지가 없으면 null", nullable = true) String nextCursor) {

    public boolean hasNext() {
        return nextCursor != null;
    }
}
