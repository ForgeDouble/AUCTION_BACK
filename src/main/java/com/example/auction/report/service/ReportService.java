package com.example.auction.report.service;

import com.example.auction.report.dto.ReportCreateDto;
import com.example.auction.report.repository.ReportRepository;
import com.example.auction.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;

    public ReportService(UserRepository userRepository, ReportRepository reportRepository) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
    }


}
