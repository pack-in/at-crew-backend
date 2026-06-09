package com.atcrew.auth.internal.application;

import com.atcrew.auth.internal.persistence.RefreshTokenRepository;
import com.atcrew.member.MemberDeactivatedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Service
class AuthEventListener {

    private final RefreshTokenRepository refreshTokenRepository;

    AuthEventListener(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @ApplicationModuleListener
    void on(MemberDeactivatedEvent event) {
        refreshTokenRepository.deleteAllByMemberId(event.memberId());
    }
}
