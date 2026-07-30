package kr.bi.go_to.batch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import kr.bi.go_to.model.batch.CategoryResolutionStatus;
import kr.bi.go_to.model.batch.DetailSyncStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiItemDto(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String addr2,
        String mapx,
        String mapy,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        String firstimage,
        String firstimage2,
        String areacode,
        String sigungucode,
        String tel,
        String zipcode,
        String modifiedtime,
        String overview,
        String homepage,
        String bfDetails,
        String introDetails,
        String showflag,
        boolean detailCommonSynced,
        boolean detailWithTourSynced,
        boolean detailIntroSynced,
        CategoryResolutionStatus categoryResolutionStatus,
        DetailSyncStatus detailCommonStatus,
        DetailSyncStatus detailWithTourStatus,
        DetailSyncStatus detailIntroStatus) {

    @JsonCreator
    public TourApiItemDto(
            @JsonProperty("contentid") String contentid,
            @JsonProperty("contenttypeid") String contenttypeid,
            @JsonProperty("title") String title,
            @JsonProperty("addr1") String addr1,
            @JsonProperty("addr2") String addr2,
            @JsonProperty("mapx") String mapx,
            @JsonProperty("mapy") String mapy,
            @JsonProperty("lclsSystm1") String lclsSystm1,
            @JsonProperty("lclsSystm2") String lclsSystm2,
            @JsonProperty("lclsSystm3") String lclsSystm3,
            @JsonProperty("firstimage") String firstimage,
            @JsonProperty("firstimage2") String firstimage2,
            @JsonProperty("areacode") String areacode,
            @JsonProperty("sigungucode") String sigungucode,
            @JsonProperty("tel") String tel,
            @JsonProperty("zipcode") String zipcode,
            @JsonProperty("modifiedtime") String modifiedtime,
            @JsonProperty("overview") String overview,
            @JsonProperty("homepage") String homepage,
            @JsonProperty("bfDetails") String bfDetails,
            @JsonProperty("introDetails") String introDetails,
            @JsonProperty("showflag") String showflag) {
        this(
                contentid,
                contenttypeid,
                title,
                addr1,
                addr2,
                mapx,
                mapy,
                lclsSystm1,
                lclsSystm2,
                lclsSystm3,
                firstimage,
                firstimage2,
                areacode,
                sigungucode,
                tel,
                zipcode,
                modifiedtime,
                overview,
                homepage,
                bfDetails,
                introDetails,
                showflag,
                false,
                false,
                false,
                hasText(lclsSystm3) ? CategoryResolutionStatus.RESOLVED : CategoryResolutionStatus.PENDING,
                DetailSyncStatus.PENDING,
                DetailSyncStatus.PENDING,
                DetailSyncStatus.PENDING);
    }

    public TourApiItemDto withDetails(String overview, String homepage, String bfDetails, String introDetails) {
        return withDetails(overview, homepage, bfDetails, introDetails, false, false, false);
    }

    public TourApiItemDto withDetails(
            String overview,
            String homepage,
            String bfDetails,
            String introDetails,
            boolean detailCommonSynced,
            boolean detailWithTourSynced,
            boolean detailIntroSynced) {
        return new TourApiItemDto(
                this.contentid(),
                this.contenttypeid(),
                this.title(),
                this.addr1(),
                this.addr2(),
                this.mapx(),
                this.mapy(),
                this.lclsSystm1(),
                this.lclsSystm2(),
                this.lclsSystm3(),
                this.firstimage(),
                this.firstimage2(),
                this.areacode(),
                this.sigungucode(),
                this.tel(),
                this.zipcode(),
                this.modifiedtime(),
                overview,
                homepage,
                bfDetails,
                introDetails,
                this.showflag(),
                detailCommonSynced,
                detailWithTourSynced,
                detailIntroSynced,
                categoryResolutionStatus,
                detailCommonSynced ? DetailSyncStatus.SUCCESS : detailCommonStatus,
                detailWithTourSynced ? DetailSyncStatus.SUCCESS : detailWithTourStatus,
                detailIntroSynced ? DetailSyncStatus.SUCCESS : detailIntroStatus);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
