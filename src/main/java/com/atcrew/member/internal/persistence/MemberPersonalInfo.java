package com.atcrew.member.internal.persistence;

import jakarta.persistence.Embeddable;

@Embeddable
class MemberPersonalInfo {

    private String birthDate;
    private String school;
    private String location;

    protected MemberPersonalInfo() {
    }

    MemberPersonalInfo(String birthDate, String school, String location) {
        this.birthDate = birthDate;
        this.school = school;
        this.location = location;
    }

    MemberPersonalInfo withBirthDate(String birthDate) {
        return new MemberPersonalInfo(birthDate, this.school, this.location);
    }

    MemberPersonalInfo withSchool(String school) {
        return new MemberPersonalInfo(this.birthDate, school, this.location);
    }

    MemberPersonalInfo withLocation(String location) {
        return new MemberPersonalInfo(this.birthDate, this.school, location);
    }

    String getBirthDate() { return birthDate; }
    String getSchool() { return school; }
    String getLocation() { return location; }
}
