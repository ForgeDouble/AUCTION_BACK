package com.example.auction.user.dto;

import com.example.auction.user.domain.User;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long Id;
    private String name;
    private String email;

    public static UserDto fromEntity(User user) {
        return UserDto.builder()
                .Id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}
