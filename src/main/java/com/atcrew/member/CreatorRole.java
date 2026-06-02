package com.atcrew.member;

public enum CreatorRole {
    WEBTOON("웹툰작가"),
    ILLUSTRATOR("일러스트작가"),
    WEB_NOVELIST("웹소설작가"),
    OTHER("기타");

    private final String description;

    CreatorRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
