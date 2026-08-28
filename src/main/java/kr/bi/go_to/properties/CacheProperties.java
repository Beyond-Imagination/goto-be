package kr.bi.go_to.properties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "goto.cache")
public class CacheProperties {

    public static final String HELP_REQUESTS_PENDING_COUNT =
            "${goto.cache.caches.help-requests-pending-count.name:help-requests:pending-count}";
    public static final String INDOOR_MAP = "${goto.cache.caches.indoor-map.name:indoor-map}";

    private Duration defaultTtl = Duration.ofHours(24);
    private Map<String, CacheSetting> caches = new HashMap<>();

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Map<String, CacheSetting> getCaches() {
        return caches;
    }

    public void setCaches(Map<String, CacheSetting> caches) {
        this.caches = caches == null ? new HashMap<>() : new HashMap<>(caches);
    }

    public String getCacheName(String key) {
        CacheSetting setting = caches.get(key);
        return setting != null && setting.getName() != null ? setting.getName() : key;
    }

    public Duration getTtl(String key) {
        CacheSetting setting = caches.get(key);
        return setting != null && setting.getTtl() != null ? setting.getTtl() : defaultTtl;
    }

    public static class CacheSetting {

        private String name;
        private Duration ttl;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }
}
