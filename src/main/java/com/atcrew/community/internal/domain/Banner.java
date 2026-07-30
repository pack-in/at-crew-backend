package com.atcrew.community.internal.domain;

import com.atcrew.community.BannerStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "banners")
public class Banner {

    @Id
    private String id;

    private String memberId;
    private String imageUrl;
    private String linkUrl;
    private int sortOrder;
    private BannerStatus status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    protected Banner() {
    }

    public static Banner create(String memberId, String imageUrl, String linkUrl, int sortOrder) {
        Banner banner = new Banner();
        banner.memberId = memberId;
        banner.imageUrl = imageUrl;
        banner.linkUrl = linkUrl;
        banner.sortOrder = sortOrder;
        banner.status = BannerStatus.ACTIVE;
        return banner;
    }

    public void update(String imageUrl, String linkUrl, Integer sortOrder) {
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (linkUrl != null) this.linkUrl = linkUrl;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }

    public void delete() {
        this.status = BannerStatus.DELETED;
    }

    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getImageUrl() { return imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public int getSortOrder() { return sortOrder; }
    public BannerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
