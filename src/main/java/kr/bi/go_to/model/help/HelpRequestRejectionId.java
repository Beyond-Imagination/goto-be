package kr.bi.go_to.model.help;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class HelpRequestRejectionId implements Serializable {

    private UUID helpRequestId;
    private Long memberId;

    public HelpRequestRejectionId() {}

    public HelpRequestRejectionId(UUID helpRequestId, Long memberId) {
        this.helpRequestId = helpRequestId;
        this.memberId = memberId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HelpRequestRejectionId that)) {
            return false;
        }
        return Objects.equals(helpRequestId, that.helpRequestId) && Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(helpRequestId, memberId);
    }
}
