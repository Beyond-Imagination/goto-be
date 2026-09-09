package kr.bi.go_to.controller.upload;

import kr.bi.go_to.controller.upload.response.ImageUploadResponse;
import kr.bi.go_to.service.storage.ImageStorageService;
import kr.bi.go_to.spec.ImageUploadApiSpec;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 제보 사진 업로드. 업로드 후 받은 URL을 제보 생성 요청의 photoUrls에 넣는다.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class ImageUploadController implements ImageUploadApiSpec {

    private final ImageStorageService imageStorageService;

    public ImageUploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @Override
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse uploadImage(@RequestPart("file") MultipartFile file) {
        return new ImageUploadResponse(imageStorageService.upload(file));
    }
}
