package kr.bi.go_to.batch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

    public TourApiItemDto(
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
            String showflag) {
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
