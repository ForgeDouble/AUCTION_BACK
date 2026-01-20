package com.example.auction.chat.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRoomInviteRequest {
    private String targetEmail;
}