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
        String showflag) {
    public TourApiItemDto withDetails(String overview, String homepage, String bfDetails, String introDetails) {
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
                this.showflag());
    }
}
