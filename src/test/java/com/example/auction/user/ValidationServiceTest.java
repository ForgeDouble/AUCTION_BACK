package com.example.auction.user;

import com.example.auction.common.exception.BadRequestException;
import com.example.auction.user.domain.Gender;
import com.example.auction.user.dto.UserRegisterDto;
import com.example.auction.user.dto.UserUpdateDto;
import com.example.auction.user.service.ValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/*
    회원가입 필요 정책 준수 데이터 만 DB 정합성 판정
    - EMAIL 형식 여부 확인
    - 생년월일 검증( 실존형식, 미래 일 적용 시 , 나이제한(14 ~ 120 ) 판정
    - 전화번호 형식 검증
    - 비밀 번호 정책 ( 영+숫자, 길이조건 위반 ) 조건
    - 성별 누락 조건
    - 닉네임 / 주소 검증 ( 최소단위 검증 )
 */
class ValidationServiceTest {

    private final ValidationService validationService = new ValidationService();

    private UserRegisterDto validRegisterDto() {
        return UserRegisterDto.builder()
                .email("user@test.com")
                .password("Password1!")
                .name("Hong Gil-dong")
                .gender(Gender.M)
                .birthday("2000.01.01")
                .phone("01012345678")
                .address("Seoul Gangnam-gu")
                .nickname("Gildong")
                .build();
    }

    private UserUpdateDto validUpdateDto() {
        return UserUpdateDto.builder()
                .phone("01012345678")
                .address("Seoul Seocho-gu")
                .build();
    }

    @Nested
    @DisplayName("validateRegister")
    class ValidateRegisterTest {

        @Test
        @DisplayName("PASS - valid registration payload")
        void validateRegister_success() {
            assertThatCode(() -> validationService.validateRegister(validRegisterDto()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PASS - nickname can be null")
        void validateRegister_success_nullNickname() {
            UserRegisterDto dto = validRegisterDto();
            dto.setNickname(null);

            assertThatCode(() -> validationService.validateRegister(dto))
                    .doesNotThrowAnyException();
        }

//        @Test
//        @DisplayName("PASS - address can be blank")
//        void validateRegister_success_blankAddress() {
//            UserRegisterDto dto = validRegisterDto();
//            dto.setAddress("   ");
//
//            assertThatCode(() -> validationService.validateRegister(dto))
//                    .doesNotThrowAnyException();
//        }

        @Test
        @DisplayName("FAIL - invalid email format")
        void validateRegister_fail_invalidEmail() {
            UserRegisterDto dto = validRegisterDto();
            dto.setEmail("invalid-email");

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("유효한 이메일 주소가 아닙니다");
        }

        @Test
        @DisplayName("FAIL - name shorter than 2 chars")
        void validateRegister_fail_shortName() {
            UserRegisterDto dto = validRegisterDto();
            dto.setName("A");

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - invalid nickname format")
        void validateRegister_fail_invalidNickname() {
            UserRegisterDto dto = validRegisterDto();
            dto.setNickname("nick!");

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

//        @Test
//        @DisplayName("FAIL - invalid birthday format")
//        void validateRegister_fail_invalidBirthdayFormat() {
//            UserRegisterDto dto = validRegisterDto();
//            dto.setBirthday("2000-01-01");
//
//            assertThatThrownBy(() -> validationService.validateRegister(dto))
//                    .isInstanceOf(BadRequestException.class);
//        }

        @Test
        @DisplayName("FAIL - non-existent date (2023.02.30)")
        void validateRegister_fail_nonexistentBirthday() {
            UserRegisterDto dto = validRegisterDto();
            dto.setBirthday("2023.02.30");

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - future birthday")
        void validateRegister_fail_futureBirthday() {
            String future = LocalDate.now()
                    .plusDays(1)
                    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

            UserRegisterDto dto = validRegisterDto();
            dto.setBirthday(future);

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - under 14 years old")
        void validateRegister_fail_under14() {
            String under14 = LocalDate.now()
                    .minusYears(13)
                    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

            UserRegisterDto dto = validRegisterDto();
            dto.setBirthday(under14);

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - older than 120 years")
        void validateRegister_fail_tooOld() {
            String tooOld = LocalDate.now()
                    .minusYears(121)
                    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

            UserRegisterDto dto = validRegisterDto();
            dto.setBirthday(tooOld);

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - invalid phone number")
        void validateRegister_fail_invalidPhone() {
            UserRegisterDto dto = validRegisterDto();
            dto.setPhone("12345");

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - address too short")
        void validateRegister_fail_shortAddress() {
            UserRegisterDto dto = validRegisterDto();
            dto.setAddress("A");

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - invalid password format")
        void validateRegister_fail_invalidPassword() {
            UserRegisterDto dto = validRegisterDto();
            dto.setPassword("abcdefgh");

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - gender is null")
        void validateRegister_fail_nullGender() {
            UserRegisterDto dto = validRegisterDto();
            dto.setGender(null);

            assertThatThrownBy(() -> validationService.validateRegister(dto))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("validateUpdateProfile")
    class ValidateUpdateProfileTest {

        @Test
        @DisplayName("PASS - valid update payload")
        void validateUpdateProfile_success() {
            assertThatCode(() -> validationService.validateUpdateProfile(validUpdateDto()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PASS - address can be blank")
        void validateUpdateProfile_success_blankAddress() {
            UserUpdateDto dto = validUpdateDto();
            dto.setAddress("   ");

            assertThatCode(() -> validationService.validateUpdateProfile(dto))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("FAIL - phone is null")
        void validateUpdateProfile_fail_nullPhone() {
            UserUpdateDto dto = validUpdateDto();
            dto.setPhone(null);

            assertThatThrownBy(() -> validationService.validateUpdateProfile(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - invalid phone format")
        void validateUpdateProfile_fail_invalidPhone() {
            UserUpdateDto dto = validUpdateDto();
            dto.setPhone("abc-defg");

            assertThatThrownBy(() -> validationService.validateUpdateProfile(dto))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("FAIL - address too short")
        void validateUpdateProfile_fail_shortAddress() {
            UserUpdateDto dto = validUpdateDto();
            dto.setAddress("A");

            assertThatThrownBy(() -> validationService.validateUpdateProfile(dto))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}