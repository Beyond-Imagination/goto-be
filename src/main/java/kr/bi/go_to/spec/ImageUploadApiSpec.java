package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.bi.go_to.controller.upload.response.ImageUploadResponse;
import kr.bi.go_to.enums.SwaggerTag;
import kr.bi.go_to.exception.ErrorResponse;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = SwaggerTag.UPLOAD_NAME, description = SwaggerTag.UPLOAD_DESCRIPTION)
public interface ImageUploadApiSpec {

    @Operation(
            tags = SwaggerTag.UPLOAD_NAME,
            summary = "제보 사진 업로드",
            description = "이미지 한 장을 업로드하고 조회용 URL을 반환합니다. 반환된 URL을 제보 생성 요청의 photoUrls에 넣어 주세요.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "업로드 성공",
                content = @Content(schema = @Schema(implementation = ImageUploadResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "이미지가 아니거나 용량 초과",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "저장소 미설정 또는 업로드 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    ImageUploadResponse uploadImage(MultipartFile file);
}
