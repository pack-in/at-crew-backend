package com.atcrew.common.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * {@code List<String>} ↔ JSON 문자열 컬럼 변환 컨버터.
 *
 * <p>표시 전용 리스트(검색 대상이 아닌 필드)를 JSON 컬럼에 저장할 때 사용한다
 * (docs/design/mariadb-migration-design.md §3.2, docs/design/recruit-module-design.md §1).
 * 특정 엔티티에 종속되지 않는 범용 컨버터이므로 {@code autoApply = false}로 두고,
 * 각 필드에 {@code @Convert(converter = StringListJsonConverter.class)}를 명시적으로 지정해 사용한다.
 */
@Converter(autoApply = false)
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("List<String>을 JSON으로 직렬화하는 데 실패했습니다", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 컬럼을 List<String>으로 역직렬화하는 데 실패했습니다", e);
        }
    }
}
