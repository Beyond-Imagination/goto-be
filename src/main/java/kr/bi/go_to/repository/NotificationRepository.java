package kr.bi.go_to.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.model.push.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByMember_IdAndReadAtIsNull(Long memberId);

    /** 첫 페이지. 최신순으로 size+1건을 읽어 다음 페이지 유무를 판단한다. */
    @Query("select n from Notification n where n.member.id = :memberId order by n.createdAt desc, n.id desc")
    List<Notification> findFirstPage(@Param("memberId") Long memberId, Limit limit);

    /**
     * 커서 이후 페이지.
     *
     * <p>같은 시각에 저장된 알림이 있어도 (createdAt, id)로 자르면 건너뛰거나 겹치지 않는다.
     */
    @Query(
            """
            select n from Notification n
            where n.member.id = :memberId
              and (n.createdAt < :afterCreatedAt
                   or (n.createdAt = :afterCreatedAt and n.id < :afterId))
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findPageAfter(
            @Param("memberId") Long memberId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") long afterId,
            Limit limit);

    /**
     * 이 도움 요청 알림을 이미 받은 회원들.
     *
     * <p>확대 발송에서 1단계 대상에게 같은 알림이 또 가지 않게 거르는 데 쓴다.
     * 알림 이력을 남겨 둔 덕분에 따로 발송 로그를 만들지 않아도 된다.
     */
    @Query("select distinct n.member.id from Notification n where n.helpRequestId = :helpRequestId")
    List<Long> findNotifiedMemberIdsByHelpRequest(@Param("helpRequestId") UUID helpRequestId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.member.id = :memberId and n.readAt is null")
    int markAllRead(@Param("memberId") Long memberId, @Param("now") Instant now);
}
