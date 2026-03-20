package com.example.auction.user.service;

import com.example.auction.common.auth.JwtTokenProvider;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.*;
import com.example.auction.common.service.CustomTokenExpiredStrategy;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.domain.UserStatus;
import com.example.auction.user.dto.*;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.repository.LoginUserProjection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomTokenExpiredStrategy customTokenExpiredStrategy;
    private final UserStatusService userStatusService;
    private final ValidationService validationService;

    public UserService(UserRepository userRepository, ProductRepository productRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, CustomTokenExpiredStrategy customTokenExpiredStrategy, UserStatusService userStatusService, ValidationService validationService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.customTokenExpiredStrategy = customTokenExpiredStrategy;
        this.userStatusService = userStatusService;
        this.validationService = validationService;
    }

    /* 회원가입 */
    @Transactional
    public User register(UserRegisterDto registerDto) {
        validationService.validateRegister(registerDto);

        if (userRepository.findByEmail(registerDto.getEmail()).isPresent()) {
            throw new BadRequestException("EMAIL_ALREADY_EXISTS", "이미 존재하는 이메일입니다.");
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
            throw new BadRequestException("EMAIL_ALREADY_EXISTS", "이미 존재하는 이메일입니다.");
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
//    @Transactional(readOnly = true)
    public String login(UserLoginDto dto) {
//        long totalStart = System.nanoTime();
//
//        long t1 = System.nanoTime();
        LoginUserProjection user = userRepository.findLoginUserByEmail(dto.getEmail())
                .orElseThrow(() -> {
                    log.warn("[INVALID_LOGIN_CREDENTIALS] email={}", dto.getEmail());
                    return new UnauthorizedAccessException("INVALID_LOGIN_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다.");
                });
//        long findUserMs = (System.nanoTime() - t1) / 1_000_000;

        if (user.getDelYn() == DelYN.Y) {
            log.warn("[DELETED_ACCOUNT] email={}", dto.getEmail());
            throw new UnauthorizedAccessException("INVALID_LOGIN_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            String until = user.getSuspendedUntil()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            log.warn("[SUSPENDED_ACCOUNT] email={} until={}", dto.getEmail(), until);
            throw new AccountSuspendedException("정지된 계정입니다.", until);
        }

//        long t2 = System.nanoTime();
        boolean passwordMatched = passwordEncoder.matches(dto.getPassword(), user.getPassword());
//        long passwordMatchMs = (System.nanoTime() - t2) / 1_000_000;

        if (!passwordMatched) {
            log.warn("[INVALID_LOGIN_CREDENTIALS] email={}", dto.getEmail());
            throw new UnauthorizedAccessException("INVALID_LOGIN_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

//        long t3 = System.nanoTime();
        String token = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getAuthority()
        );
//        long tokenCreateMs = (System.nanoTime() - t3) / 1_000_000;

//        long t4 = System.nanoTime();
        long ttl = jwtTokenProvider.getRemainingSeconds(token);
        customTokenExpiredStrategy.save(user.getEmail(), token, ttl);
//        long redisSaveMs = (System.nanoTime() - t4) / 1_000_000;

//        long t5 = System.nanoTime();
        userStatusService.touch(user.getEmail());
//        long touchMs = (System.nanoTime() - t5) / 1_000_000;
//
//        long totalMs = (System.nanoTime() - totalStart) / 1_000_000;
//
//        if (totalMs >= 300) {
//            log.warn(
//                    "[LOGIN_TIMING] email={}, total={}ms, findUser={}ms, passwordMatch={}ms, tokenCreate={}ms, redisSave={}ms, touch={}ms",
//                    dto.getEmail(),
//                    totalMs,
//                    findUserMs,
//                    passwordMatchMs,
//                    tokenCreateMs,
//                    redisSaveMs,
//                    touchMs
//            );
//        } else {
//            log.info(
//                    "[LOGIN_TIMING] email={}, total={}ms, findUser={}ms, passwordMatch={}ms, tokenCreate={}ms, redisSave={}ms, touch={}ms",
//                    dto.getEmail(),
//                    totalMs,
//                    findUserMs,
//                    passwordMatchMs,
//                    tokenCreateMs,
//                    redisSaveMs,
//                    touchMs
//            );
//        }

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
    public void updateUser(UserUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email:" + email));

        if (Boolean.TRUE.equals(user.getViewOnly())) {
            throw new UnauthorizedAccessException("USER_TEMPORARY_RESTRICTED", "임시 제한 상태라 닉네임을 변경할 수 없습니다.");
        }

        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            String until = user.getSuspendedUntil()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            throw new AccountSuspendedException("정지된 계정입니다." , until);
        }

        validationService.validateUpdateProfile(dto);
        user.update(dto);
        userRepository.save(user);
    }

    /* 회원 탈퇴 */
    @Transactional
    public void delete(UserDeleteDto deleteDto, String deletedBy) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        if (deleteDto == null || deleteDto.getUserId() == null) {
            log.warn("[USER_ID_REQUIRED] deleteDto or userId null. email={}", email);
            throw new BadRequestException("USER_ID_REQUIRED", "탈퇴할 사용자 ID가 필요합니다.");
        }

        User me = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[INVALID_USER] delete requester not found. email={}", email);
                    return new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다.");
                });

        User target = userRepository.findById(deleteDto.getUserId())
                .filter(user -> user.getDelYn() == DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[USER_NOT_FOUND] delete target not found. targetUserId={}", deleteDto.getUserId());
                    return new ResourceNotFoundException("USER_NOT_FOUND", "삭제하려는 유저가 존재하지 않습니다.");
                });

        // 본인 탈퇴 허용
        if (!me.getUserId().equals(target.getUserId())) {
            // 본인이 ADMIN이 아니면 남 삭제 불가
            if (me.getAuthority() != Authority.ADMIN) {
                log.warn("[USER_DELETE_FORBIDDEN] meUserId={}, targetUserId={}", me.getUserId(), target.getUserId());
                throw new UnauthorizedAccessException("USER_DELETE_FORBIDDEN", "본인 또는 관리자만 탈퇴할 수 있습니다.");
            }

            // 타겟이 ADMIN이면 상위 ADMIN만 삭제 가능 (userId 작은 쪽이 상위)
            if (target.getAuthority() == Authority.ADMIN) {
                if (me.getUserId() >= target.getUserId()) {
                    log.warn("[ADMIN_DELETE_FORBIDDEN] meUserId={}, targetAdminId={}", me.getUserId(), target.getUserId());
                    throw new UnauthorizedAccessException("ADMIN_DELETE_FORBIDDEN", "상위 ADMIN만 하위 ADMIN 계정을 삭제할 수 있습니다.");
                }
            }
        }

        try {
            target.softDelete();
            userRepository.save(target);
        } catch (Exception e) {
            log.error("[USER_DELETE_FAILED] soft delete fail. meUserId={}, targetUserId={}",
                    me.getUserId(), target.getUserId(), e);
            throw new InternalErrorException("USER_DELETE_FAILED", "탈퇴 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }


    /* 회원 상세 조회 (마이페이지용) */
    @Transactional(readOnly = true)
    public UserDetailDto getMyDetail() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email:" + email));


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

        Authority auth = user.getAuthority();
        if (auth != Authority.ADMIN && auth != Authority.INQUIRY) {
            throw new AccessDeniedException("ADMIN 또는 INQUIRY만 접근 가능합니다.");
        }
    }

    /* 닉네임 생성 및 업데이트 */
    @Transactional
    public void updateNickname(UserNicknameUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email:" + email));

        if (Boolean.TRUE.equals(user.getViewOnly())) {
            throw new UnauthorizedAccessException("USER_TEMPORARY_RESTRICTED", "임시 제한 상태라 닉네임을 변경할 수 없습니다.");
        }
        if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
            String until = user.getSuspendedUntil()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            throw new AccountSuspendedException("정지된 계정입니다." , until);
        }

        String newNickname = dto.getNickname();
        if (newNickname == null || newNickname.trim().isEmpty()) {
            throw new BadRequestException("BLANK_NICKNAME", "닉네임을 입력해 주세요.");
        }

        newNickname = newNickname.trim();

        // 닉네임 2~8 , 제한문자 추가
        if (newNickname.length() < 2 || newNickname.length() > 8) {
            throw new BadRequestException("INVALID_NICKNAME_LENGTH", "닉네임은 2~8자로 입력해 주세요.");
        }
        if (!newNickname.matches("^[A-Za-z0-9가-힣_]+$")) {
            throw new BadRequestException("INVALID_NICKNAME_FORMAT", "닉네임은 영문,숫자,한글,_ 만 사용가능합니다.");
        }

        if (newNickname.equals(user.getNickname())) {
            return;
        }

        //
        if (user.getLastNicknameChangedAt() != null) {
            long days = ChronoUnit.DAYS.between(user.getLastNicknameChangedAt(), LocalDateTime.now());
            if (days < 7) {
                long remain = 7 - days;
                throw new UnauthorizedAccessException("NICKNAME_CHANGE_COOLDOWN", "닉네임은 " + remain + "일 이후 변경 가능합니다.");
            }
        }

        // 중복 체크
        boolean exists = userRepository.existsByNicknameAndDelYn(newNickname, DelYN.N);
        if (exists) {
            throw new BadRequestException("ALREADY_USED_NICKNAME", "이미 사용 중인 닉네임입니다.");
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

        String newToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getAuthority()
        );

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
               .orElseThrow(() -> new InternalErrorException("DATA_NOT_FOUND" ,"판매자를 찾을 수 없습니다."));
       log.warn("[DATA_NOT_FOUND] productId={}", productId);
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

    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfile(Long userId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User requester = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("요청자 정보를 찾을 수 없습니다."));

        User target = userRepository.findById(userId)
                .filter(u -> u.getDelYn() == DelYN.N)
                .orElseThrow(() -> new RuntimeException("조회 대상 유저가 존재하지 않습니다."));

        boolean reportable = !Objects.equals(requester.getUserId(), target.getUserId());

        long totalProducts = productRepository.countByUser_UserIdAndDelYnAndBlockedFalse(
                target.getUserId(), DelYN.N
        );

        long soldCount = productRepository.countByUser_UserIdAndStatusAndDelYnAndBlockedFalse(
                target.getUserId(), Status.SELLED, DelYN.N
        );

        long sellingCount = productRepository.countByUser_UserIdAndStatusInAndDelYnAndBlockedFalse(
                target.getUserId(), List.of(Status.READY, Status.PROCESSING), DelYN.N
        );

        long endedCount = productRepository.countByUser_UserIdAndStatusInAndDelYnAndBlockedFalse(
                target.getUserId(), List.of(Status.SELLED, Status.NOTSELLED), DelYN.N
        );

        return PublicProfileDto.builder()
                .userId(target.getUserId())
                .nickname(target.getNickname())
                .profileImageUrl(target.getProfileImageUrl())
                .createdAt(target.getCreatedAt())
                .tradeCount(soldCount)
                .totalProducts(totalProducts)
                .sellingProducts(sellingCount)
                .endedProducts(endedCount)
                .reportable(reportable)
                .build();
    }
}

