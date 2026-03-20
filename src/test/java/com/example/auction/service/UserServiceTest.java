package com.example.auction.service;

import com.example.auction.common.exception.BadRequestException;
import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.User;
import com.example.auction.user.dto.UserRegisterDto;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserService;
import com.example.auction.user.service.ValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    ValidationService validationService;   // validationService도 Mock 필요

    @Mock
    PasswordEncoder passwordEncoder;       // passwordEncoder도 Mock 필요

    @InjectMocks
    UserService userService;

    @Test
    void 회원가입_성공() {
        // Arrange
        UserRegisterDto dto = new UserRegisterDto
                ("test@email.com", "password123", "홍길동", Gender.M, "2000-08-01", "01011112222", "경기도", "테스트" );

        given(userRepository.findByEmail("test@email.com"))
                .willReturn(Optional.empty());              // 이메일 중복 없음
        given(passwordEncoder.encode("password123"))
                .willReturn("encodedPw");                  // 암호화된 비번 반환
        given(userRepository.save(any(User.class)))
                .willReturn(new User());                   // 저장 성공

        // Act
        User result = userService.register(dto);

        // Assert
        assertThat(result).isNotNull();
        verify(userRepository, times(1)).save(any(User.class));  // save가 1번 호출됐는지
    }

    @Test
    void 회원가입_실패_이메일중복() {
        // Arrange
        UserRegisterDto dto = new UserRegisterDto
                ("test@email.com", "password123", "홍길동", Gender.M, "2000-08-01", "01011112222", "경기도", "테스트" );


        given(userRepository.findByEmail("test@email.com"))
                .willReturn(Optional.of(new User()));      // 이미 이메일 존재

        // Act & Assert
        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 존재하는 이메일입니다.");

        verify(userRepository, never()).save(any());       // save가 절대 호출 안됐는지
    }
}
