package com.dw.movie.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Long totalSpent = 0L;
    private Long points = 0L;

    @Enumerated(EnumType.STRING)
    private Tier tier = Tier.BASIC;

    private LocalDateTime createAt;

    private String refreshToken;

    public Member (String email, String password, String name, String phone){
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = Role.USER;
        this.tier = Tier.BASIC;
        this.totalSpent = 0L;
        this.points = 0L;
        this.createAt = LocalDateTime.now();
    }
    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

}
