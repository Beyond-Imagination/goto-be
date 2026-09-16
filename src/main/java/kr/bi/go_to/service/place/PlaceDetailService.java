package kr.bi.go_to.service.place;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.AccessibilityRow;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.Badge;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.DetailState;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.Issue;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.RowKey;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.RowStatus;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.RowValue;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse.Summary;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.place.PlaceBfDetails;
import kr.bi.go_to.model.place.PlaceBfInfo;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceFacilityStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.repository.PlaceBfInfoRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceDetailService {

    private static final int RECENT_REPORT_LIMIT = 20;

    private final PlaceRepository placeRepository;
    private final PlaceBfInfoRepository placeBfInfoRepository;
    private final PlaceStateReportRepository placeStateReportRepository;

    public PlaceDetailService(
            PlaceRepository placeRepository,
            PlaceBfInfoRepository placeBfInfoRepository,
            PlaceStateReportRepository placeStateReportRepository) {
        this.placeRepository = placeRepository;
        this.placeBfInfoRepository = placeBfInfoRepository;
        this.placeStateReportRepository = placeStateReportRepository;
    }

    @Transactional(readOnly = true)
    public PlaceDetailResponse getDetail(Long placeId) {
        Place place = placeRepository.findByIdAndIsDeletedFalse(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        PlaceBfDetails bfDetails = placeBfInfoRepository.findById(placeId)
                .map(PlaceBfInfo::getBfDetails)
                .orElse(null);
        List<PlaceStateReport> reports = placeStateReportRepository.findLatestByPlace(placeId, RECENT_REPORT_LIMIT);
        PlaceStateReport latestReport = reports.isEmpty() ? null : reports.getFirst();

        List<AccessibilityRow> rows = rows(bfDetails, latestReport);
        DetailState detailState = detailState(rows, reports);
        List<Issue> issues = reports.stream()
                .filter(this::isWarningReport)
                .map(this::toIssue)
                .toList();

        return new PlaceDetailResponse(
                place.getId(),
                place.getName(),
                place.getSanitizedAddress(),
                place.getCategoryCode(),
                place.getCategoryCode(),
                place.getThumbnailUrl() == null || place.getThumbnailUrl().isBlank()
                        ? List.of()
                        : List.of(place.getThumbnailUrl()),
                detailState,
                badges(detailState),
                summary(detailState),
                issues,
                rows,
                notice(detailState));
    }

    private List<AccessibilityRow> rows(PlaceBfDetails bfDetails, PlaceStateReport latestReport) {
        return List.of(
                row(RowKey.ENTRANCE, "입구 접근성", official(bfDetails, "route", "접근 가능", "경사로 있음"),
                        recentAccess(latestReport)),
                row(RowKey.ELEVATOR, "엘리베이터", official(bfDetails, "elevator", "정상", "운영 중"),
                        recentFacility(latestReport, PriorityFacility.ELEVATOR)),
                row(RowKey.ACCESSIBLE_TOILET, "장애인 화장실", official(bfDetails, "restroom", "있음", "장애인 화장실 있음"),
                        recentFacility(latestReport, PriorityFacility.ACCESSIBLE_TOILET)),
                row(RowKey.PARKING, "주차장", noOfficial(), recentFacility(latestReport, PriorityFacility.PARKING)),
                row(RowKey.NURSING_ROOM, "수유실", noOfficial(), noReport()));
    }

    private AccessibilityRow row(RowKey key, String label, RowValue official, RowValue recent) {
        return new AccessibilityRow(key, label, official, recent);
    }

    private RowValue official(PlaceBfDetails bfDetails, String mobilityKey, String availableText, String availableDescription) {
        PlaceBfDetails.BfItem item = mobilityItem(bfDetails, mobilityKey);
        if (item == null || item.getIsAvailable() == null) {
            return noOfficial();
        }
        if (Boolean.TRUE.equals(item.getIsAvailable())) {
            return new RowValue(RowStatus.AVAILABLE, availableText, textOrDefault(item.getDetails(), availableDescription), false);
        }
        return new RowValue(RowStatus.UNAVAILABLE, "주의 필요", textOrDefault(item.getDetails(), "방문 전 확인이 필요해요"), false);
    }

    private PlaceBfDetails.BfItem mobilityItem(PlaceBfDetails bfDetails, String key) {
        Map<String, PlaceBfDetails.BfItem> mobility = bfDetails == null ? null : bfDetails.getMobility();
        return mobility == null ? null : mobility.get(key);
    }

    private RowValue recentAccess(PlaceStateReport latestReport) {
        if (latestReport == null) {
            return noReport();
        }
        return switch (latestReport.getAccessStatus()) {
            case ACCESSIBLE -> new RowValue(RowStatus.AVAILABLE, "접근 가능", "최근 제보 기준", false);
            case PARTIALLY_ACCESSIBLE -> new RowValue(RowStatus.WARNING, "주의 필요", "일부 불편 제보", false);
            case INACCESSIBLE -> new RowValue(RowStatus.UNAVAILABLE, "접근 어려움", "이용 불가 제보", false);
        };
    }

    private RowValue recentFacility(PlaceStateReport latestReport, PriorityFacility facility) {
        if (latestReport == null) {
            return noReport();
        }
        PlaceFacilityStatus status = latestReport.getFacilityStatuses().get(facility);
        if (status == null) {
            return noReport();
        }
        return switch (status) {
            case AVAILABLE -> new RowValue(RowStatus.AVAILABLE, "정상", "최근 제보 기준", false);
            case UNAVAILABLE -> new RowValue(RowStatus.UNAVAILABLE, "없음", "이용 불가 제보", false);
            case BROKEN -> new RowValue(RowStatus.BROKEN, "고장", "고장 제보", false);
        };
    }

    private RowValue noOfficial() {
        return new RowValue(RowStatus.NO_OFFICIAL, "공식정보 없음", "공공 데이터에 등록된 정보가 없어요", false);
    }

    private RowValue noReport() {
        return new RowValue(RowStatus.NO_REPORT, "제보 없음", "제보하기 >", true);
    }

    private DetailState detailState(List<AccessibilityRow> rows, List<PlaceStateReport> reports) {
        if (rows.stream().anyMatch(row -> isWarning(row.official()) || isWarning(row.recent()))) {
            return DetailState.WARNING;
        }
        if (rows.stream().allMatch(row -> row.official().status() == RowStatus.NO_OFFICIAL)) {
            return DetailState.OFFICIAL_MISSING;
        }
        if (reports.isEmpty()) {
            return DetailState.REPORT_MISSING;
        }
        return DetailState.NORMAL;
    }

    private boolean isWarning(RowValue value) {
        return value.status() == RowStatus.WARNING
                || value.status() == RowStatus.UNAVAILABLE
                || value.status() == RowStatus.BROKEN;
    }

    private boolean isWarningReport(PlaceStateReport report) {
        return report.getAccessStatus() != PlaceAccessStatus.ACCESSIBLE
                || report.getFacilityStatuses().values().stream()
                        .anyMatch(status -> status == PlaceFacilityStatus.UNAVAILABLE || status == PlaceFacilityStatus.BROKEN);
    }

    private Issue toIssue(PlaceStateReport report) {
        return new Issue(
                report.getId(),
                textOrDefault(report.getDescription(), issueTitle(report)),
                "최근 제보",
                report.getCreatedAt(),
                1,
                report.getAccessStatus().name());
    }

    private String issueTitle(PlaceStateReport report) {
        if (report.getAccessStatus() == PlaceAccessStatus.PARTIALLY_ACCESSIBLE) {
            return "일부 불편 제보가 있어요";
        }
        if (report.getAccessStatus() == PlaceAccessStatus.INACCESSIBLE) {
            return "접근 어려움 제보가 있어요";
        }
        return "시설 상태 확인이 필요해요";
    }

    private List<Badge> badges(DetailState state) {
        List<Badge> badges = new ArrayList<>();
        switch (state) {
            case WARNING -> {
                badges.add(new Badge("주의 필요", "warning"));
                badges.add(new Badge("최근 확인됨", "info"));
            }
            case OFFICIAL_MISSING -> badges.add(new Badge("사용자 제보만 있음", "orange"));
            case REPORT_MISSING -> badges.add(new Badge("제보없음", "neutral"));
            case NORMAL -> {
                badges.add(new Badge("접근성 양호", "good"));
                badges.add(new Badge("최근 확인됨", "info"));
            }
        }
        return badges;
    }

    private Summary summary(DetailState state) {
        return switch (state) {
            case WARNING -> new Summary("최근 이용에 주의가 필요해요", "최근 제보 또는 공식 정보에 불편/고장/불가 상태가 있어요");
            case OFFICIAL_MISSING -> new Summary("공식 정보가 아직 없어요", "사용자 제보를 기준으로 현재 상태를 보여드려요");
            case REPORT_MISSING -> new Summary("아직 방문 제보가 없어요", "공식 정보 기준으로 보여드려요. 다녀오셨다면 알려주세요");
            case NORMAL -> new Summary("접근성 정보를 확인했어요", "공식 정보와 최근 제보를 기반으로 해요");
        };
    }

    private String notice(DetailState state) {
        return switch (state) {
            case OFFICIAL_MISSING -> "이 정보는 사용자 제보만으로 구성돼 있어요";
            case REPORT_MISSING -> "이 정보는 공식 정보만으로 구성돼 있어요";
            default -> "이 정보는 공식 정보와 최근 제보를 기반으로 해요";
        };
    }

    private String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
