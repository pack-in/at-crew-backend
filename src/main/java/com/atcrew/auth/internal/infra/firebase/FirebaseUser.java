package com.atcrew.auth.internal.infra.firebase;

import com.atcrew.member.AuthProvider;

public record FirebaseUser(String email, AuthProvider provider, boolean emailVerified) {}
