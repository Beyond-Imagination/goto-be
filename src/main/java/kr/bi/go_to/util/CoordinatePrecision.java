package kr.bi.go_to.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 저장·노출하는 좌표의 정밀도.
 *
 * <p>소수점 3자리는 위도 기준 약 110m다. 수락 전 도우미에게 보여주는 도움 요청 위치
 * (화면기획 20.1)와 같은 기준을 쓰며, "이 근처에 있다"를 판단하기엔 충분하고 "어느 건물
 * 어디에 있다"를 알아내기엔 부족한 정도다.
 */
public final class CoordinatePrecision {

    /** 소수점 자릿수. 위도 기준 약 110m. */
    public static final int APPROXIMATE_SCALE = 3;

    private CoordinatePrecision() {}

    public static BigDecimal approximate(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(APPROXIMATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 기기 위치처럼 double로 다루는 좌표를 같은 정밀도로 줄인다.
     * 저장 직전에 부르는 것이 원칙이다 — 원좌표가 DB에 남지 않아야 의미가 있다.
     */
    public static Double approximate(Double coordinate) {
        return coordinate == null
                ? null
                : BigDecimal.valueOf(coordinate)
                        .setScale(APPROXIMATE_SCALE, RoundingMode.HALF_UP)
                        .doubleValue();
    }
}
