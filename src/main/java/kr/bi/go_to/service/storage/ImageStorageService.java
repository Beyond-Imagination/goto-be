package kr.bi.go_to.service.storage;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.properties.ImageStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 제보 사진을 S3에 올리고 조회용 URL을 돌려준다.
 *
 * <p>업로드는 앱 서버를 거친다(프리사인 방식 아님). 파일이 수 MB 수준이고, Content-Type·용량 검증과
 * 객체 키 생성을 서버가 통제하는 편이 안전하며, 버킷에 CORS를 열지 않아도 되기 때문이다.
 */
@Slf4j
@Service
public class ImageStorageService {

    private final S3Client s3Client;
    private final ImageStorageProperties properties;
    private final String region;

    public ImageStorageService(
            S3Client s3Client,
            ImageStorageProperties properties,
            @org.springframework.beans.factory.annotation.Value("${spring.cloud.aws.region.static:ap-northeast-2}")
                    String region) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.region = region;
    }

    /**
     * 이미지를 업로드하고 공개 URL을 반환한다.
     * 반환된 URL을 제보 생성 요청의 photoUrls에 그대로 넣으면 된다.
     */
    public String upload(MultipartFile file) {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        }

        validate(file);

        String key = buildKey(file.getContentType());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | RuntimeException exception) {
            // 업로드 실패는 사용자에게 재시도 가능한 오류로 알리고, 원인은 로그로만 남긴다.
            log.warn("이미지 업로드 실패 (key={})", key, exception);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return toPublicUrl(key);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.IMAGE_FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !properties.getAllowedContentTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }

    /**
     * 원본 파일명은 키에 쓰지 않는다. 경로 조작·중복·비ASCII 파일명 문제를 한 번에 피하려고
     * UUID로 새 이름을 만들고 확장자만 Content-Type에서 유도한다.
     */
    private String buildKey(String contentType) {
        String extension =
                switch (contentType) {
                    case "image/png" -> "png";
                    case "image/webp" -> "webp";
                    case "image/heic" -> "heic";
                    default -> "jpg";
                };
        return "%s/%s.%s".formatted(properties.getKeyPrefix(), UUID.randomUUID(), extension);
    }

    private String toPublicUrl(String key) {
        String base = properties.getPublicBaseUrl();
        if (!base.isBlank()) {
            return "%s/%s".formatted(base.replaceAll("/+$", ""), key);
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(properties.getBucket(), region, key);
    }
}
