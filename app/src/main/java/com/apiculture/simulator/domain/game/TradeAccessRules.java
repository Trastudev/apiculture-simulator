package com.apiculture.simulator.domain.game;

/** Cuotas para usar el puerto público y cada mercado internacional. */
public final class TradeAccessRules {

    public static final int PORT_LEVEL = 30;
    public static final int PORT_FEE_B = 10_000;
    public static final int MARKET_LEVEL = 35;
    public static final int MARKET_FEE_B = 15_000;

    private TradeAccessRules() {
    }

    public static boolean portLevelReached(int playerLevel) {
        return playerLevel >= PORT_LEVEL;
    }

    public static boolean marketLevelReached(int playerLevel) {
        return playerLevel >= MARKET_LEVEL;
    }
}
