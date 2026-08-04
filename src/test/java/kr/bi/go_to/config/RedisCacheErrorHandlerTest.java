package kr.bi.go_to.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

class RedisCacheErrorHandlerTest {

    private final RedisCacheErrorHandler errorHandler = new RedisCacheErrorHandler();

    @Test
    @DisplayName("캐시 조회 실패 시 예외를 삼켜서 DB 폴백이 가능하게 한다")
    void swallowsCacheGetError() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("indoor-map");

        assertThatCode(() -> errorHandler.handleCacheGetError(new RuntimeException("boom"), cache, "3:1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("캐시 저장 실패 시 예외를 삼켜서 응답 흐름을 막지 않는다")
    void swallowsCachePutError() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("indoor-map");

        assertThatCode(() -> errorHandler.handleCachePutError(new RuntimeException("boom"), cache, "3:1", "value"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("캐시 무효화 실패 시 예외를 삼켜서 쓰기 흐름을 막지 않는다")
    void swallowsCacheEvictError() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("indoor-map");

        assertThatCode(() -> errorHandler.handleCacheEvictError(new RuntimeException("boom"), cache, "3:1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("캐시 전체 삭제 실패 시 예외를 삼킨다")
    void swallowsCacheClearError() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("indoor-map");

        assertThatCode(() -> errorHandler.handleCacheClearError(new RuntimeException("boom"), cache))
                .doesNotThrowAnyException();
    }
}
