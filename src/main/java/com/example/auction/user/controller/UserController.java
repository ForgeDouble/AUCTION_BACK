package com.example.auction.user.controller;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.user.dto.*;
import com.example.auction.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /* 로그인 */
    @PostMapping("/login")
    public ResponseEntity<CommonResDto> login(@RequestBody UserLoginDto loginDto) {
        String token = userService.login(loginDto); // 예외 발생 시 전역 핸들러로 위임
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "로그인 성공", result));
    }

    /* 회원가입 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegisterDto registerDto) {
        userService.register(registerDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "회원가입 성공", null));
    }

    /* 닉네임 생성 */
    /* 닉네임 변경 */
    @PutMapping("/nickname")
    public ResponseEntity<?> updateNickname(@RequestBody UserNicknameUpdateDto dto) {
        try {
            userService.updateNickname(dto);
            return ResponseEntity.ok("닉네임 변경 완료");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }

    /* 회원 상세 조회 */
    @GetMapping("/detail/{userId}")
    public ResponseEntity<?> detail(@PathVariable Long userId) {
        try {
            UserDetailDto detail = userService.getUserDetail(userId);
            return ResponseEntity.ok(detail);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }

    /* 회원 목록 조회 */
    @GetMapping("/list")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<UserDto> users = userService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }

    /* 회원정보 수정 */
    @PutMapping("/update")
    public ResponseEntity<?> update(@RequestBody UserUpdateDto updateDto) {
        try {
            userService.update(updateDto);
            return ResponseEntity.ok("회원정보 수정 완료");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 오류");
        }
    }

    /* 회원 탈퇴 */
    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody UserDeleteDto deleteDto) {
        try {
            userService.delete(deleteDto, "SELF");
            return ResponseEntity.ok("회원 탈퇴 성공");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }
}
