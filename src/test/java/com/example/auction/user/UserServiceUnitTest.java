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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    UserRepository userRepository;

    @Mock
    ValidationService validationService;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    void REGISTER_SUCCESS() {
        UserRegisterDto dto = new UserRegisterDto(
                "test@email.com",
                "password123",
                "홍길동",
                Gender.M,
                "2000-08-01",
                "01011112222",
                "경기도",
                "테스트"
        );

        given(userRepository.findByEmail("test@email.com"))
                .willReturn(Optional.empty());
        given(passwordEncoder.encode("password123"))
                .willReturn("encodedPw");
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        User result = userService.register(dto);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@email.com");
        assertThat(result.getPassword()).isEqualTo("encodedPw");
        assertThat(result.getName()).isEqualTo("홍길동");

        verify(userRepository, times(1)).findByEmail("test@email.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void FAIL_REGISTER_DUPLICATE_EMAIL() {
        UserRegisterDto dto = new UserRegisterDto(
                "test@email.com",
                "password123",
                "홍길동",
                Gender.M,
                "2000-08-01",
                "01011112222",
                "경기도",
                "테스트"
        );

        given(userRepository.findByEmail("test@email.com"))
                .willReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 존재하는 이메일입니다.");

        verify(userRepository, times(1)).findByEmail("test@email.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

}