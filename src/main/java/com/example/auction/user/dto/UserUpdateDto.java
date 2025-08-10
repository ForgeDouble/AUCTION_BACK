package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateDto {
    private String name;
    private String address;
    private String phone;
}
