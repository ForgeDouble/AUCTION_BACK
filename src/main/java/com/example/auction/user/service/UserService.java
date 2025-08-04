package com.example.auction.user.service;

import com.example.auction.common.auth.JwtTokenProvider;
import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.dto.*;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /* 회원가입 */
    @Transactional
    public User register(UserRegisterDto registerDto, MultipartFile profileImage) {
        if (userRepository.findByEmail(registerDto.getEmail()).isPresent()) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }
        String encodedPassword = passwordEncoder.encode(registerDto.getPassword());
        User newUser = registerDto.toEntity();
        newUser.setPassword(encodedPassword);

        return userRepository.save(newUser);
    }

    /* 로그인 */
    @Transactional(readOnly = true)
    public String login(UserLoginDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 이메일입니다."));

        if (user.getDelYn() == DelYN.Y) {
            throw new RuntimeException("탈퇴된 계정입니다.");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
        return jwtTokenProvider.createAccessToken(user);
    }


    /* 회원정보 수정 */
    @Transactional
    public User update(UserUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        user.update(dto);


        return userRepository.save(user);
    }

    /* 회원 탈퇴 */
    @Transactional
    public void delete(Long userId, String deletedBy) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        user.softDelete();
        userRepository.save(user);
    }


    /* 회원 상세 조회 */
    @Transactional(readOnly = true)
    public UserDetailDto getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));
        return UserDetailDto.fromEntity(user);
    }

    /* 회원 목록 조회 */
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getDelYn() == DelYN.N)
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
    }

    /* 관리자 여부 확인 */
    @Transactional(readOnly = true)
    public boolean checkAdminAuthority() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));
        return user.getAuthority() == Authority.ADMIN;
    }
}

