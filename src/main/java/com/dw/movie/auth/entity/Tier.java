package com.dw.movie.auth.entity;

public enum Tier {
    BASIC(0L, 1),
    SILVER(100_000L, 2),
    GOLD(300_000L, 3),
    VIP(700_000L, 5);

    private final long minSpent;
    private final int pointRatePercent;

    Tier(long minSpent, int pointRatePercent) {
        this.minSpent = minSpent;
        this.pointRatePercent = pointRatePercent;
    }

    public long earnPoints(long amount) {
        return amount * pointRatePercent / 100;
    }

    public static Tier from(long totalSpent) {
        Tier result = BASIC;
        for (Tier tier : values()) {
            if (totalSpent >= tier.minSpent) {
                result = tier;
            }
        }
        return result;
    }
}
