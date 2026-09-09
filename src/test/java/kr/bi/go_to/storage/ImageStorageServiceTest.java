package kr.bi.go_to.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.properties.ImageStorageProperties;
import kr.bi.go_to.service.storage.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class ImageStorageServiceTest {

    private static final String REGION = "ap-northeast-2";

    private S3Client s3Client;
    private ImageStorageProperties properties;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        properties = new ImageStorageProperties();
        properties.setBucket("goto-images");
    }

    private ImageStorageService service() {
        return new ImageStorageService(s3Client, properties, REGION);
    }

    private MockMultipartFile image(String contentType, byte[] content) {
        return new MockMultipartFile("file", "photo.jpg", contentType, content);
    }

    @Test
    @DisplayName("이미지를 올리면 S3 키가 담긴 공개 URL을 돌려준다")
    void uploadsAndReturnsPublicUrl() {
        String url = service().upload(image("image/jpeg", new byte[] {1, 2, 3}));

        assertThat(url).startsWith("https://goto-images.s3.ap-northeast-2.amazonaws.com/obstacle-reports/");
        assertThat(url).endsWith(".jpg");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("publicBaseUrl을 설정하면 그 주소를 앞에 붙인다")
    void usesPublicBaseUrlWhenConfigured() {
        properties.setPublicBaseUrl("https://cdn.example.test/");

        String url = service().upload(image("image/png", new byte[] {1}));

        assertThat(url).startsWith("https://cdn.example.test/obstacle-reports/");
        assertThat(url).endsWith(".png");
    }

    @Test
    @DisplayName("원본 파일명은 키에 쓰지 않는다")
    void doesNotUseOriginalFilename() {
        String url =
                service().upload(new MockMultipartFile("file", "../../비ASCII 이름.jpg", "image/jpeg", new byte[] {1}));

        assertThat(url).doesNotContain("비ASCII");
        assertThat(url).doesNotContain("..");
    }

    @Test
    @DisplayName("버킷이 설정되지 않았으면 503을 던지고 업로드를 시도하지 않는다")
    void failsWhenBucketMissing() {
        properties.setBucket("");

        assertThatThrownBy(() -> service().upload(image("image/jpeg", new byte[] {1})))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("이미지가 아닌 Content-Type은 400으로 막는다")
    void rejectsNonImageContentType() {
        assertThatThrownBy(() -> service().upload(image("application/pdf", new byte[] {1})))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    @DisplayName("빈 파일은 400으로 막는다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service().upload(image("image/jpeg", new byte[] {})))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    @DisplayName("허용 용량을 넘으면 400으로 막는다")
    void rejectsTooLargeFile() {
        properties.setMaxFileSizeBytes(2);

        assertThatThrownBy(() -> service().upload(image("image/jpeg", new byte[] {1, 2, 3})))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("허용 Content-Type 목록을 좁히면 그에 맞게 막는다")
    void honoursAllowedContentTypes() {
        properties.setAllowedContentTypes(List.of("image/png"));

        assertThatThrownBy(() -> service().upload(image("image/jpeg", new byte[] {1})))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    @DisplayName("S3 호출이 실패하면 503으로 감싼다")
    void wrapsS3Failure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service().upload(image("image/jpeg", new byte[] {1})))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_UPLOAD_FAILED);
    }
}
