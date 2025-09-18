package com.example.auction.push.dto;

import com.example.auction.push.domain.PushEventType;
import lombok.Data;

@Data
public class PushAckDto {

    // UUID 값
    private String pushId;
    private PushEventType event;
}
