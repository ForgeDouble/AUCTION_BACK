package com.example.auction.user.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.user.domain.UserStatus;
import com.example.auction.user.dto.*;
import com.example.auction.user.service.UserImageService;
import com.example.auction.user.service.UserService;
import com.example.auction.user.service.UserStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final UserImageService userImageService;
    private final UserStatusService userStatusService;

    public UserController(UserService userService, UserImageService userImageService, UserStatusService userStatusService) {
        this.userService = userService;
        this.userImageService = userImageService;
        this.userStatusService = userStatusService;
    }

    /* 로그인 */
    @PostMapping("/login")
    public ResponseEntity<CommonResDto> login(@RequestBody UserLoginDto loginDto) {
        String token = userService.login(loginDto);
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "로그인 성공", result));
    }

    /* 로그아웃 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        userService.logout();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "로그아웃 성공", null));
    }

    /* 로그인 연장 - 새 토큰 덮어쓰기 */
    @PostMapping("/extend")
    public ResponseEntity<?> extend() {
        var res = userService.extendLogin();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "로그인 연장 성공", res));
    }

    /* 회원가입 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@ModelAttribute UserRegisterDto registerDto) {
        userService.register(registerDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "회원가입 성공", null));
    }
    /* [관리자 기능] 새로운 ADMIN 계정 생성 */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/create")
    public ResponseEntity<?> createAdmin(@RequestBody AdminUserRegisterDto dto) {
        userService.createAdminUser(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "ADMIN 계정 생성 성공", null));
    }

    /* [관리자 기능] 새로운 INQUIRY(문의 담당자) 계정 생성 */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/inquiry/create")
    public ResponseEntity<?> createInquiry(@RequestBody AdminUserRegisterDto dto) {
        userService.createInquiryUser(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "INQUIRY 계정 생성 성공", null));
    }


    /* 닉네임 변경 */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/nickname")
    public ResponseEntity<?> updateNickname(@RequestBody UserNicknameUpdateDto dto) {
        userService.updateNickname(dto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "닉네임 변경 완료", null));
    }

    /* 마이페이지 조회 */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/detail")
    public ResponseEntity<?> myDetail() {
        UserDetailDto detail = userService.getMyDetail();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "조회 성공", detail));
    }


    /* [관리자 + 유저 기능] 타겟팅 조회 */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/view/{userId}")
    public ResponseEntity<CommonResDto> viewUser(@PathVariable Long userId) {
        Object view = userService.getUserViewByTargetId(userId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "유저 조회 성공", view));
    }

    /* [관리자 기능]회원 목록 조회 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    public ResponseEntity<?> getAllUsers() {
        List<UserDto> users = userService.getAllUsers();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "조회 성공", users));
    }

    /* 회원정보 수정 */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/update")
    public ResponseEntity<?> update(@ModelAttribute UserUpdateDto updateDto) {
        userService.update(updateDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "회원정보 수정 완료", null));
    }

    /* 회원 탈퇴 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody UserDeleteDto deleteDto) {
        userService.delete(deleteDto, "SELF");
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "회원 탈퇴 성공", null));
    }

    /* 이미지 저장 */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResDto> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String url = userImageService.uploadOrReplace(file);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "프로필 이미지 저장", Map.of("url", url)));
    }

    /* 이미지 삭제 */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/image/delete")
    public ResponseEntity<CommonResDto> delete() {
        userImageService.deleteAvatar();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "프로필 이미지 삭제", null));
    }

    /* 접속중인 유저 확인 */
    /* 근데 이 코드라면 jwt -> bearer 헤더로 전달하고 자기만 판단하는 코드같은디;?*/
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/verify-token")
    public ResponseEntity<?> verifyToken() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "계정 조회 성공", email));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/status/me")
    public ResponseEntity<?> myStatus() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserStatus status = userStatusService.getStatus(email);
        UserStatusDto dto = new UserStatusDto(email, status);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상태 조회 성공", dto));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/status/{userId}")
    public ResponseEntity<?> userStatus(@PathVariable Long userId) {
        UserStatus status = userService.getStatusByUserId(userId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상태 조회 성공", status));
    }

    /* 일일 접속 현황 통계 제공 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status/daily")
    public ResponseEntity<?> dailyStatus(@RequestParam(required = false) String date) {
        DailyActiveUserStatsDto dto = userStatusService.getDailyActiveUserStats(date);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "일일 접속 현황", dto));
    }

    /* 판매자의 정보 읽기 */
    @GetMapping("/seller/{productId}")
    public ResponseEntity<?> getSellerByProductId(@PathVariable Long productId) {
        SellerDto sellerDto = userService.getSellerInfoByProductId(productId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "유저정보 조회 성공", sellerDto));
    }
}
