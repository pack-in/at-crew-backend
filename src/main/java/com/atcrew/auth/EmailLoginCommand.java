package com.atcrew.auth;

public record EmailLoginCommand(String email, String password) {
    @Override
    public String toString() {
        return "EmailLoginCommand[email=" + email + ", password=****]";
    }
}
