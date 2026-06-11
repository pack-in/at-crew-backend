package com.atcrew.member;

public enum AccountType {
    CREATOR("창작자/개인"),
    COMPANY("기업");

    private final String description;

    AccountType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
