package com.example.auction.common.data;

import com.example.auction.category.domain.Category;
import com.example.auction.category.repository.CategoryRepository;
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
    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        seedUsers();
        seedCategories();
    }

    private void seedUsers() {
        // 관리자
        createUserIfNotExists(
                "admin@auction.test", "관리자", "9999",
                Gender.M, "1990.01.01", "01012345678",
                "서울시 중구 어딘가", "주딱", Authority.ADMIN
        );
        // 일반 유저
        createUserIfNotExists(
                "user1@auction.test", "홍길동", "1234",
                Gender.W, "1995.05.05", "01098765432",
                "서울시 강남구 어딘가", "유저임", Authority.USER
        );
        // 문의 직원 생성
        createUserIfNotExists(
                "admin2@auction.test", "문의직원", "1234",
                Gender.W, "2000.01.01", "01012341234",
                "서울시 중구 어딘가", "문의직원", Authority.INQUIRY
        );
    }

    private void createUserIfNotExists(
            String email, String name, String rawPassword,
            Gender gender, String birthday, String phone,
            String address, String nickname, Authority authority
    ) {
        userRepository.findByEmail(email).ifPresentOrElse(
                u -> {}, // already exists
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

    private void seedCategories() {
        // 루트
        Category electronics = upsertRoot("전자제품");
        Category fashion     = upsertRoot("패션/잡화");
        Category home        = upsertRoot("생활/가전");
        Category hobby       = upsertRoot("취미/레저");
        Category collect     = upsertRoot("컬렉터블");
        Category vehicle     = upsertRoot("자동차/오토바이");
        Category media       = upsertRoot("도서/음반/영화");

        // 전자제품 하위
        upsertChild("노트북/태블릿", electronics);
        upsertChild("데스크탑/부품", electronics);
        upsertChild("스마트폰/웨어러블", electronics);
        upsertChild("카메라/드론", electronics);
        upsertChild("TV/오디오", electronics);

        // 패션/잡화 하위
        upsertChild("여성의류", fashion);
        upsertChild("남성의류", fashion);
        upsertChild("신발", fashion);
        upsertChild("가방/지갑", fashion);
        upsertChild("액세서리/시계", fashion);

        // 생활/가전 하위
        upsertChild("주방/식기", home);
        upsertChild("청소/세탁기기", home);
        upsertChild("냉장고/에어컨", home);
        upsertChild("소형가전", home);

        // 취미/레저 하위
        upsertChild("자전거/보드", hobby);
        upsertChild("캠핑/등산", hobby);
        upsertChild("악기", hobby);
        upsertChild("게임/콘솔", hobby);

        // 컬렉터블 하위
        upsertChild("피규어/키덜트", collect);
        upsertChild("트레이딩카드", collect);
        upsertChild("코인/우표", collect);

        // 자동차/오토바이 하위
        upsertChild("자동차용품", vehicle);
        upsertChild("오토바이용품", vehicle);
        upsertChild("휠/타이어", vehicle);

        // 도서/음반/영화 하위
        upsertChild("도서", media);
        upsertChild("음반/LP", media);
        upsertChild("DVD/블루레이", media);
    }

    private Category upsertRoot(String name) {
        return categoryRepository.findByCategoryName(name)
                .orElseGet(() -> categoryRepository.save(
                        Category.builder()
                                .categoryName(name)
                                .parent(null)
                                .build()
                ));
    }

    private Category upsertChild(String name, Category parent) {
        return categoryRepository.findByCategoryName(name)
                .orElseGet(() -> categoryRepository.save(
                        Category.builder()
                                .categoryName(name)
                                .parent(parent)
                                .build()
                ));
    }
}