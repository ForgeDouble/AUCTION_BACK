//package com.example.auction.push.dto;
//
//
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.util.Map;
//
//@Data
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//public class FcmMessageDto {
//    private String token;                 // 단건 대상
//    private Map<String, String> data;     // 클릭 라우팅 등
//
//    private Notification notification;    // 알림 제목/본문/이미지
//    private Android android;              // 안드 옵션
//    private Apns apns;                    // iOS 옵션
//
//    @Data
//    @Builder
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class Notification {
//        private String title;
//        private String body;
//        private String image; // 선택
//    }
//
//    @Data
//    @Builder
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class Android {
//        /** HIGH | NORMAL (기본 HIGH) */
//        private String priority;
//        /** TTL(초) – 기본 3600 */
//        private Long ttlSeconds;
//        /** collapseKey, channelId 등 필요시 확장 */
//        private String collapseKey;
//    }
//
//    @Data
//    @Builder
//    @NoArgsConstructor
//    @AllArgsConstructor
//    public static class Apns {
//        /** high|normal → 10|5 (기본 high) */
//        private String priority;
//        /** 백그라운드 갱신 필요 시 true */
//        private Boolean contentAvailable;
//        /** 시스템 사운드명(ex: "default") */
//        private String sound;
//        private Integer badge;
//        private String category;
//        private String threadId;
//    }
//}
