package com.example.auction.user.domain;


// 정책
// 접속중 , 자리비움 , 비 로그인 상태
// 접속 -> 로그인 시 바로 적용
// 자리비움 -> 로그인 돼있음 but 5분 이상 활동 없음
// 그 이외 -> 비 로그인(토큰 / 로그인 기록 x)
public enum UserStatus {
    ONLINE, // 접속중
    IDLE, // 자리비움
    OFFLINE // 비로그인
}
