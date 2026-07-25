package kr.bi.go_to.batch.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Tour API는 결과 0건일 때 {@code body.items}를 객체가 아닌 빈 문자열 {@code ""}로 내려준다. 이
 * deserializer는 blank 문자열만 {@code null}로 정규화하고, 객체는 기존 {@link
 * TourApiResponseDto.Items} 매핑을 유지한다.
 */
public class TourApiItemsDeserializer extends StdDeserializer<TourApiResponseDto.Items> {

    public TourApiItemsDeserializer() {
        super(TourApiResponseDto.Items.class);
    }

    @Override
    public TourApiResponseDto.Items deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            return (TourApiResponseDto.Items) ctxt.handleUnexpectedToken(handledType(), p);
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.START_OBJECT) {
            JsonNode node = ctxt.readTree(p);
            return ctxt.readTreeAsValue(node, TourApiResponseDto.Items.class);
        }
        return (TourApiResponseDto.Items) ctxt.handleUnexpectedToken(handledType(), p);
    }
}
