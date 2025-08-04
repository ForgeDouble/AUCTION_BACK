package com.example.auction.user.domain;


import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.user.dto.UserUpdateDto;
import jakarta.persistence.*;
import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(length = 100)
    private String address;

    @Column(nullable = false, length = 20)
    private String birthday; // xxxx.xx.xx

    @Column(nullable = false, length = 11)
    private String phone; // xxx-xxxx-xxxx

    @Column(nullable = false)
    private Long warning = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Authority authority;

//    private String profileImage;

    public void update(UserUpdateDto dto) {
        this.name = dto.getName();
        this.gender = dto.getGender();
        this.address = dto.getAddress();
        this.birthday = dto.getBirthday();
        this.phone = dto.getPhone();
    }
}
