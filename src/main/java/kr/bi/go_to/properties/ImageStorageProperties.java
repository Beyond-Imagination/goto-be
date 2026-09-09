package kr.bi.go_to.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 제보 사진 업로드 저장소 설정.
 *
 * <p>bucket이 비어 있으면 업로드 기능이 비활성 상태로 동작한다(503). 로컬 개발에서 S3 없이
 * 나머지 API를 띄울 수 있도록 한 것이며, {@code NaverReverseGeocodingClient}가 키 미설정을
 * 흡수하는 것과 같은 원칙이다.
 */
@ConfigurationProperties(prefix = "goto.image-storage")
public class ImageStorageProperties {

    /** S3 버킷 이름. Parameter Store(/goto/goto.image-storage.bucket)로 주입한다. */
    private String bucket = "";

    /** 업로드 객체 키 앞에 붙는 경로. 버킷을 다른 용도와 공유할 때 구분한다. */
    private String keyPrefix = "obstacle-reports";

    /**
     * 업로드된 객체를 읽을 때 쓰는 공개 base URL (CloudFront 등).
     * 비어 있으면 S3 가상 호스팅 주소를 쓴다.
     */
    private String publicBaseUrl = "";

    /** 파일 하나의 최대 크기(바이트). 기본 10MB. */
    private long maxFileSizeBytes = 10L * 1024 * 1024;

    /** 허용 Content-Type. 이미지 외 업로드를 막는다. */
    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp", "image/heic");

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public boolean isConfigured() {
        return !bucket.isBlank();
    }
}
