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
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.help.HelpMatchingLog;
import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestRejection;
import kr.bi.go_to.model.help.HelpRequestStatus;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.HelpMatchingLogRepository;
import kr.bi.go_to.repository.HelpRequestRejectionRepository;
import kr.bi.go_to.repository.HelpRequestRepository;
import kr.bi.go_to.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return HelpRequestResponse.from(helpRequest);
    }

    @Transactional(readOnly = true)
    public List<NearbyHelpRequestResponse> findNearby(
            Long memberId, BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        Member member = memberService.getUser(memberId);
        Instant now = Instant.now(clock);

        return helpRequestRepository
                .findNearbyOpenRequests(
                        member.getId(), HelpRequestStatus.REQUESTED.name(), latitude, longitude, radiusMeters, now)
                .stream()
                .map(request -> NearbyHelpRequestResponse.from(
                        request, distanceMeters(latitude, longitude, request.getLatitude(), request.getLongitude())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HelpRequestResponse> findMine(Long memberId) {
        Member member = memberService.getUser(memberId);
        return helpRequestRepository
                .findByRequesterIdOrHelperIdOrderByRequestedAtDesc(member.getId(), member.getId())
                .stream()
                .map(HelpRequestResponse::from)
                .toList();
    }

    @Transactional
    public HelpRequestResponse get(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExisting(id);
        ensureParticipant(helpRequest, member);
        return HelpRequestResponse.from(helpRequest);
    }

    @Transactional
    public HelpRequestResponse accept(Long memberId, UUID id) {
        Member helper = memberService.getUser(memberId);
        HelpRequest helpRequest = findExistingForUpdate(id);
        ensureRequestedAndNotExpired(helpRequest);

        if (helpRequest.isRequester(helper)) {
            throw new BusinessException(ErrorCode.CANNOT_ACCEPT_OWN_HELP_REQUEST);
        }
        if (rejectionRepository.existsByHelpRequestIdAndMemberId(helpRequest.getId(), helper.getId())) {
            throw new BusinessException(ErrorCode.HELP_REQUEST_ALREADY_REJECTED);
        }

        helpRequest.accept(helper, Instant.now(clock));
        matchingLogRepository.save(new HelpMatchingLog(helpRequest));
        return HelpRequestResponse.from(helpRequest);
    }

    @Transactional
    public void reject(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExistingForUpdate(id);
        ensureRequestedAndNotExpired(helpRequest);

        if (helpRequest.isRequester(member)) {
            throw new BusinessException(ErrorCode.CANNOT_REJECT_OWN_HELP_REQUEST);
        }
        if (!rejectionRepository.existsByHelpRequestIdAndMemberId(helpRequest.getId(), member.getId())) {
            rejectionRepository.save(new HelpRequestRejection(helpRequest, member, Instant.now(clock)));
        }
    }

    @Transactional
    public HelpRequestResponse complete(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExistingForUpdate(id);
        ensureParticipant(helpRequest, member);

        if (helpRequest.getStatus() != HelpRequestStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.HELP_REQUEST_NOT_ACCEPTED);
        }

        Instant now = Instant.now(clock);
        helpRequest.complete(now);
        matchingLogRepository.findByHelpRequestId(helpRequest.getId()).ifPresent(log -> log.complete(now));
        return HelpRequestResponse.from(helpRequest);
    }

    @Transactional
    public HelpRequestResponse cancel(Long memberId, UUID id) {
        Member member = memberService.getUser(memberId);
        HelpRequest helpRequest = findExistingForUpdate(id);

        if (!helpRequest.isRequester(member)) {
            throw new BusinessException(ErrorCode.ONLY_REQUESTER_CAN_CANCEL);
        }
        if (helpRequest.getStatus() != HelpRequestStatus.REQUESTED
                && helpRequest.getStatus() != HelpRequestStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.HELP_REQUEST_CANNOT_BE_CANCELED);
        }

        helpRequest.cancel(Instant.now(clock));
        return HelpRequestResponse.from(helpRequest);
    }

    private HelpRequest findExisting(UUID id) {
        return helpRequestRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.HELP_REQUEST_NOT_FOUND));
    }

    private HelpRequest findExistingForUpdate(UUID id) {
        return helpRequestRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.HELP_REQUEST_NOT_FOUND));
    }

    private void ensureRequestedAndNotExpired(HelpRequest helpRequest) {
        if (helpRequest.isExpired(Instant.now(clock)) || helpRequest.getStatus() == HelpRequestStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.HELP_REQUEST_EXPIRED);
        }
        if (helpRequest.getStatus() != HelpRequestStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.HELP_REQUEST_NOT_OPEN);
        }
    }

    private void ensureParticipant(HelpRequest helpRequest, Member member) {
        if (!helpRequest.isRequester(member) && !helpRequest.isHelper(member)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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
        return placeRepository.findById(placeId).orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
    }

    private long distanceMeters(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            BigDecimal targetLatitude,
            BigDecimal targetLongitude) {
        double earthRadiusMeters = 6_371_000;
        double originLatitudeValue = originLatitude.doubleValue();
        double originLongitudeValue = originLongitude.doubleValue();
        double targetLatitudeValue = targetLatitude.doubleValue();
        double targetLongitudeValue = targetLongitude.doubleValue();
        double lat1 = Math.toRadians(originLatitudeValue);
        double lat2 = Math.toRadians(targetLatitudeValue);
        double deltaLat = Math.toRadians(targetLatitudeValue - originLatitudeValue);
        double deltaLng = Math.toRadians(targetLongitudeValue - originLongitudeValue);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadiusMeters * c);
    }
}
