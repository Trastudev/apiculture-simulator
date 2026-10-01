package com.apiculture.simulator.domain.map;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SeaportCatalog {

    private static final List<Seaport> ALL;

    static {
        List<Seaport> list = new ArrayList<>();
        iberia(list);
        madagascar(list);
        southAfrica(list);
        ALL = Collections.unmodifiableList(list);
    }

    private SeaportCatalog() {
    }

    @NonNull
    public static List<Seaport> all() {
        return ALL;
    }

    @NonNull
    public static List<Seaport> in(@Nullable PlayableMapRegion region) {
        List<Seaport> out = new ArrayList<>();
        for (Seaport port : ALL) {
            if (region == null || port.region == region) {
                out.add(port);
            }
        }
        return out;
    }

    @Nullable
    public static Seaport byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Seaport port : ALL) {
            if (port.id.equals(id)) {
                return port;
            }
        }
        return null;
    }

    @NonNull
    public static String label(@NonNull Context context, @Nullable Seaport port) {
        if (port == null) {
            return "—";
        }
        int res = nameRes(port.id);
        return res == 0 ? port.name : context.getString(res);
    }

    /** Si el hex o la etiqueta guardada es un puerto, devuelve «Puerto de …». */
    @NonNull
    public static String present(@NonNull Context context, @Nullable String label, @Nullable String hexOrPortId) {
        Seaport byId = byId(hexOrPortId);
        if (byId != null) {
            return label(context, byId);
        }
        if (label == null || label.trim().isEmpty()) {
            return "—";
        }
        return label.trim();
    }

    @NonNull
    public static String present(@Nullable String label, @Nullable String hexOrPortId) {
        Seaport byId = byId(hexOrPortId);
        if (byId != null) {
            return byId.name;
        }
        if (label == null || label.trim().isEmpty()) {
            return "—";
        }
        return label.trim();
    }

    private static int nameRes(@NonNull String id) {
        switch (id) {
            case "bcn": return R.string.port_bcn;
            case "vlc": return R.string.port_vlc;
            case "cartagena": return R.string.port_cartagena;
            case "cadiz": return R.string.port_cadiz;
            case "palma": return R.string.port_palma;
            case "lisboa": return R.string.port_lisboa;
            case "coruna": return R.string.port_coruna;
            case "gijon": return R.string.port_gijon;
            case "sansebastian": return R.string.port_sansebastian;
            case "toamasina": return R.string.port_toamasina;
            case "mahajanga": return R.string.port_mahajanga;
            case "antsiranana": return R.string.port_antsiranana;
            case "toliara": return R.string.port_toliara;
            case "tolagnaro": return R.string.port_tolagnaro;
            case "capetown": return R.string.port_capetown;
            case "durban": return R.string.port_durban;
            case "gqeberha": return R.string.port_gqeberha;
            case "richards": return R.string.port_richards;
            case "saldanha": return R.string.port_saldanha;
            default: return 0;
        }
    }

    private static void iberia(List<Seaport> list) {
        list.add(port("bcn", "Puerto de Barcelona", PlayableMapRegion.IBERIA, 41.35, 2.17, "east-iberia"));
        list.add(port("vlc", "Puerto de Valencia", PlayableMapRegion.IBERIA, 39.44, -0.32, "east-iberia"));
        list.add(port("cartagena", "Puerto de Cartagena", PlayableMapRegion.IBERIA, 37.60, -0.98, "east-iberia"));
        list.add(port("cadiz", "Puerto de Cádiz", PlayableMapRegion.IBERIA, 36.53, -6.29, "west-iberia"));
        list.add(port("palma", "Puerto de Palma", PlayableMapRegion.IBERIA, 39.57, 2.63, "east-iberia"));
        list.add(port("lisboa", "Puerto de Lisboa", PlayableMapRegion.IBERIA, 38.71, -9.14, "west-iberia"));
        list.add(port("coruna", "Puerto de A Coruña", PlayableMapRegion.IBERIA, 43.37, -8.40, "west-iberia"));
        list.add(port("gijon", "Puerto de Gijón", PlayableMapRegion.IBERIA, 43.55, -5.66, "north-iberia"));
        list.add(port("sansebastian", "Puerto de San Sebastián", PlayableMapRegion.IBERIA, 43.32, -1.99, "north-iberia"));
    }

    private static void madagascar(List<Seaport> list) {
        list.add(port("toamasina", "Puerto de Toamasina", PlayableMapRegion.MADAGASCAR, -18.15, 49.40, "east-mdg"));
        list.add(port("mahajanga", "Puerto de Mahajanga", PlayableMapRegion.MADAGASCAR, -15.72, 46.32, "west-mdg"));
        list.add(port("antsiranana", "Puerto de Antsiranana", PlayableMapRegion.MADAGASCAR, -12.28, 49.29, "east-mdg"));
        list.add(port("toliara", "Puerto de Toliara", PlayableMapRegion.MADAGASCAR, -23.35, 43.67, "west-mdg"));
        list.add(port("tolagnaro", "Puerto de Tolagnaro", PlayableMapRegion.MADAGASCAR, -25.03, 46.99, "east-mdg"));
    }

    private static void southAfrica(List<Seaport> list) {
        list.add(port("capetown", "Puerto de Ciudad del Cabo", PlayableMapRegion.SOUTH_AFRICA, -33.92, 18.42, "west-za"));
        list.add(port("durban", "Puerto de Durban", PlayableMapRegion.SOUTH_AFRICA, -29.87, 31.02, "east-za"));
        list.add(port("gqeberha", "Puerto de Gqeberha", PlayableMapRegion.SOUTH_AFRICA, -33.96, 25.63, "west-za"));
        list.add(port("richards", "Puerto de Richards Bay", PlayableMapRegion.SOUTH_AFRICA, -28.80, 32.08, "east-za"));
        list.add(port("saldanha", "Puerto de Saldanha", PlayableMapRegion.SOUTH_AFRICA, -33.01, 17.95, "west-za"));
    }

    private static Seaport port(String id, String name, PlayableMapRegion region,
            double lat, double lng, String lane) {
        return new Seaport(id, name, region, lat, lng, lane);
    }
}
