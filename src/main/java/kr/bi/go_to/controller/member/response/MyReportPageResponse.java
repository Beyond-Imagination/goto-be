package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "MyReportPageResponse", description = "내 제보 기록 한 페이지")
public record MyReportPageResponse(
        @Schema(description = "최신순 항목 목록") List<MyReportItemResponse> items,
        @Schema(description = "다음 페이지 커서. 다음 페이지가 없으면 null이며, 값은 그대로 다음 요청의 cursor에 넣으면 된다", nullable = true)
                String nextCursor) {

    public boolean hasNext() {
        return nextCursor != null;
    }
}
