package com.example.auction.chat.controller;

import com.example.auction.chat.dto.ChatMessageRequest;
import com.example.auction.chat.dto.ChatMessageResponse;
import com.example.auction.chat.service.ChatMessageService;
import com.example.auction.common.dto.CommonResDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/chat/message")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    // 메시지 전송(REST 경유)
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/send")
    public ResponseEntity<CommonResDto> send(@RequestBody ChatMessageRequest chatMessageRequest) {
        chatMessageService.send(chatMessageRequest);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "메시지 전송", null));
    }

    // 최근 N개 가져오기(무한 스크롤 첫 페이지 용)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/recent")
    public ResponseEntity<CommonResDto> recent(@RequestParam String roomId, @RequestParam(defaultValue = "30") int size) {
        List<ChatMessageResponse> list = chatMessageService.getRecent(roomId, size);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "최근 메시지", list));
    }

}
