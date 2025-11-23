package com.example.auction.chat.controller;

import com.example.auction.chat.dto.ChatRoomOpenRequest;
import com.example.auction.chat.dto.ChatRoomResponse;
import com.example.auction.chat.service.ChatRoomService;
import com.example.auction.common.dto.CommonResDto;
import com.example.auction.user.domain.Authority;
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
    public ResponseEntity<CommonResDto> open(@RequestBody ChatRoomOpenRequest chatRoomOpenRequest) {
        var room = chatRoomService.openRoom(chatRoomOpenRequest);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "채팅방 생성/조회 성공", room.getId()));
    }

    // 내 방 목록
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<CommonResDto> myRooms() {
        List<ChatRoomResponse> list = chatRoomService.listMyRooms();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "내 채팅방 목록", list));
    }

    // 방 입장: 읽지 않음 → 0, 알림 재계산
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/enter")
    public ResponseEntity<CommonResDto> enter(@RequestParam String roomId) {
        chatRoomService.enter(roomId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "입장 처리", null));
    }

    // 방 퇴장
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/exit")
    public ResponseEntity<CommonResDto> exit() {
        chatRoomService.exit();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "퇴장 처리", null));
    }

    // 문의하기 기능 구현
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/inquire")
    public ResponseEntity<CommonResDto> inquire(@RequestBody ChatRoomOpenRequest chatRoomOpenRequest) {
        var room = chatRoomService.openInquiryRoom(chatRoomOpenRequest);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "문의방 생성/조회 성공", room.getId()));
    }

    // INQUIRY 초대
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @PostMapping("/{roomId}/invite-inquiry")
    public ResponseEntity<CommonResDto> inviteInquiry(@PathVariable String roomId,
                                                      @RequestParam String targetInquiryEmail) {
        chatRoomService.inviteInquiry(roomId, targetInquiryEmail);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "문의 담당자 초대 완료", null));
    }

    // INQUIRY 리스트
    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @GetMapping("/members/inquiry")
    public ResponseEntity<CommonResDto> listInquiryMembers() {
        var list = chatRoomService.listMembersByAuthority(Authority.INQUIRY);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "INQUIRY 담당자 목록", list));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INQUIRY')")
    @GetMapping("/members/admin")
    public ResponseEntity<CommonResDto> listAdminMembers() {
        var list = chatRoomService.listMembersByAuthority(Authority.ADMIN);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "ADMIN 목록", list));
    }
}
