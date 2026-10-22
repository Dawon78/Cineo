package com.dw.movie.auth;

import com.dw.movie.auth.entity.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider("test-secret-key-for-unit-tests-0123456789abcdef", 1800000L, 1209600000L);

    @Test
    void 액세스_토큰은_액세스_토큰으로_인식된다() {
        String token = provider.createAccessToken(1L, Role.USER);

        assertTrue(provider.validateToken(token));
        assertTrue(provider.isAccessToken(token));
    }

    @Test
    void 리프레시_토큰은_유효해도_액세스_토큰으로_인식되지_않는다() {
        String token = provider.createRefreshToken(1L);

        assertTrue(provider.validateToken(token));
        assertFalse(provider.isAccessToken(token));
    }
}
