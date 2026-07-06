package kr.bi.go_to.model.common;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class UuidV7 {

    private UuidV7() {}

    // DB 인덱스 효율을 위해 인덱스가 걸리는 컬럼에 UUID를 써야한다면 UUIDV7을 쓰는 것이 좋다
    public static UUID generate() {
        long timestampMillis = System.currentTimeMillis();
        long randomA = ThreadLocalRandom.current().nextLong() & 0x0FFFL;
        long randomB = ThreadLocalRandom.current().nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;

        long mostSignificantBits = ((timestampMillis & 0xFFFF_FFFF_FFFFL) << 16) | 0x7000L | randomA;
        long leastSignificantBits = 0x8000_0000_0000_0000L | randomB;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
