package com.atcrew.member.internal.persistence;

import jakarta.persistence.Embeddable;

@Embeddable
class MemberCareer {

    private String desiredField;
    private String creativeTools;
    private String career;

    protected MemberCareer() {
    }

    MemberCareer(String desiredField, String creativeTools, String career) {
        this.desiredField = desiredField;
        this.creativeTools = creativeTools;
        this.career = career;
    }

    MemberCareer withDesiredField(String desiredField) {
        return new MemberCareer(desiredField, this.creativeTools, this.career);
    }

    MemberCareer withCreativeTools(String creativeTools) {
        return new MemberCareer(this.desiredField, creativeTools, this.career);
    }

    MemberCareer withCareer(String career) {
        return new MemberCareer(this.desiredField, this.creativeTools, career);
    }

    String getDesiredField() { return desiredField; }
    String getCreativeTools() { return creativeTools; }
    String getCareer() { return career; }
}
