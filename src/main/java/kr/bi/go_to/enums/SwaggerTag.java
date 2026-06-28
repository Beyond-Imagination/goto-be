package kr.bi.go_to.enums;

import io.swagger.v3.oas.models.tags.Tag;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum SwaggerTag {
    AUTH("A. Auth", "인증 API"),
    ADMIN("B. Admin", "관리자 API");

    public static final String AUTH_NAME = "A. Auth";
    public static final String AUTH_DESCRIPTION = "인증 API";

    public static final String ADMIN_NAME = "B. Admin";
    public static final String ADMIN_DESCRIPTION = "관리자 API";

    private final String tagName;
    private final String description;

    SwaggerTag(String tagName, String description) {
        this.tagName = tagName;
        this.description = description;
    }

    public String tagName() {
        return tagName;
    }

    public String description() {
        return description;
    }

    public Tag toOpenApiTag() {
        return new Tag().name(tagName).description(description);
    }

    public static List<Tag> openApiTags() {
        return Arrays.stream(values())
                .sorted(Comparator.comparing(SwaggerTag::tagName))
                .map(SwaggerTag::toOpenApiTag)
                .toList();
    }
}
