package kr.bi.go_to.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.tags.Tag;
import kr.bi.go_to.enums.SwaggerTag;
import org.junit.jupiter.api.Test;

class SwaggerTagTest {

    @Test
    void managesSwaggerTagsInAlphabeticalDisplayOrder() {
        assertThat(SwaggerTag.openApiTags())
                .extracting(Tag::getName)
                .containsExactly("A. Auth");
    }
}
