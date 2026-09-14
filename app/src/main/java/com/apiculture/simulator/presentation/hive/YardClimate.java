package com.apiculture.simulator.presentation.hive;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.game.DailySkyCondition;
import com.apiculture.simulator.domain.game.DailyWeather;
import com.apiculture.simulator.domain.game.GameCalendar;
import com.apiculture.simulator.domain.game.HexNectarRules;
import com.apiculture.simulator.domain.game.IberianClimateZone;
import com.apiculture.simulator.domain.game.SouthernAfricanClimateZone;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.time.LocalDate;

/**
 * Paisaje del apiario: clima ibérico o sudafricano, independiente del tiempo (sol, nubes, lluvia).
 */
public enum YardClimate {
    ATLANTIC,
    MOUNTAIN,
    MEDITERRANEAN,
    SOUTH,
    CONTINENTAL,
    FYNBOS,
    KAROO,
    HIGHVELD,
    SUBTROPICAL,
    BUSHVELD;

    public String labelEs() {
        switch (this) {
            case ATLANTIC:
                return "Atlántico";
            case MOUNTAIN:
                return "Montaña";
            case MEDITERRANEAN:
                return "Mediterráneo";
            case SOUTH:
                return "Sur";
            case FYNBOS:
                return "Fynbos";
            case KAROO:
                return "Karoo";
            case HIGHVELD:
                return "Highveld";
            case SUBTROPICAL:
                return "Subtropical";
            case BUSHVELD:
                return "Bushveld";
            case CONTINENTAL:
            default:
                return "Continental";
        }
    }

    @DrawableRes
    public int backdropRes() {
        switch (this) {
            case ATLANTIC:
                return R.drawable.bg_yard_atlantic;
            case MOUNTAIN:
                return R.drawable.bg_yard_mountain;
            case MEDITERRANEAN:
                return R.drawable.bg_yard_mediterranean;
            case SOUTH:
                return R.drawable.bg_yard_south;
            case FYNBOS:
                return R.drawable.bg_yard_fynbos;
            case KAROO:
                return R.drawable.bg_yard_karoo;
            case HIGHVELD:
                return R.drawable.bg_yard_highveld;
            case SUBTROPICAL:
                return R.drawable.bg_yard_subtropical;
            case BUSHVELD:
                return R.drawable.bg_yard_bushveld;
            case CONTINENTAL:
            default:
                return R.drawable.bg_yard_continental;
        }
    }

    public static YardClimate fromIberia(@Nullable IberianClimateZone zone) {
        if (zone == null) {
            return CONTINENTAL;
        }
        switch (zone) {
            case ATLANTIC:
                return ATLANTIC;
            case MOUNTAIN:
                return MOUNTAIN;
            case MEDITERRANEAN:
                return MEDITERRANEAN;
            case SOUTH:
                return SOUTH;
            case CONTINENTAL:
            default:
                return CONTINENTAL;
        }
    }

    public static YardClimate fromZa(@Nullable SouthernAfricanClimateZone zone) {
        if (zone == null) {
            return HIGHVELD;
        }
        switch (zone) {
            case FYNBOS:
                return FYNBOS;
            case KAROO:
                return KAROO;
            case SUBTROPICAL:
                return SUBTROPICAL;
            case BUSHVELD:
                return BUSHVELD;
            case HIGHVELD:
            default:
                return HIGHVELD;
        }
    }

    public static YardClimate resolve(@Nullable Context context, @Nullable String hexId,
                                      @Nullable HiveEntity sample) {
        boolean southern = PlayableMapRegion.fromHexId(hexId) == PlayableMapRegion.SOUTH_AFRICA
                || HexNectarRules.isSouthernHive(sample);
        HexParcel parcel = null;
        if (context != null && hexId != null && !hexId.isEmpty()) {
            parcel = IberiaHexOverlayStore.findById(context.getApplicationContext(), hexId);
        }
        if (southern) {
            if (parcel != null) {
                return fromZa(SouthernAfricanClimateZone.forParcel(parcel));
            }
            return fromZa(HexNectarRules.southernZoneForHive(sample));
        }
        if (parcel != null) {
            return fromIberia(IberianClimateZone.forParcel(parcel));
        }
        return fromIberia(HexNectarRules.zoneForHive(sample));
    }

    public static DailySkyCondition yesterdaySky(@Nullable String hexId, @Nullable HiveEntity sample,
                                                 @Nullable HexParcel parcel) {
        return yesterdaySky(hexId, sample, parcel, null);
    }

    /**
     * Cielo del día anterior en el apiario. Si hay observación Open-Meteo, manda (igual que la producción).
     */
    public static DailySkyCondition yesterdaySky(@Nullable String hexId, @Nullable HiveEntity sample,
                                                 @Nullable HexParcel parcel,
                                                 @Nullable DailyWeather observed) {
        LocalDate yesterday = LocalDate.now(GameCalendar.userTimeZone()).minusDays(1);
        int dayKey = GameCalendar.toDayKey(yesterday);
        // El cielo del apiario es por parcela, no por colmena (misma previa y patio).
        String skyKey;
        if (hexId != null && !hexId.isEmpty()) {
            skyKey = "yard:" + hexId;
        } else if (sample != null && sample.id != null) {
            skyKey = "hive:" + sample.id;
        } else {
            skyKey = "yard:";
        }
        int elev = 400;
        if (parcel != null && parcel.maxElevationMeters != null) {
            elev = parcel.maxElevationMeters;
        } else if (sample != null && sample.elevationMeters >= 0) {
            elev = sample.elevationMeters;
        }
        return DailySkyCondition.forHiveDay(skyKey, dayKey, elev, observed);
    }

    @Nullable
    public static double[] weatherLatLng(@Nullable HexParcel parcel, @Nullable HiveEntity sample) {
        if (parcel != null) {
            return new double[]{parcel.centroidLat, parcel.centroidLon};
        }
        if (sample != null && !Double.isNaN(sample.lat) && !Double.isNaN(sample.lng)
                && !(sample.lat == 0.0 && sample.lng == 0.0)) {
            return new double[]{sample.lat, sample.lng};
        }
        return null;
    }
}
