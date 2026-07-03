package kr.bi.go_to.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.controller.help.request.CreateHelpRequestRequest;
import kr.bi.go_to.controller.help.response.HelpRequestResponse;
import kr.bi.go_to.controller.help.response.NearbyHelpRequestResponse;
import kr.bi.go_to.model.help.HelpMatchingLog;
import kr.bi.go_to.model.help.HelpMatchingLogRepository;
import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestRejection;
import kr.bi.go_to.model.help.HelpRequestRejectionRepository;
import kr.bi.go_to.model.help.HelpRequestRepository;
import kr.bi.go_to.model.help.HelpRequestStatus;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.PlaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HelpRequestService {

    private static final int DEFAULT_EXPIRES_IN_MINUTES = 30;

    private final HelpRequestRepository helpRequestRepository;
    private final HelpRequestRejectionRepository rejectionRepository;
    private final HelpMatchingLogRepository matchingLogRepository;
    private final PlaceRepository placeRepository;
    private final MemberService memberService;
    private final Clock clock;

    public HelpRequestService(
            HelpRequestRepository helpRequestRepository,
            HelpRequestRejectionRepository rejectionRepository,
            HelpMatchingLogRepository matchingLogRepository,
            PlaceRepository placeRepository,
            MemberService memberService,
            Clock clock) {
        this.helpRequestRepository = helpRequestRepository;
        this.rejectionRepository = rejectionRepository;
        this.matchingLogRepository = matchingLogRepository;
        this.placeRepository = placeRepository;
        this.memberService = memberService;
        this.clock = clock;
    }

    @Transactional
    public HelpRequestResponse create(Long memberId, CreateHelpRequestRequest request) {
        Member requester = memberService.getUser(memberId);
        Place place = findPlaceOrNull(request.placeId());
        Instant now = Instant.now(clock);
        int expiresInMinutes =
                request.expiresInMinutes() == null ? DEFAULT_EXPIRES_IN_MINUTES : request.expiresInMinutes();

        HelpRequest helpRequest = helpRequestRepository.save(new HelpRequest(
                place,
                requester,
                request.locationLabel().trim(),
                request.latitude(),
                request.longitude(),
                request.floorLevel(),
                trimToNull(request.message()),
                now,
                now.plus(Duration.ofMinutes(expiresInMinutes))));

        return toResponse(helpRequest);
    }

    @Transactional(readOnly = true)
    public List<NearbyHelpRequestResponse> findNearby(
            Long memberId, BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        Member member = memberService.getUser(memberId);
        Instant now = Instant.now(clock);

        return helpRequestRepository
                .findByStatusAndExpiresAtAfterOrderByRequestedAtDesc(HelpRequestStatus.REQUESTED, now)
                .stream()
                .filter(request -> !request.isRequester(member))
                .filter(request ->
                        !rejectionRepository.existsByHelpRequestIdAndMemberId(request.getId(), member.getId()))
                .map(request -> toNearbyResponse(
                        request, distanceMeters(latitude, longitude, request.getLatitude(), request.getLongitude())))
                .filter(response -> response.distanceMeters() <= radiusMeters)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HelpRequestResponse> findMine(Long memberId) {
        Member member = memberService.getUser(memberId);
        return helpRequestRepository
                .findByRequesterIdOrHelperIdOrderByRequestedAtDesc(member.getId(), member.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HelpRequestResponse get(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExisting(id);
        ensureParticipant(helpRequest, member);
        expireIfNeeded(helpRequest);
        return toResponse(helpRequest);
    }

    @Transactional
    public HelpRequestResponse accept(Long memberId, UUID id) {
        Member helper = memberService.getUser(memberId);
        HelpRequest helpRequest = findExisting(id);
        ensureRequestedAndNotExpired(helpRequest);

        if (helpRequest.isRequester(helper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requester cannot accept own help request");
        }
        if (rejectionRepository.existsByHelpRequestIdAndMemberId(helpRequest.getId(), helper.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Help request was already rejected by this member");
        }

        helpRequest.accept(helper, Instant.now(clock));
        matchingLogRepository.save(new HelpMatchingLog(helpRequest));
        return toResponse(helpRequest);
    }

    @Transactional
    public void reject(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExisting(id);
        ensureRequestedAndNotExpired(helpRequest);

        if (helpRequest.isRequester(member)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requester cannot reject own help request");
        }
        if (!rejectionRepository.existsByHelpRequestIdAndMemberId(helpRequest.getId(), member.getId())) {
            rejectionRepository.save(new HelpRequestRejection(helpRequest, member, Instant.now(clock)));
        }
    }

    @Transactional
    public HelpRequestResponse complete(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExisting(id);
        ensureParticipant(helpRequest, member);

        if (helpRequest.getStatus() != HelpRequestStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only accepted help requests can be completed");
        }

        Instant now = Instant.now(clock);
        helpRequest.complete(now);
        matchingLogRepository.findByHelpRequestId(helpRequest.getId()).ifPresent(log -> log.complete(now));
        return toResponse(helpRequest);
    }

    @Transactional
    public HelpRequestResponse cancel(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExisting(id);

        if (!helpRequest.isRequester(member)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only requester can cancel help request");
        }
        if (helpRequest.getStatus() != HelpRequestStatus.REQUESTED
                && helpRequest.getStatus() != HelpRequestStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Help request cannot be canceled");
        }

        helpRequest.cancel(Instant.now(clock));
        return toResponse(helpRequest);
    }

    private HelpRequest findExisting(UUID id) {
        return helpRequestRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help request not found"));
    }

    private void ensureRequestedAndNotExpired(HelpRequest helpRequest) {
        expireIfNeeded(helpRequest);
        if (helpRequest.getStatus() == HelpRequestStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Help request expired");
        }
        if (helpRequest.getStatus() != HelpRequestStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Help request is not open");
        }
    }

    private void expireIfNeeded(HelpRequest helpRequest) {
        helpRequest.expire(Instant.now(clock));
    }

    private void ensureParticipant(HelpRequest helpRequest, Member member) {
        if (!helpRequest.isRequester(member) && !helpRequest.isHelper(member)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Help request detail is only visible to participants");
        }
    }

    private HelpRequestResponse toResponse(HelpRequest helpRequest) {
        return new HelpRequestResponse(
                helpRequest.getId(),
                helpRequest.getStatus().name(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getId(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getName(),
                helpRequest.getLocationLabel(),
                helpRequest.getLatitude(),
                helpRequest.getLongitude(),
                helpRequest.getFloorLevel(),
                helpRequest.getMessage(),
                helpRequest.getRequester().getNickname(),
                helpRequest.getHelper() == null ? null : helpRequest.getHelper().getNickname(),
                helpRequest.getRequestedAt(),
                helpRequest.getExpiresAt(),
                helpRequest.getAcceptedAt(),
                helpRequest.getCompletedAt(),
                helpRequest.getCanceledAt(),
                shareMessage(helpRequest),
                true);
    }

    private NearbyHelpRequestResponse toNearbyResponse(HelpRequest helpRequest, long distanceMeters) {
        return new NearbyHelpRequestResponse(
                helpRequest.getId(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getId(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getName(),
                helpRequest.getLocationLabel(),
                helpRequest.getMessage(),
                distanceMeters,
                helpRequest.getRequestedAt(),
                helpRequest.getExpiresAt());
    }

    private String shareMessage(HelpRequest helpRequest) {
        String floor = helpRequest.getFloorLevel() == null ? "" : " " + helpRequest.getFloorLevel() + "층";
        return "현재 " + helpRequest.getLocationLabel() + floor + " 근처에서 이동 도움이 필요합니다.";
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Place findPlaceOrNull(Long placeId) {
        if (placeId == null) {
            return null;
        }
        return placeRepository
                .findById(placeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found"));
    }

    private long distanceMeters(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal targetLatitude,
            BigDecimal targetLongitude) {
        double earthRadiusMeters = 6_371_000;
        double lat1 = Math.toRadians(originLatitude.doubleValue());
        double lat2 = Math.toRadians(targetLatitude.doubleValue());
        double deltaLat = Math.toRadians(targetLatitude.subtract(originLatitude).doubleValue());
        double deltaLng =
                Math.toRadians(targetLongitude.subtract(originLongitude).doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadiusMeters * c);
    }
}
