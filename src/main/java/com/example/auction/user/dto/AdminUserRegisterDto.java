package com.example.auction.user.dto;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.User;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserRegisterDto {
    private String email;
    private String password;
    private String name;
    private Gender gender;
    private String birthday;
    private String phone;
    private String address;
    private String nickname;

    public User toEntity(Authority authority) {
        return User.builder()
                .email(email)
                .password(password)
                .name(name)
                .gender(gender)
                .birthday(birthday)
                .phone(phone)
                .address(address)
                .authority(authority)
                .warning(0L)
                .nickname(nickname)
                .build();
    }
}


