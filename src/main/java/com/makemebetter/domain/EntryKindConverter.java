package com.makemebetter.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 让 EntryKind 在库里存成 'study' / 'daily' / 'diary'，而不是 Java 枚举名。 */
@Converter(autoApply = false)
public class EntryKindConverter implements AttributeConverter<EntryKind, String> {

    @Override
    public String convertToDatabaseColumn(EntryKind kind) {
        return kind == null ? null : kind.getCode();
    }

    @Override
    public EntryKind convertToEntityAttribute(String code) {
        return code == null ? null : EntryKind.fromCode(code);
    }
}
