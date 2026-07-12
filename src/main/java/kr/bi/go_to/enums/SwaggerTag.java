package kr.bi.go_to.enums;

import io.swagger.v3.oas.models.tags.Tag;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum SwaggerTag {
    AUTH("A. Auth", "Authentication API"),
    HELP_REQUEST("B. Help Request", "Help request API"),
    PLACE("C. Place", "Place search API"),
    REPORT("D. Report", "Facility status report API");

    public static final String AUTH_NAME = "A. Auth";
    public static final String AUTH_DESCRIPTION = "Authentication API";
    public static final String HELP_REQUEST_NAME = "B. Help Request";
    public static final String HELP_REQUEST_DESCRIPTION = "Help request API";
    public static final String PLACE_NAME = "C. Place";
    public static final String PLACE_DESCRIPTION = "Place search API";
    public static final String REPORT_NAME = "D. Report";
    public static final String REPORT_DESCRIPTION = "Facility status report API";

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
