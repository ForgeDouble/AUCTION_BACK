package com.example.auction.chat.domain;

public enum ChatRoomType {
    NORMAL, // 일반 1:1
    INQUIRY, // 문의 담당자 / 관리자 간 채팅
    ADMIN_GROUP, // 관리자 단체방 (라운지) - 관리자 통합
    STAFF_GROUP // 운영진 그룹방
}
