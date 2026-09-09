package kr.bi.go_to.controller.upload.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ImageUploadResponse", description = "업로드된 이미지의 조회용 URL")
public record ImageUploadResponse(
        @Schema(
                        description = "제보 생성 요청의 photoUrls에 그대로 넣는 공개 URL",
                        example = "https://goto-images.s3.ap-northeast-2.amazonaws.com/obstacle-reports/9f1c.jpg")
                String url) {}
