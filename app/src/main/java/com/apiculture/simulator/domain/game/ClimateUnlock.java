package com.apiculture.simulator.domain.game;

import android.content.Context;

import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Desbloqueo de climas (y de la región Sudáfrica) por nivel de jugador.
 */
public final class ClimateUnlock {

    public static final int LEVEL_MEDITERRANEAN = 0;
    public static final int LEVEL_CONTINENTAL = 5;
    public static final int LEVEL_MDG_EQUATORIAL = 0;
    public static final int LEVEL_MDG_HIGHLANDS = 10;
    public static final int LEVEL_ATLANTIC = 15;
    public static final int LEVEL_SOUTH = 35;
    public static final int LEVEL_MDG_TROPICAL = 20;
    public static final int LEVEL_MOUNTAIN = 25;
    public static final int LEVEL_MDG_DESERT = 30;
    public static final int LEVEL_SOUTH_AFRICA = 40;
    public static final int LEVEL_ZA_HIGHVELD = 40;
    public static final int LEVEL_ZA_BUSHVELD = 40;
    public static final int LEVEL_ZA_KAROO = 45;
    public static final int LEVEL_ZA_SUBTROPICAL = 50;
    public static final int LEVEL_ZA_FYNBOS = 55;

    private ClimateUnlock() {
    }

    public static int minLevel(IberianClimateZone zone) {
        if (zone == null) {
            return LEVEL_CONTINENTAL;
        }
        switch (zone) {
            case MEDITERRANEAN:
                return LEVEL_MEDITERRANEAN;
            case CONTINENTAL:
                return LEVEL_CONTINENTAL;
            case ATLANTIC:
                return LEVEL_ATLANTIC;
            case SOUTH:
                return LEVEL_SOUTH;
            case MOUNTAIN:
            default:
                return LEVEL_MOUNTAIN;
        }
    }

    public static int minLevel(SouthernAfricanClimateZone zone) {
        if (zone == null) {
            return LEVEL_ZA_HIGHVELD;
        }
        switch (zone) {
            case HIGHVELD:
                return LEVEL_ZA_HIGHVELD;
            case BUSHVELD:
                return LEVEL_ZA_BUSHVELD;
            case KAROO:
                return LEVEL_ZA_KAROO;
            case SUBTROPICAL:
                return LEVEL_ZA_SUBTROPICAL;
            case FYNBOS:
            default:
                return LEVEL_ZA_FYNBOS;
        }
    }

    public static int minLevel(MadagascarClimateZone zone) {
        if (zone == null) {
            return LEVEL_MDG_TROPICAL;
        }
        switch (zone) {
            case EQUATORIAL:
                return LEVEL_MDG_EQUATORIAL;
            case HIGHLANDS:
                return LEVEL_MDG_HIGHLANDS;
            case DESERT:
                return LEVEL_MDG_DESERT;
            case TROPICAL:
            default:
                return LEVEL_MDG_TROPICAL;
        }
    }

    public static boolean canAccessSouthAfrica(int playerLevel) {
        return Math.max(0, playerLevel) >= LEVEL_SOUTH_AFRICA;
    }

    public static boolean canBuy(IberianClimateZone zone, int playerLevel) {
        return Math.max(0, playerLevel) >= minLevel(zone);
    }

    public static boolean canBuy(SouthernAfricanClimateZone zone, int playerLevel) {
        return Math.max(0, playerLevel) >= minLevel(zone);
    }

    public static boolean canAccessMadagascar(int playerLevel) {
        return Math.max(0, playerLevel) >= LEVEL_MDG_EQUATORIAL;
    }

    public static boolean canBuy(MadagascarClimateZone zone, int playerLevel) {
        return Math.max(0, playerLevel) >= minLevel(zone);
    }

    public static boolean canBuyParcel(HexParcel parcel, int playerLevel) {
        if (parcel == null) {
            return false;
        }
        int lvl = Math.max(0, playerLevel);
        if (HexFlora.isMadagascarParcel(parcel)) {
            return canAccessMadagascar(lvl)
                    && canBuy(MadagascarClimateZone.forParcel(parcel), lvl);
        }
        if (HexFlora.isZaParcel(parcel)) {
            return canAccessSouthAfrica(lvl)
                    && canBuy(SouthernAfricanClimateZone.forParcel(parcel), lvl);
        }
        return canBuy(IberianClimateZone.forParcel(parcel), lvl);
    }

    public static int minLevelForParcel(HexParcel parcel) {
        if (parcel == null) {
            return 0;
        }
        if (HexFlora.isMadagascarParcel(parcel)) {
            return minLevel(MadagascarClimateZone.forParcel(parcel));
        }
        if (HexFlora.isZaParcel(parcel)) {
            return Math.max(LEVEL_SOUTH_AFRICA, minLevel(SouthernAfricanClimateZone.forParcel(parcel)));
        }
        return minLevel(IberianClimateZone.forParcel(parcel));
    }

    public static String climateLabel(Context context, HexParcel parcel) {
        if (parcel == null || context == null) {
            return "—";
        }
        if (com.apiculture.simulator.domain.parcel.HexFlora.isMadagascarParcel(parcel)) {
            return MadagascarClimateZone.forParcel(parcel).label(context);
        }
        if (com.apiculture.simulator.domain.parcel.HexFlora.isZaParcel(parcel)) {
            return SouthernAfricanClimateZone.forParcel(parcel).label(context);
        }
        return IberianClimateZone.forParcel(parcel).label(context);
    }

    public static String climateLabelForParcel(HexParcel parcel) {
        if (parcel == null) {
            return "—";
        }
        if (HexFlora.isMadagascarParcel(parcel)) {
            return MadagascarClimateZone.forParcel(parcel).labelEs();
        }
        if (HexFlora.isZaParcel(parcel)) {
            return SouthernAfricanClimateZone.forParcel(parcel).labelEs();
        }
        return IberianClimateZone.forParcel(parcel).labelEs();
    }

    /**
     * Climas que se acaban de desbloquear al pasar de {@code prevLevel} a {@code newLevel}.
     */
    public static List<Unlock> newlyUnlocked(int prevLevel, int newLevel) {
        if (newLevel <= prevLevel) {
            return Collections.emptyList();
        }
        List<Unlock> out = new ArrayList<>();
        for (Unlock u : allInOrder()) {
            if (u.minLevel > prevLevel && u.minLevel <= newLevel) {
                out.add(u);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<Unlock> allInOrder() {
        List<Unlock> all = new ArrayList<>();
        all.add(Unlock.iberia(IberianClimateZone.CONTINENTAL, LEVEL_CONTINENTAL, "Continental"));
        all.add(Unlock.mdg(MadagascarClimateZone.HIGHLANDS, LEVEL_MDG_HIGHLANDS, "Altiplano"));
        all.add(Unlock.iberia(IberianClimateZone.ATLANTIC, LEVEL_ATLANTIC, "Atlántico"));
        all.add(Unlock.mdg(MadagascarClimateZone.TROPICAL, LEVEL_MDG_TROPICAL, "Tropical"));
        all.add(Unlock.iberia(IberianClimateZone.MOUNTAIN, LEVEL_MOUNTAIN, "Alta montaña"));
        all.add(Unlock.mdg(MadagascarClimateZone.DESERT, LEVEL_MDG_DESERT, "Desierto"));
        all.add(Unlock.iberia(IberianClimateZone.SOUTH, LEVEL_SOUTH, "Sur"));
        all.add(Unlock.southAfricaRegion(LEVEL_SOUTH_AFRICA, "Sudáfrica"));
        all.add(Unlock.za(SouthernAfricanClimateZone.HIGHVELD, LEVEL_ZA_HIGHVELD, "Highveld"));
        all.add(Unlock.za(SouthernAfricanClimateZone.BUSHVELD, LEVEL_ZA_BUSHVELD, "Bushveld"));
        all.add(Unlock.za(SouthernAfricanClimateZone.KAROO, LEVEL_ZA_KAROO, "Karoo"));
        all.add(Unlock.za(SouthernAfricanClimateZone.SUBTROPICAL, LEVEL_ZA_SUBTROPICAL, "Costa subtropical"));
        all.add(Unlock.za(SouthernAfricanClimateZone.FYNBOS, LEVEL_ZA_FYNBOS, "Fynbos"));
        return Collections.unmodifiableList(all);
    }

    public static final class Unlock {
        public final IberianClimateZone iberia;
        public final SouthernAfricanClimateZone za;
        public final MadagascarClimateZone mdg;
        public final boolean southAfricaRegion;
        public final int minLevel;
        public final String labelEs;

        private Unlock(
                IberianClimateZone iberia,
                SouthernAfricanClimateZone za,
                MadagascarClimateZone mdg,
                boolean southAfricaRegion,
                int minLevel,
                String labelEs) {
            this.iberia = iberia;
            this.za = za;
            this.mdg = mdg;
            this.southAfricaRegion = southAfricaRegion;
            this.minLevel = minLevel;
            this.labelEs = labelEs;
        }

        static Unlock iberia(IberianClimateZone z, int level, String label) {
            return new Unlock(z, null, null, false, level, label);
        }

        static Unlock za(SouthernAfricanClimateZone z, int level, String label) {
            return new Unlock(null, z, null, false, level, label);
        }

        static Unlock mdg(MadagascarClimateZone z, int level, String label) {
            return new Unlock(null, null, z, false, level, label);
        }

        static Unlock southAfricaRegion(int level, String label) {
            return new Unlock(null, null, null, true, level, label);
        }

        public boolean southern() {
            return za != null || southAfricaRegion;
        }

        public boolean madagascar() {
            return mdg != null;
        }
    }
}
