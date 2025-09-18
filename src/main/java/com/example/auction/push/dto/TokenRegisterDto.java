package com.example.auction.push.dto;

import com.example.auction.push.domain.DevicePlatform;
import com.example.auction.push.domain.DeviceToken;
import com.example.auction.report.domain.Report;
import com.example.auction.user.domain.User;
import lombok.Data;

@Data
public class TokenRegisterDto {
    private String token;
    private DevicePlatform platform;
    private String appVersion;
    private String deviceModel;

}
