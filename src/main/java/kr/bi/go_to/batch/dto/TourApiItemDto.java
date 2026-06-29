package kr.bi.go_to.batch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiItemDto(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String addr2,
        String mapx,
        String mapy,
        String cat1,
        String cat2,
        String cat3,
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
        boolean detailIntroSynced) {

    @JsonCreator
    public TourApiItemDto(
            @JsonProperty("contentid") String contentid,
            @JsonProperty("contenttypeid") String contenttypeid,
            @JsonProperty("title") String title,
            @JsonProperty("addr1") String addr1,
            @JsonProperty("addr2") String addr2,
            @JsonProperty("mapx") String mapx,
            @JsonProperty("mapy") String mapy,
            @JsonProperty("cat1") String cat1,
            @JsonProperty("cat2") String cat2,
            @JsonProperty("cat3") String cat3,
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
                cat1,
                cat2,
                cat3,
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
                false);
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
                this.cat1(),
                this.cat2(),
                this.cat3(),
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
                detailIntroSynced);
    }
}
