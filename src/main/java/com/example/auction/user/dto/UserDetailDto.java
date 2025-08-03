package com.example.auction.user.dto;

import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.Manager;
import com.example.auction.user.domain.User;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailDto {
    private Long Id;
    private String email;
    private String name;
    private Gender gender;
    private String birthday;
    private String phone;
    private Manager manager;
    private Long warning;

    public static UserDetailDto fromEntity(User user) {
        return UserDetailDto.builder()
                .Id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .phone(user.getPhone())
                .manager(user.getManager())
                .warning(user.getWarning())
                .build();
    }
}