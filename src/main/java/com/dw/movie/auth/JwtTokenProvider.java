package com.dw.movie.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity}") long accessTokenValidity,
            @Value("${jwt.refresh-token-validity}") long refreshTokenValidity
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
    }

    public String createAccessToken(Long memberId, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValidity);

        return Jwts.builder()
                .claim("memberId", memberId)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long memberId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenValidity);

        return Jwts.builder()
                .claim("memberId", memberId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}