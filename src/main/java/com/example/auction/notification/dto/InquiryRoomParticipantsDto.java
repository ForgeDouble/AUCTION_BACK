package com.example.auction.notification.dto;

import com.example.auction.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class InquiryRoomParticipantsDto {

    private final List<User> customers;
    private final List<User> handlers;


}
