package com.example.auction.common.data;

import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.Gender;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 1) 관리자 유저
        createUserIfNotExists(
                "admin@auction.test",
                "관리자",
                "9999",
                Gender.M,
                "1990.01.01",
                "01012345678",
                "서울시 중구 어딘가",
                "주딱",
                Authority.ADMIN
        );

        // 2) 일반 유저
        createUserIfNotExists(
                "user1@auction.test",
                "홍길동",
                "1234",
                Gender.W,
                "1995.05.05",
                "01098765432",
                "서울시 강남구 어딘가",
                "유저임",
                Authority.USER
        );
    }

    private void createUserIfNotExists(
            String email,
            String name,
            String rawPassword,
            Gender gender,
            String birthday,
            String phone,
            String address,
            String nickname,
            Authority authority
    ) {
        userRepository.findByEmail(email).ifPresentOrElse(
                u -> {
                },
                () -> {
                    User user = User.builder()
                            .email(email)
                            .name(name)
                            .password(passwordEncoder.encode(rawPassword))
                            .gender(gender)
                            .birthday(birthday)
                            .phone(phone)
                            .address(address)
                            .nickname(nickname)
                            .authority(authority)
                            .build();

                    userRepository.save(user);
                }
        );
    }
}