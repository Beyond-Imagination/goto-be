package kr.bi.go_to.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.tags.Tag;
import kr.bi.go_to.enums.SwaggerTag;
import org.junit.jupiter.api.Test;

class SwaggerTagTest {

    @Test
    void Swagger_태그는_알파벳_접두사_기준_표시_순서로_관리된다() {
        assertThat(SwaggerTag.openApiTags()).extracting(Tag::getName).containsExactly("A. Auth", "B. Help Request", "C. Place");
    }
}
