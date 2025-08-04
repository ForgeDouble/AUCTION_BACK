package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisterDto {
    private String email;
    private String password;
    private String name;
    private Gender gender;
    private String birthday;
    private String phone;

    public User toEntity() {
        return User.builder()
                .email(email)
                .password(password)
                .name(name)
                .gender(gender)
                .birthday(birthday)
                .phone(phone)
                .authority(Authority.USER)
                .warning(0L)
                .build();
    }
}
