package com.dw.movie.auth.jwt;

import com.dw.movie.auth.jwt.JwtTokenProvider.AccessTokenInfo;
import com.dw.movie.member.entity.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider("test-secret-key-for-unit-tests-0123456789abcdef", 1800000L, 1209600000L);

    @Test
    void 액세스_토큰에서_회원id와_권한을_꺼낸다() {
        String token = provider.createAccessToken(1L, Role.USER);

        AccessTokenInfo info = provider.parseAccessToken(token);

        assertNotNull(info);
        assertEquals(1L, info.memberId());
        assertEquals("USER", info.role());
    }

    @Test
    void 리프레시_토큰은_유효해도_액세스_토큰으로_인식되지_않는다() {
        String token = provider.createRefreshToken(1L);

        assertTrue(provider.validateToken(token));
        assertNull(provider.parseAccessToken(token));
    }

    @Test
    void 위조된_토큰은_액세스_토큰으로_인식되지_않는다() {
        String token = provider.createAccessToken(1L, Role.USER) + "x";

        assertNull(provider.parseAccessToken(token));
    }
}
