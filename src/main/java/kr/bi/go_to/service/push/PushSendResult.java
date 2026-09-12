package kr.bi.go_to.service.push;

import java.util.List;

/**
 * 발송 결과.
 *
 * @param successCount 실제로 FCM이 받아준 건수
 * @param invalidTokens 더 이상 쓸 수 없는 토큰(앱 삭제·토큰 만료). 저장소에서 지워야 한다.
 */
public record PushSendResult(int successCount, List<String> invalidTokens) {

    public static PushSendResult none() {
        return new PushSendResult(0, List.of());
    }

    public PushSendResult {
        invalidTokens = List.copyOf(invalidTokens);
    }
}
