package com.example.auction.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveHourBucketDto {
    private String hour;
    private long count;
}
