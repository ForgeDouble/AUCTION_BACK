package com.example.auction.user.service;

import com.example.auction.user.domain.CustomUserDetails;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Autowired
    public CustomUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("해당 사용자(" + email + ")를 찾을 수 없습니다."));

        String role = "ROLE_" + user.getAuthority().name(); // Authority.ADMIN -> ROLE_ADMIN
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                true, true, true, true,
                authorities
        );
    }
}

