package com.example.auction.chat.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ChatRoomCreateGroupRequest {
    private String title;
    private List<String> participantEmails = new ArrayList<>();
    private boolean staffOnly = true;
}
