package com.example.auction.chat.service;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class InquiryResolver {

    private final UserRepository userRepository;

    // 원하는경우 yml 을 통한 지정 가능
    @Value("${support.inquirer.email:}")
    private String inquirerEmail;

    public User resolve() {
        if (StringUtils.hasText(inquirerEmail)) {
            return userRepository.findByEmail(inquirerEmail)
                    .orElseThrow(() -> new IllegalStateException("지정된 문의 담당자 이메일을 찾을 수 없습니다: " + inquirerEmail));
        }
        // 지정이 x 인 경우 authority 중 뒤에 생성된 사람을 사용
        return userRepository.findFirstByAuthorityOrderByCreatedAtDesc(Authority.INQUIRY)
                .orElseThrow(() -> new IllegalStateException("권한 INQUIRE 담당자가 존재하지 않습니다."));
    }
}
