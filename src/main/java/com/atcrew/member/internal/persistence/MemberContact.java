package com.atcrew.member.internal.persistence;

import jakarta.persistence.Embeddable;

@Embeddable
class MemberContact {

    private String contactEmail;
    private String socialMediaLink;
    private String twitter;

    protected MemberContact() {
    }

    MemberContact(String contactEmail, String socialMediaLink, String twitter) {
        this.contactEmail = contactEmail;
        this.socialMediaLink = socialMediaLink;
        this.twitter = twitter;
    }

    MemberContact withContactEmail(String contactEmail) {
        return new MemberContact(contactEmail, this.socialMediaLink, this.twitter);
    }

    MemberContact withSocialMediaLink(String socialMediaLink) {
        return new MemberContact(this.contactEmail, socialMediaLink, this.twitter);
    }

    MemberContact withTwitter(String twitter) {
        return new MemberContact(this.contactEmail, this.socialMediaLink, twitter);
    }

    String getContactEmail() { return contactEmail; }
    String getSocialMediaLink() { return socialMediaLink; }
    String getTwitter() { return twitter; }
}
