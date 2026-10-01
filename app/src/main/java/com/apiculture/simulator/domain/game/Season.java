package com.apiculture.simulator.domain.game;

import android.content.Context;

import androidx.annotation.NonNull;

import com.apiculture.simulator.R;

public enum Season {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    @NonNull
    public String label(@NonNull Context context) {
        switch (this) {
            case SPRING:
                return context.getString(R.string.season_spring);
            case SUMMER:
                return context.getString(R.string.season_summer);
            case AUTUMN:
                return context.getString(R.string.season_autumn);
            case WINTER:
            default:
                return context.getString(R.string.season_winter);
        }
    }

    public String emoji() {
        switch (this) {
            case SPRING:
                return "\uD83C\uDF38";
            case SUMMER:
                return "\u2600\uFE0F";
            case AUTUMN:
                return "\uD83C\uDF42";
            case WINTER:
            default:
                return "\u2744\uFE0F";
        }
    }

    public static Season fromDayOfYear(int dayOfYear) {
        if (dayOfYear < 80) return WINTER;
        if (dayOfYear < 172) return SPRING;
        if (dayOfYear < 264) return SUMMER;
        if (dayOfYear < 355) return AUTUMN;
        return WINTER;
    }
}
