package com.example.auction.common.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUserContext {

    private SecurityUserContext() {}

    public static AuthUserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new IllegalStateException("인증 정보가 없습니다.");
        Object p = auth.getPrincipal();
        if (p instanceof AuthUserPrincipal principal) return principal;
        throw new IllegalStateException("AuthUserPrincipal 이 아닙니다. principal=" + p);
    }

    public static Long userId() {
        return principal().getUserId();
    }

    public static String email() {
        return principal().getEmail();
    }


}