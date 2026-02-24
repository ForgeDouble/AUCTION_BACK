package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserUpdateDto {
    private String nickname;
    private String phone;
    private String address;
}
