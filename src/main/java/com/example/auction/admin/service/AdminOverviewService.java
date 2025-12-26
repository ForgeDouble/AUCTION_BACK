package com.example.auction.admin.service;

import com.example.auction.admin.dto.AdminOverviewResponse;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.user.service.UserStatusService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminOverviewService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final BidRepository bidRepository;

    private final UserStatusService userStatusService;

//    private final AdminReportCounter adminReportCounter;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public AdminOverviewService(UserRepository userRepository, ProductRepository productRepository, BidRepository bidRepository, UserStatusService userStatusService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.bidRepository = bidRepository;
        this.userStatusService = userStatusService;
    }


}
