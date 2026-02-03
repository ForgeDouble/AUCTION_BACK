package com.example.auction.user.service;

import com.example.auction.common.exception.InternalErrorException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendPasswordResetEmail(String to, String token) {
        try {
            log.info("[EMAIL SERVICE] 객체 확인 fromEmail={} toEmail={}", fromEmail, to);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("비밀번호 재설정");

            String resetLink = frontendUrl + "/reset_password?token=" + token;

            String htmlContent = String.format("""
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #333;">비밀번호 재설정</h2>
                    <p>안녕하세요,</p>
                    <p>아래 버튼을 클릭하여 비밀번호를 재설정하세요.</p>
                    <div style="margin: 30px 0;">
                        <a href="%s" 
                           style="background-color: #765AFF; 
                                  color: white; 
                                  padding: 12px 24px; 
                                  text-decoration: none; 
                                  border-radius: 5px;
                                  display: inline-block;">
                            비밀번호 재설정하기
                        </a>
                    </div>
                    <p style="color: #666; font-size: 14px;">
                        이 링크는 15분간 유효합니다.
                    </p>
                    <p style="color: #666; font-size: 14px;">
                        비밀번호 재설정을 요청하지 않으셨다면 이 메일을 무시하세요.
                    </p>
                    <hr style="margin: 30px 0; border: none; border-top: 1px solid #eee;">
                    <p style="color: #999; font-size: 12px;">
                        링크가 작동하지 않으면 아래 URL을 복사하여 브라우저에 붙여넣으세요:<br>
                        %s
                    </p>
                </div>
                """, resetLink, resetLink);

            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new InternalErrorException("FAILED_TO_SEND_EMAIL", "이메일 전송 실패", e);
        }
    }
}
