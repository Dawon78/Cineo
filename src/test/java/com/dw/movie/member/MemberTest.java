package com.dw.movie.member;

import com.dw.movie.member.entity.Member;
import com.dw.movie.member.entity.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberTest {

    private Member newMember() {
        return new Member("a@a.com", "pw", "name", "010");
    }

    @Test
    void 결제를_기록하면_누적금액과_포인트가_늘고_등급이_오른다() {
        Member member = newMember();

        long earned = member.recordPayment(100_000L);

        assertEquals(1_000L, earned);
        assertEquals(100_000L, member.getTotalSpent());
        assertEquals(1_000L, member.getPoints());
        assertEquals(Tier.SILVER, member.getTier());
    }

    @Test
    void 포인트는_결제_당시_등급의_적립률로_쌓인다() {
        Member member = newMember();
        member.recordPayment(100_000L);

        long earned = member.recordPayment(10_000L);

        assertEquals(200L, earned);
    }

    @Test
    void 환불하면_누적금액과_포인트가_줄고_등급이_내려간다() {
        Member member = newMember();
        long earned = member.recordPayment(100_000L);

        member.refundPayment(100_000L, earned);

        assertEquals(0L, member.getTotalSpent());
        assertEquals(0L, member.getPoints());
        assertEquals(Tier.BASIC, member.getTier());
    }

    @Test
    void 환불해도_누적금액과_포인트는_0_아래로_내려가지_않는다() {
        Member member = newMember();
        member.recordPayment(10_000L);

        member.refundPayment(50_000L, 5_000L);

        assertEquals(0L, member.getTotalSpent());
        assertEquals(0L, member.getPoints());
    }
}
