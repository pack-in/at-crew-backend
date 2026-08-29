package com.atcrew.artwork.internal.domain.artwork;

import com.atcrew.artwork.ArtworkCustomTagType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * 직접입력 태그 (기획서 업로드-R13). 최대 10자, 앞뒤 공백 제거, 같은 항목 안에서 중복 불가.
 * 값 검증은 {@link Artwork#updateDetails}가 저장 직전에 수행한다.
 */
@Embeddable
public class ArtworkCustomTag {

    public static final int MAX_LENGTH = 10;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type")
    private ArtworkCustomTagType type;

    @Column(name = "value")
    private String value;

    protected ArtworkCustomTag() {
    }

    public ArtworkCustomTag(ArtworkCustomTagType type, String value) {
        this.type = type;
        this.value = value;
    }

    public ArtworkCustomTagType getType() { return type; }
    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArtworkCustomTag other)) return false;
        return type == other.type && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }
}
