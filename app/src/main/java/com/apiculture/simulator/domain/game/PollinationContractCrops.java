package com.apiculture.simulator.domain.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Rotación NPC de polinización por clima. Independiente de lo que el jugador puede sembrar:
 * Iberia se apaga en invierno; Madagascar (y ZA) cubren noviembre–febrero.
 */
public final class PollinationContractCrops {

    /** No mostrar el siguiente pico si está más lejos que esto (evita naranjos “para marzo” en diciembre). */
    public static final int MAX_AHEAD_DAYS = 21;

    private static final String NARANJOS = "Campo de naranjos";
    private static final String ALMENDROS = "Campo de almendros";
    private static final String CEREZOS = "Campo de cerezos";
    private static final String MANZANOS = "Campo de manzanos";
    private static final String PERALES = "Campo de perales";
    private static final String COLZA = "Campo de Colza";
    private static final String GIRASOLES = "Campo de girasoles";

    private static final List<String> MED = Arrays.asList(
            HexFlora.MOSTAZA, ALMENDROS, NARANJOS, HexFlora.TREBOL,
            HexFlora.LAVANDA_CAMPO, GIRASOLES, HexFlora.FACELIA);

    private static final List<String> CONTINENTAL = Arrays.asList(
            ALMENDROS, HexFlora.MOSTAZA, COLZA, CEREZOS, MANZANOS, PERALES,
            HexFlora.TREBOL, HexFlora.LAVANDA_CAMPO, GIRASOLES, HexFlora.FACELIA);

    private static final List<String> ATLANTIC = Arrays.asList(
            COLZA, MANZANOS, PERALES, CEREZOS, HexFlora.MOSTAZA, HexFlora.TREBOL,
            HexFlora.LAVANDA_CAMPO, GIRASOLES, HexFlora.FACELIA, HexFlora.RABANIZA);

    private static final List<String> MOUNTAIN = Arrays.asList(
            CEREZOS, MANZANOS, PERALES, HexFlora.TREBOL, HexFlora.LAVANDA_CAMPO, HexFlora.FACELIA);

    private static final List<String> SOUTH = Arrays.asList(
            ALMENDROS, HexFlora.MOSTAZA, NARANJOS, HexFlora.TREBOL,
            HexFlora.LAVANDA_CAMPO, GIRASOLES, HexFlora.FACELIA);

    private static final List<String> MDG_EQ = Arrays.asList(
            HexFlora.LITCHI, NARANJOS, HexFlora.TREBOL, GIRASOLES, HexFlora.FACELIA);

    private static final List<String> MDG_HIGH = Arrays.asList(
            HexFlora.CAFE, NARANJOS, HexFlora.TREBOL, HexFlora.FACELIA);

    private static final List<String> MDG_TROP = Arrays.asList(
            HexFlora.MANGO, NARANJOS, HexFlora.TREBOL, HexFlora.RABANIZA);

    private static final List<String> MDG_DESERT = Arrays.asList(
            HexFlora.SISAL, HexFlora.LUCERNA, HexFlora.MOSTAZA);

    private static final List<String> ZA_HV = Arrays.asList(
            GIRASOLES, HexFlora.LUCERNA, COLZA, HexFlora.TREBOL, HexFlora.MOSTAZA,
            HexFlora.FACELIA, HexFlora.RABANIZA);

    private static final List<String> ZA_BUSH = Arrays.asList(
            GIRASOLES, HexFlora.LUCERNA, COLZA, HexFlora.TREBOL, HexFlora.MOSTAZA,
            HexFlora.RABANIZA);

    private static final List<String> ZA_KAROO = Arrays.asList(
            HexFlora.LUCERNA, GIRASOLES, HexFlora.MOSTAZA, HexFlora.TREBOL, HexFlora.RABANIZA);

    private static final List<String> ZA_SUB = Arrays.asList(
            HexFlora.LITCHI, HexFlora.MACADAMIA, HexFlora.AGUACATE, NARANJOS,
            HexFlora.FACELIA, HexFlora.TREBOL, HexFlora.RABANIZA);

    private static final List<String> ZA_FYN = Arrays.asList(
            COLZA, NARANJOS, GIRASOLES, HexFlora.LUCERNA, HexFlora.MOSTAZA,
            HexFlora.TREBOL, HexFlora.FACELIA, HexFlora.RABANIZA);

    private PollinationContractCrops() {
    }

    @NonNull
    public static List<String> forParcel(@Nullable HexParcel parcel) {
        if (parcel == null) {
            return Collections.emptyList();
        }
        if (HexFlora.isMadagascarParcel(parcel)) {
            return forMdg(MadagascarClimateZone.forParcel(parcel));
        }
        if (HexFlora.isZaParcel(parcel)) {
            return forZa(SouthernAfricanClimateZone.forParcel(parcel));
        }
        return forIberia(IberianClimateZone.forParcel(parcel));
    }

    public static boolean isOfferedOnParcel(@Nullable String flora, @Nullable HexParcel parcel) {
        String key = HexFlora.canonicalKey(flora);
        if (key.isEmpty()) {
            return false;
        }
        List<String> crops = forParcel(parcel);
        for (int i = 0; i < crops.size(); i++) {
            if (key.equals(HexFlora.canonicalKey(crops.get(i)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * El inicio guardado ya quedó en un día anterior a {@code today},
     * aunque el tramo siga abierto.
     */
    public static boolean startedBeforeToday(int startDoy, int endDoy, int createdDayKey,
            @Nullable LocalDate today) {
        if (startDoy <= 0) {
            return false;
        }
        LocalDate day = today != null ? today : LocalDate.now(GameCalendar.userTimeZone());
        LocalDate created = createdDayKey > 0 ? GameCalendar.fromDayKey(createdDayKey) : day;
        if (created.isAfter(day)) {
            created = day;
        }
        int createdDoy = Math.min(365, created.getDayOfYear());
        boolean openThen = endDoy > 0 && FloraBloomWindow.containsDoy(
                new FloraBloomWindow.Span(startDoy, endDoy), createdDoy);
        LocalDate start = FloraBloomWindow.nextDateOfDoy(created, startDoy);
        if (openThen && start.isAfter(created)) {
            start = start.minusYears(1);
        }
        return start.isBefore(day);
    }

    public static int daysUntilWork(@Nullable NpcContractFarm farm, @Nullable LocalDate today) {
        if (farm == null) {
            return Integer.MAX_VALUE;
        }
        LocalDate day = today != null ? today : LocalDate.now();
        int doy = Math.min(365, day.getDayOfYear());
        if (farm.terms != null && farm.terms.startDoy > 0) {
            if (farm.terms.endDoy > 0 && FloraBloomWindow.containsDoy(
                    new FloraBloomWindow.Span(farm.terms.startDoy, farm.terms.endDoy), doy)) {
                return 0;
            }
            return FloraBloomWindow.daysUntilStart(farm.terms.startDoy, doy);
        }
        return FloraBloomWindow.daysUntilBloomStart(farm.flora, farm.parcel, day);
    }

    /**
     * Fracción de celdas que mantienen oferta (0–1). Invierno ibérico bajo;
     * verano austral en Madagascar/ZA alto.
     */
    public static double seasonalKeepRate(@Nullable HexParcel parcel, int dayOfYear) {
        int doy = Math.max(1, Math.min(365, dayOfYear));
        if (parcel == null) {
            return 1.0;
        }
        if (HexFlora.isMadagascarParcel(parcel)) {
            return mdgKeep(MadagascarClimateZone.forParcel(parcel), doy);
        }
        if (HexFlora.isZaParcel(parcel)) {
            return zaKeep(SouthernAfricanClimateZone.forParcel(parcel), doy);
        }
        return iberiaKeep(IberianClimateZone.forParcel(parcel), doy);
    }

    private static List<String> forIberia(IberianClimateZone zone) {
        switch (zone) {
            case ATLANTIC:
                return ATLANTIC;
            case MOUNTAIN:
                return MOUNTAIN;
            case SOUTH:
                return SOUTH;
            case CONTINENTAL:
                return CONTINENTAL;
            case MEDITERRANEAN:
            default:
                return MED;
        }
    }

    private static List<String> forMdg(MadagascarClimateZone zone) {
        switch (zone) {
            case HIGHLANDS:
                return MDG_HIGH;
            case DESERT:
                return MDG_DESERT;
            case TROPICAL:
                return MDG_TROP;
            case EQUATORIAL:
            default:
                return MDG_EQ;
        }
    }

    private static List<String> forZa(SouthernAfricanClimateZone zone) {
        switch (zone) {
            case FYNBOS:
                return ZA_FYN;
            case KAROO:
                return ZA_KAROO;
            case SUBTROPICAL:
                return ZA_SUB;
            case BUSHVELD:
                return ZA_BUSH;
            case HIGHVELD:
            default:
                return ZA_HV;
        }
    }

    private static double iberiaKeep(IberianClimateZone zone, int doy) {
        if (zone == IberianClimateZone.MOUNTAIN) {
            return (doy >= 121 && doy <= 273) ? 1.0 : 0.05;
        }
        if (doy >= 305 || doy <= 31) {
            return zone == IberianClimateZone.ATLANTIC ? 0.18 : 0.08;
        }
        if (doy <= 59) {
            return 0.45;
        }
        return 1.0;
    }

    private static double mdgKeep(MadagascarClimateZone zone, int doy) {
        boolean australSummer = doy >= 305 || doy <= 60;
        switch (zone) {
            case HIGHLANDS:
                if (doy >= 244 && doy <= 334) {
                    return 1.0;
                }
                if (australSummer) {
                    return 0.7;
                }
                if (doy >= 61 && doy <= 120) {
                    return 0.45;
                }
                return 0.12;
            case TROPICAL:
                if (doy >= 121 && doy <= 243) {
                    return 1.0;
                }
                if (australSummer) {
                    return 0.65;
                }
                return 0.35;
            case DESERT:
                if (australSummer) {
                    return 0.85;
                }
                if (doy >= 121 && doy <= 212) {
                    return 0.45;
                }
                return 0.12;
            case EQUATORIAL:
            default:
                if (australSummer) {
                    return 1.0;
                }
                if (doy <= 120) {
                    return 0.55;
                }
                if (doy <= 243) {
                    return 0.12;
                }
                return 0.75;
        }
    }

    private static double zaKeep(SouthernAfricanClimateZone zone, int doy) {
        boolean australSummer = doy >= 305 || doy <= 60;
        boolean australWinter = doy >= 152 && doy <= 243;
        switch (zone) {
            case KAROO:
                if (australSummer) {
                    return 0.55;
                }
                return australWinter ? 0.1 : 0.28;
            case SUBTROPICAL:
                if (doy >= 305 || doy <= 31) {
                    return 1.0;
                }
                if (doy >= 182 && doy <= 304) {
                    return 0.85;
                }
                return 0.35;
            case FYNBOS:
                if (doy >= 213 && doy <= 304) {
                    return 1.0;
                }
                if (australSummer) {
                    return 0.75;
                }
                return 0.28;
            case BUSHVELD:
            case HIGHVELD:
            default:
                if (australSummer) {
                    return 1.0;
                }
                if (australWinter) {
                    return 0.15;
                }
                return 0.5;
        }
    }
}
