package com.example.auction.user.service;

import com.example.auction.common.auth.JwtTokenProvider;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.AccountSuspendedException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.domain.UserStatus;
import com.example.auction.user.dto.*;
import com.example.auction.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomTokenExpiredStrategy customTokenExpiredStrategy;
    private final UserStatusService userStatusService;
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, CustomTokenExpiredStrategy customTokenExpiredStrategy, UserStatusService userStatusService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.customTokenExpiredStrategy = customTokenExpiredStrategy;
        this.userStatusService = userStatusService;
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

    // 관리자 계정 생성 - ADMIN / INQUIRY
    @Transactional
    public User createSpecialUser(AdminUserRegisterDto adminUserRegisterDto, Authority authority) {
        if (userRepository.findByEmail(adminUserRegisterDto.getEmail()).isPresent()) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(adminUserRegisterDto.getPassword());
        User newUser = adminUserRegisterDto.toEntity(authority);
        newUser.setPassword(encodedPassword);

        return userRepository.save(newUser);

    }

    // ADMIN 계정 생성
    @Transactional
    public User createAdminUser(AdminUserRegisterDto dto) {
        return createSpecialUser(dto, Authority.ADMIN);
    }

    // INQUIRY 계정 생성
    @Transactional
    public User createInquiryUser(AdminUserRegisterDto dto) {
        return createSpecialUser(dto, Authority.INQUIRY);
    }

    /* 로그인 */
    @Transactional(readOnly = true)
    public String login(UserLoginDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> {
                    log.warn("[EMAIL_NOT_FOUND] 존재하지 않는 이메일 email={}",dto.getEmail());
                    return new UnauthorizedAccessException("LOGIN_FAILED" , "이메일 또는 비밀번호가 일치하지 않습니다.");
                }
        );

        if (user.getDelYn() == DelYN.Y) {
            log.warn("[DELETED_ACCOUNT] 탈퇴된 계정 email={}", dto.getEmail());
            throw new UnauthorizedAccessException("LOGIN_FAILED" , "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            String until = user.getSuspendedUntil()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            log.warn("[SUSPENDED_ACCOUNT] 정지된 계정 email={} until={}", dto.getEmail(), until);
            throw new AccountSuspendedException("정지된 계정입니다." , until);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("[INVALID_PASSWORD] 일치하지 않는 비밀번호");
            throw new UnauthorizedAccessException("LOGIN_FAILED" , "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        String token = jwtTokenProvider.createAccessToken(user);

        long ttl = jwtTokenProvider.getRemainingSeconds(token);
        customTokenExpiredStrategy.save(user.getEmail(), token, ttl);

        // 로그인 시점부터 접속중 처리
        userStatusService.touch(user.getEmail());

        return token;
    }

    /* 로그아웃 */
    public void logout() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        customTokenExpiredStrategy.delete(email);
        userStatusService.clear(email);
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

        //  본인 탈퇴 허용
        if (user.getUserId().equals(targetUser.getUserId())) {
        // 자기 자신 삭제는 허용
        } else {
            // 본인이 ADMIN 이 아니면, 남을 지울 수 없음
            if (user.getAuthority() != Authority.ADMIN) {
                throw new RuntimeException("본인 또는 관리자만 탈퇴할 수 있습니다.");
            }

            // 타겟이 ADMIN 인 경우, 상위 ADMIN 만 삭제 가능 -> USERID 가 더 작은 쪽으로 셋팅
            if (targetUser.getAuthority() == Authority.ADMIN) {
                if (user.getUserId() >= targetUser.getUserId()) {
                    throw new RuntimeException("상위 ADMIN만 하위 ADMIN 계정을 삭제할 수 있습니다.");
                }
            }
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

        if (Boolean.TRUE.equals(user.getViewOnly())) {
            throw new RuntimeException("임시 제한 상태라 닉네임을 변경할 수 없습니다.");
        }
        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            String until = user.getSuspendedUntil()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            throw new RuntimeException("정지된 계정입니다. 해제 시각: " + until);
        }

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

    /* 타 유저 상태 조회 */
    @Transactional(readOnly = true)
    public UserStatus getStatusByUserId(Long userId) {
        User user = userRepository.findByUserIdAndDelYn(userId, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));
        return userStatusService.getStatus(user.getEmail());
    }


    @Transactional
    public TokenExtendRes extendLogin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            throw new RuntimeException("정지된 계정입니다.");
        }

        String newToken = jwtTokenProvider.createAccessToken(user);

        long ttl = jwtTokenProvider.getRemainingSeconds(newToken);
        customTokenExpiredStrategy.save(email, newToken, ttl);

        // 접속 유지(선택)
        userStatusService.touch(email);

        return new TokenExtendRes(newToken, ttl);
    }


    @Transactional(readOnly = true)
    public PageUserListDto<AdminUserRowDto> getUsersPageForAdmin(String authorityText, String keyword, int page, int size) {
        checkAdminAuthority();

        Authority authority = null;
        if (authorityText != null && !authorityText.isBlank() && !"ALL".equalsIgnoreCase(authorityText)) {
            authority = Authority.valueOf(authorityText.toUpperCase());
        }

        String k = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100); // 최대 100

        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "userId"));
        var p = userRepository.searchUsersForAdmin(DelYN.N, authority, k, pageable);

        var mapped = p.map(AdminUserRowDto::adminUserRowDto);
        return PageUserListDto.from(mapped);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getUserRoleCountsForAdmin() {
        checkAdminAuthority();
        long admin = userRepository.countByAuthorityAndDelYn(Authority.ADMIN, DelYN.N);
        long inquiry = userRepository.countByAuthorityAndDelYn(Authority.INQUIRY, DelYN.N);
        long user = userRepository.countByAuthorityAndDelYn(Authority.USER, DelYN.N);
        return java.util.Map.of("ADMIN", admin, "INQUIRY", inquiry, "USER", user);
    }

    /* 판매자 정보를 조회하는 함수 */
    @Transactional(readOnly = true)
    public SellerDto getSellerInfoByProductId(Long productId) {
       SellerDto dto = userRepository.findSellerInfoByProductId(productId)
               .orElseThrow(() -> new ResourceNotFoundException("user"));
       return dto;
    }

    /* 토큰에서 정보를 가져오는 함수 */
    public UserTokenDto getTokenInfo() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserTokenDto dto = userRepository.findUserTokenInfoByEmail(email)
                .orElseThrow(() -> {
                    log.warn("[DATA_NOT_FOUND] 존재하지 않는 이메일 email={}", email);
                    return new ResourceNotFoundException("user");
                });
        return dto;
    }
}

