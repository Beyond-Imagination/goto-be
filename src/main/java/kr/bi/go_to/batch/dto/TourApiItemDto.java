package kr.bi.go_to.batch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiItemDto {
    private String contentid;
    private String contenttypeid;
    private String title;
    private String addr1;
    private String addr2;
    private String mapx;
    private String mapy;
    private String cat1;
    private String cat2;
    private String cat3;
    private String firstimage;
    private String firstimage2;
    private String areacode;
    private String sigungucode;
    private String tel;
    private String zipcode;
    private String modifiedtime;
}
