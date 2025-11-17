package com.example.auction.chat.controller;

import com.example.auction.chat.dto.ChatRoomOpenRequest;
import com.example.auction.chat.dto.ChatRoomResponse;
import com.example.auction.chat.service.ChatRoomService;
import com.example.auction.common.dto.CommonResDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat/room")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    public ChatRoomController(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    // 방 열기(1:1 고정): userId, targetId 필요
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/open")
    public ResponseEntity<CommonResDto> open(@RequestBody ChatRoomOpenRequest req) {
        var room = chatRoomService.openRoom(req);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "채팅방 생성/조회 성공", room.getId()));
    }

    // 내 방 목록: userId 기준(프론트에서 로그인 유저의 식별자 전달)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<CommonResDto> myRooms(@RequestParam String userId) {
        List<ChatRoomResponse> list = chatRoomService.listMyRooms(userId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "내 채팅방 목록", list));
    }

    // 방 입장: 읽지 않음 → 0, 알림 재계산
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/enter")
    public ResponseEntity<CommonResDto> enter(@RequestParam String userId, @RequestParam String roomId) {
        chatRoomService.enter(userId, roomId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "입장 처리", null));
    }

    // 방 퇴장
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/exit")
    public ResponseEntity<CommonResDto> exit(@RequestParam String userId) {
        chatRoomService.exit(userId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "퇴장 처리", null));
    }
}
