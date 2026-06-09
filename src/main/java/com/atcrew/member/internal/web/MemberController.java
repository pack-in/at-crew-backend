package com.atcrew.member.internal.web;

import com.atcrew.member.AddCareerCommand;
import com.atcrew.member.CareerEntryInfo;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import com.atcrew.member.UpdateCareerCommand;
import com.atcrew.member.UpdateProfileCommand;
import com.atcrew.member.internal.web.dto.AddCareerRequest;
import com.atcrew.member.internal.web.dto.RegisterRequest;
import com.atcrew.member.internal.web.dto.UpdateCareerRequest;
import com.atcrew.member.internal.web.dto.UpdateDetailsRequest;
import com.atcrew.member.internal.web.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PatchMapping("/{id}/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProfile(@PathVariable String id, @RequestBody @Valid UpdateProfileRequest request) {
        memberService.updateProfile(id, new UpdateProfileCommand(
                request.name(), request.creatorRole(), request.employmentStatus(),
                request.activityFields(), request.experienceLevel(), request.activeRegions(),
                request.totalSlotCount(), request.availableSlotCount(), request.teamExperiences()));
    }

    @PatchMapping("/{id}/details")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDetails(@PathVariable String id, @RequestBody @Valid UpdateDetailsRequest request) {
        memberService.updateDetails(id, request.contact(), request.sns(), request.tools());
    }

    @PostMapping("/{id}/careers")
    @ResponseStatus(HttpStatus.CREATED)
    public CareerEntryInfo addCareer(@PathVariable String id, @RequestBody @Valid AddCareerRequest request) {
        return memberService.addCareer(id, new AddCareerCommand(
                request.workTitle(), request.role(), request.startDate(),
                request.endDate(), request.ongoing(), request.description()));
    }

    @PatchMapping("/{id}/careers/{careerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateCareer(@PathVariable String id, @PathVariable String careerId,
                             @RequestBody @Valid UpdateCareerRequest request) {
        memberService.updateCareer(id, careerId, new UpdateCareerCommand(
                request.workTitle(), request.role(), request.startDate(),
                request.endDate(), request.ongoing(), request.description()));
    }

    @DeleteMapping("/{id}/careers/{careerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCareer(@PathVariable String id, @PathVariable String careerId) {
        memberService.deleteCareer(id, careerId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String id) {
        memberService.deactivate(id);
    }
}
