package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateDto {
    private String password;
    private String name;
    private Gender gender;
    private String address;
    private String birthday;
    private String phone;
}
