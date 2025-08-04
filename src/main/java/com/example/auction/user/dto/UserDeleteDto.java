package com.example.auction.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDeleteDto {
    private Long id;

    private String email;
    private String name;
    private String reason;
}

