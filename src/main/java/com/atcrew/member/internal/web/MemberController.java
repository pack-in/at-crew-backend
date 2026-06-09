package com.atcrew.member.internal.web;

import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
class MemberController {

    private final MemberService memberService;

    MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberInfo register(@RequestBody @Valid RegisterRequest request) {
        return memberService.register(request.loginEmail(), request.handle(), request.name(), request.creatorRole());
    }

    @GetMapping("/{handle}")
    public MemberInfo findByHandle(@PathVariable String handle) {
        return memberService.findByHandle(handle);
    }

    @PutMapping("/{id}/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProfile(@PathVariable Long id, @RequestBody @Valid UpdateProfileRequest request) {
        memberService.updateProfile(id, request.name(), request.profileImage(),
                request.creatorRole(), request.employmentStatus(), request.experienceLevel());
    }

    @PutMapping("/{id}/details")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDetails(@PathVariable Long id, @RequestBody @Valid UpdateDetailsRequest request) {
        memberService.updateDetails(id, request.birthDate(), request.school(), request.location(),
                request.contactEmail(), request.socialMediaLink(), request.twitter(),
                request.desiredField(), request.creativeTools(), request.career(), request.keywords());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        memberService.deactivate(id);
    }
}
