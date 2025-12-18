package com.example.auction.common.auth;

import com.example.auction.user.domain.Authority;
import lombok.AllArgsConstructor;
import lombok.Getter;

import com.example.auction.user.domain.Authority;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserPrincipal implements UserDetails, Serializable {
    private static final long serialVersionUID = 1L;
    private Long userId;
    private String email;
    private Authority authority;
    private String nickname;
    private String profileImageUrl;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (authority == null) return List.of();
        return List.of(new SimpleGrantedAuthority("ROLE_" + authority.name()));
    }
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }


}
