package com.example.auction.user.service;

import com.example.auction.common.exception.BadRequestException;
import com.example.auction.user.dto.UserRegisterDto;
import com.example.auction.user.dto.UserUpdateDto;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

    public void validateRegister(UserRegisterDto dto) {
        validateEmail(dto.getEmail());
        validateName(dto.getName());
        validateNickname(dto.getNickname());
        validateBirthday(dto.getBirthday());
        validatePhone(dto.getPhone());
        validateAddress(dto.getAddress());
        validatePassword(dto.getPassword());
        validateGender(dto.getGender());
    }

    public void validateUpdateProfile(UserUpdateDto dto) {
        validateNickname(dto.getNickname());
        validatePhone(dto.getPhone());
        validateAddress(dto.getAddress());
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank())
            throw new BadRequestException("INVALID_EMAIL_FORMAT", "이메일을 입력하세요.");
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new BadRequestException("INVALID_EMAIL_FORMAT", "유효한 이메일 주소가 아닙니다.");
    }

    private void validateName(String name) {
        if (name == null || name.isBlank())
            throw new BadRequestException("INVALID_NAME_FORMAT", "이름을 입력하세요.");
        if (name.trim().length() < 2)
            throw new BadRequestException("INVALID_NAME_FORMAT", "이름은 2자 이상이어야 합니다.");
    }

    private void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return;
        if (!nickname.matches("^[\\w가-힣]{2,8}$"))
            throw new BadRequestException("INVALID_NICKNAME_FORMAT", "닉네임은 2~8자 (한글/영문/숫자)만 가능합니다.");
    }

    private void validateBirthday(String birthday) {
        if (birthday == null || birthday.isBlank())
            throw new BadRequestException("INVALID_BIRTHDAY_FORMAT", "생년월일을 입력하세요.");
        if (!birthday.matches("^\\d{4}\\.\\d{2}\\.\\d{2}$"))
            throw new BadRequestException("INVALID_BIRTHDAY_FORMAT", "생년월일 형식이 올바르지 않습니다. (yyyy.MM.dd)");

        try {
            String[] parts = birthday.split("\\.");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int d = Integer.parseInt(parts[2]);

            java.time.LocalDate birth = java.time.LocalDate.of(y, m, d);
            java.time.LocalDate today = java.time.LocalDate.now();

            if (birth.isAfter(today))
                throw new BadRequestException("INVALID_BIRTHDAY_FORMAT", "생년월일은 미래 날짜일 수 없습니다.");

            int age = today.getYear() - birth.getYear();
            if (today.isBefore(birth.withYear(today.getYear()))) age--;
            if (age < 14) throw new BadRequestException("INVALID_BIRTHDAY_FORMAT", "만 14세 이상만 가입할 수 있습니다.");
            if (age > 120) throw new BadRequestException("INVALID_BIRTHDAY_FORMAT", "유효하지 않은 생년월일입니다.");

        } catch (java.time.DateTimeException e) {
            throw new BadRequestException("INVALID_BIRTHDAY_FORMAT", "존재하지 않는 날짜입니다.");
        }
    }

    private void validatePhone(String phone) {
        if (phone == null || phone.isBlank())
            throw new BadRequestException("INVALID_PHONE_FORMAT", "전화번호를 입력하세요.");
        String digits = phone.replaceAll("\\D", "");
        if (!digits.matches("^0\\d{9,10}$"))
            throw new BadRequestException("INVALID_PHONE_FORMAT", "올바른 전화번호 형식이 아닙니다.");
    }

    private void validateAddress(String address) {
        if (address == null || address.isBlank()) return;
        if (address.trim().length() < 2)
            throw new BadRequestException("INVALID_ADDRESS_FORMAT", "주소가 너무 짧습니다.");
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank())
            throw new BadRequestException("INVALID_PASSWORD_FORMAT", "비밀번호를 입력하세요.");
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()\\[\\]{};:,.?/~_+\\-=|]{8,64}$"))
            throw new BadRequestException("INVALID_PASSWORD_FORMAT", "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 합니다.");
    }

    private void validateGender(com.example.auction.user.domain.Gender gender) {
        if (gender == null)
            throw new BadRequestException("INVALID_GENDER_FORMAT", "성별을 선택하세요.");
    }
}
