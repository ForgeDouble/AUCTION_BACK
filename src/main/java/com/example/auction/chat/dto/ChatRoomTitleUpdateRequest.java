package com.example.auction.chat.dto;

import lombok.Getter;
import lombok.Setter;

// 방 이름 업데이트를 위한 dto 추가
@Getter
@Setter
public class ChatRoomTitleUpdateRequest {
    private String title;
}
