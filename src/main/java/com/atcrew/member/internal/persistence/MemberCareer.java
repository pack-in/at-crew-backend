package com.atcrew.member.internal.persistence;

class MemberCareer {

    private String desiredField;  // 희망 분야 (라이트 호환)
    private String creativeTools; // 사용 가능한 툴

    protected MemberCareer() {
    }

    MemberCareer(String desiredField, String creativeTools) {
        this.desiredField = desiredField;
        this.creativeTools = creativeTools;
    }

    MemberCareer withDesiredField(String desiredField) {
        return new MemberCareer(desiredField, this.creativeTools);
    }

    MemberCareer withCreativeTools(String creativeTools) {
        return new MemberCareer(this.desiredField, creativeTools);
    }

    String getDesiredField() { return desiredField; }
    String getCreativeTools() { return creativeTools; }
}
