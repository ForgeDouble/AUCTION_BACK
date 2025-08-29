package com.example.auction.user.service;

import com.example.auction.common.auth.JwtTokenProvider;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.dto.*;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomTokenExpiredStrategy customTokenExpiredStrategy;
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, CustomTokenExpiredStrategy customTokenExpiredStrategy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.customTokenExpiredStrategy = customTokenExpiredStrategy;
    }

    /* 회원가입 */
    @Transactional
    public User register(UserRegisterDto registerDto) {
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

        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            throw new RuntimeException("정지된 계정입니다. 해제 시각: " + user.getSuspendedUntil());
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String token = jwtTokenProvider.createAccessToken(user);

        long ttl = jwtTokenProvider.getRemainingSeconds(token);
        customTokenExpiredStrategy.save(user.getEmail(), token, ttl);

        return token;
    }

    @Transactional(readOnly = true)
    public void logout() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        customTokenExpiredStrategy.delete(email);
    }


    /* 회원정보 수정 */
    @Transactional
    public User update(UserUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        user.update(dto);

        return userRepository.save(user);
    }

    /* 회원 탈퇴 */
    @Transactional
    public void delete(UserDeleteDto deleteDto, String deletedBy) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("현재 로그인한 유저 정보를 찾을 수 없습니다."));

        User targetUser = userRepository.findById(deleteDto.getUserId())
                .orElseThrow(() -> new RuntimeException("삭제하려는 유저가 존재하지 않습니다."));

        if (!user.getUserId().equals(targetUser.getUserId()) && user.getAuthority() != Authority.ADMIN) {
            throw new RuntimeException("본인 또는 관리자만 탈퇴할 수 있습니다.");
        }

        targetUser.softDelete();
        userRepository.save(targetUser);
    }


    /* 회원 상세 조회 (마이페이지용) */
    @Transactional(readOnly = true)
    public UserDetailDto getMyDetail() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));
        return UserDetailDto.fromEntity(user);
    }

    /* 타겟팅 조회 */
    @Transactional(readOnly = true)
    public Object getUserViewByTargetId(Long userId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User requester = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("요청자 정보를 찾을 수 없습니다."));

        // 타겟 (삭제되지 않은 유저만 조회)
        User target = userRepository.findById(userId)
                .filter(u -> u.getDelYn() == DelYN.N)
                .orElseThrow(() -> new RuntimeException("조회 대상 유저가 존재하지 않습니다."));

        if (requester.getUserId().equals(target.getUserId())) {
            return UserDetailDto.fromEntity(target);
        }

        // 관리자의 유저 조회
        if (requester.getAuthority() == Authority.ADMIN) {
            return AdminUserListDto.fromEntityForAdmin(target);
        }

        // 유저의 유저간 조회
        return PublicUserListDto.fromEntityForPublic(target);
    }



    /* 회원 목록 조회 */
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User requester = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("요청자 정보를 찾을 수 없습니다."));
        if (requester.getAuthority() != Authority.ADMIN) {
            throw new RuntimeException("관리자만 조회할 수 있습니다.");
        }

        return userRepository.findAll().stream()
                .filter(u -> u.getDelYn() == DelYN.N)
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
    }

    /* 관리자 여부 확인 */
    @Transactional(readOnly = true)
    public void checkAdminAuthority() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));
        if (user.getAuthority() != Authority.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("관리자만 접근 가능합니다.");
        }
    }

    /* 닉네임 생성 및 업데이트 */
    @Transactional
    public void updateNickname(UserNicknameUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        String newNickname = dto.getNickname();
        if (newNickname == null || newNickname.trim().isEmpty()) {
            throw new RuntimeException("닉네임을 입력해 주세요.");
        }

        newNickname = newNickname.trim();

        // 닉네임 2~8 , 제한문자 추가
        if (newNickname.length() < 2 || newNickname.length() > 8) {
            throw new RuntimeException("닉네임은 2~8자로 입력해 주세요.");
        }
        if (!newNickname.matches("^[A-Za-z0-9가-힣_]+$")) {
            throw new RuntimeException("닉네임은 영문,숫자,한글,_ 만 사용가능합니다.");
        }

        if (newNickname.equals(user.getNickname())) {
            return;
        }

        //
        if (user.getLastNicknameChangedAt() != null) {
            long days = ChronoUnit.DAYS.between(user.getLastNicknameChangedAt(), LocalDateTime.now());
            if (days < 7) {
                long remain = 7 - days;
                throw new RuntimeException("닉네임은 " + remain + "일 이후 변경 가능합니다.");
            }
        }

        // 중복 체크
        boolean exists = userRepository.existsByNicknameAndDelYn(newNickname, DelYN.N);
        if (exists) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        }

        user.changeNickname(newNickname);
        userRepository.save(user);
    }
}

