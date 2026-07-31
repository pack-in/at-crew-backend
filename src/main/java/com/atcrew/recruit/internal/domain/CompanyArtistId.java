package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * 관심 작가 테이블의 복합 기본키 (기업 Member ID, 작가 Member ID) — 설계 §2.7.
 * 좋아요·최근 본 작가 두 테이블이 같은 키 구조를 쓰므로 임베디드 ID를 공유한다.
 */
@Embeddable
public class CompanyArtistId implements Serializable {

    @Column(name = "company_member_id", length = 36, nullable = false)
    private String companyMemberId;

    @Column(name = "artist_member_id", length = 36, nullable = false)
    private String artistMemberId;

    protected CompanyArtistId() {
    }

    public CompanyArtistId(String companyMemberId, String artistMemberId) {
        this.companyMemberId = companyMemberId;
        this.artistMemberId = artistMemberId;
    }

    public String getCompanyMemberId() { return companyMemberId; }
    public String getArtistMemberId() { return artistMemberId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompanyArtistId other)) {
            return false;
        }
        return Objects.equals(companyMemberId, other.companyMemberId)
                && Objects.equals(artistMemberId, other.artistMemberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyMemberId, artistMemberId);
    }
}
