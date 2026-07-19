package kr.bi.go_to.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.bi.go_to.enums.SwaggerTag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Goto API")
                        .description("함께가길 프로젝트 백엔드 API 문서")
                        .version("v1"))
                .tags(SwaggerTag.openApiTags())
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    GlobalOpenApiCustomizer sortTagsByNameCustomizer() {
        return openApi -> {
            Map<String, Tag> tags = new LinkedHashMap<>();

            for (Tag tag : SwaggerTag.openApiTags()) {
                tags.put(tag.getName(), tag);
            }

            if (openApi.getTags() != null) {
                for (Tag tag : openApi.getTags()) {
                    tags.put(tag.getName(), tag);
                }
            }

            openApi.setTags(tags.values().stream()
                    .sorted(Comparator.comparing(Tag::getName))
                    .toList());
        };
    }
}
