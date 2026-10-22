package com.dw.movie.auth;

import com.dw.movie.auth.entity.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierTest {

    @Test
    void 누적결제금액에_따라_등급이_정해진다() {
        assertEquals(Tier.BASIC, Tier.from(0L));
        assertEquals(Tier.BASIC, Tier.from(99_999L));
        assertEquals(Tier.SILVER, Tier.from(100_000L));
        assertEquals(Tier.GOLD, Tier.from(300_000L));
        assertEquals(Tier.VIP, Tier.from(700_000L));
        assertEquals(Tier.VIP, Tier.from(5_000_000L));
    }

    @Test
    void 등급별_적립률로_포인트를_계산하고_소수점은_버린다() {
        assertEquals(120L, Tier.BASIC.earnPoints(12_000L));
        assertEquals(240L, Tier.SILVER.earnPoints(12_000L));
        assertEquals(360L, Tier.GOLD.earnPoints(12_000L));
        assertEquals(600L, Tier.VIP.earnPoints(12_000L));
        assertEquals(0L, Tier.BASIC.earnPoints(99L));
    }
}
