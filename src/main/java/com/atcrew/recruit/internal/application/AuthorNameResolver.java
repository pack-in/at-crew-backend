package com.atcrew.recruit.internal.application;

import com.atcrew.member.MemberService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 작성자/지원자 표시명을 member 모듈 공개 API로 조회하는 헬퍼.
 * recruit은 Member를 로컬에 복제하지 않으므로 표시명이 필요한 응답마다 이 헬퍼를 거친다.
 */
@Component
class AuthorNameResolver {

    private final MemberService memberService;

    AuthorNameResolver(MemberService memberService) {
        this.memberService = memberService;
    }

    // 표시명 조회 실패(탈퇴 등) 시 응답 자체를 막지 않고 null로 대체한다.
    String resolve(String memberId) {
        try {
            return memberService.findById(memberId).name();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 목록 응답용 일괄 조회 — 페이지 내 중복 회원에 대한 반복 조회를 피하기 위해 고유 ID만 조회한다(N+1 완화).
     * resolve()가 null을 반환할 수 있어 Collectors.toMap 대신 HashMap에 직접 채운다(toMap은 null 값을 허용하지 않음).
     */
    Map<String, String> resolveAll(Collection<String> memberIds) {
        Set<String> uniqueIds = new HashSet<>(memberIds);
        Map<String, String> names = new HashMap<>();
        uniqueIds.forEach(id -> names.put(id, resolve(id)));
        return names;
    }
}
