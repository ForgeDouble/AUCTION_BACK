/* 로그인 테스트를 위한 유저 500 명 생성 및 추가 를 위한 코드 */
//package com.example.auction.common.data;
//
//import com.example.auction.user.domain.Gender;
//import com.example.auction.user.dto.UserRegisterDto;
//import com.example.auction.user.repository.UserRepository;
//import com.example.auction.user.service.UserService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDate;
//
//@Slf4j
//@Component
//@Profile("seed-login-users")
//@RequiredArgsConstructor
//public class TestLoginUserSeedRunner implements CommandLineRunner {
//
//    private final UserService userService;
//    private final UserRepository userRepository;
//
//    @Override
//    public void run(String... args) {
//        int created = 0;
//        int skipped = 0;
//
//        for (int i = 3; i <= 502; i++) {
//            String email = "user" + i + "@auction.test";
//
//            if (userRepository.findByEmail(email).isPresent()) {
//                skipped++;
//                continue;
//            }
//
//            try {
//                UserRegisterDto dto = new UserRegisterDto();
//                dto.setEmail(email);
//                dto.setPassword("abcd1234");
//                dto.setName("테스트유저" + i);
//                dto.setNickname("u" + i);
//                dto.setPhone(String.format("0100000%04d", i));
//                dto.setAddress("테스트 주소 " + i);
//                dto.setBirthday("2000.01.01");
//                dto.setGender(Gender.M);
//
//                userService.register(dto);
//                created++;
//            } catch (Exception e) {
//                log.warn("[TEST_USER_SEED_FAIL] email={} message={}", email, e.getMessage());
//            }
//        }
//
//        log.info("[TEST_USER_SEED_DONE] created={}, skipped={}", created, skipped);
//    }
//}
