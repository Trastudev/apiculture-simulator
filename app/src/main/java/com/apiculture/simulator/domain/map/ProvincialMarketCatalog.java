package com.apiculture.simulator.domain.map;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.game.TranshumanceRules;
import com.apiculture.simulator.domain.parcel.HexParcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Capitales de provincia (demanda 75 %) y mercados locales (25 %)
 * repartidos por el territorio, no por las ciudades más pobladas.
 */
public final class ProvincialMarketCatalog {

    private static final double MAX_SNAP_KM = 45.0;
    private static final Map<PlayableMapRegion, List<ProvincialMarket>> CACHE =
            new EnumMap<>(PlayableMapRegion.class);

    private static final double[][] IBERIA = {
            {40.4168, -3.7038}, {41.3851, 2.1734}, {39.4699, -0.3763}, {37.3891, -5.9845},
            {36.7213, -4.4214}, {37.1773, -3.5986}, {37.8882, -4.7794}, {36.5271, -6.2886},
            {37.2614, -6.9447}, {36.8381, -2.4597}, {37.7796, -3.7849}, {38.3452, -0.4810},
            {39.9864, -0.0513}, {37.9922, -1.1307}, {38.8794, -6.9707}, {39.4753, -6.3724},
            {39.8628, -4.0273}, {38.9860, -3.9273}, {40.0704, -2.1374}, {40.6333, -3.1669},
            {38.9943, -1.8585}, {41.6523, -4.7245}, {42.5987, -5.5671}, {40.9701, -5.6635},
            {42.3439, -3.6969}, {40.6565, -4.6818}, {40.9429, -4.1088}, {41.7636, -2.4649}, {42.0096, -4.5288},
            {41.5033, -5.7438}, {43.3619, -5.8494}, {43.4623, -3.8099}, {43.2630, -2.9350},
            {43.3183, -1.9812}, {42.8467, -2.6716}, {42.8125, -1.6458}, {42.4627, -2.4449},
            {41.6488, -0.8891}, {42.1362, -0.4087}, {40.3440, -1.1069}, {41.1187, 1.2453},
            {41.9794, 2.8214}, {41.6176, 0.6200}, {43.3623, -8.4115}, {43.0097, -7.5567},
            {42.3358, -7.8639}, {42.4300, -8.6443}, {39.5696, 2.6502},
            {38.7223, -9.1393}, {41.1579, -8.6291}, {41.5518, -8.4229}, {41.6918, -8.8345},
            {41.3006, -7.7443}, {41.8072, -6.7590}, {40.6405, -8.6538}, {40.6566, -7.9125},
            {40.5371, -7.2661}, {40.2033, -8.4103}, {39.7436, -8.8071}, {39.8222, -7.4909},
            {39.2362, -8.6850}, {39.2967, -7.4289}, {38.5714, -7.9096}, {38.5244, -8.8882},
            {38.0151, -7.8632}, {37.0194, -7.9322}
    };
    private static final String[] IBERIA_NAMES = {
            "Madrid", "Barcelona", "Valencia", "Sevilla", "Málaga", "Granada", "Córdoba", "Cádiz",
            "Huelva", "Almería", "Jaén", "Alicante", "Castellón", "Murcia", "Badajoz", "Cáceres",
            "Toledo", "Ciudad Real", "Cuenca", "Guadalajara", "Albacete", "Valladolid", "León",
            "Salamanca", "Burgos", "Ávila", "Segovia", "Soria", "Palencia", "Zamora", "Oviedo", "Santander",
            "Bilbao", "San Sebastián", "Vitoria", "Pamplona", "Logroño", "Zaragoza", "Huesca",
            "Teruel", "Tarragona", "Girona", "Lleida", "A Coruña", "Lugo", "Ourense", "Pontevedra",
            "Palma",
            "Lisboa", "Porto", "Braga", "Viana do Castelo", "Vila Real", "Bragança", "Aveiro",
            "Viseu", "Guarda", "Coimbra", "Leiria", "Castelo Branco", "Santarém", "Portalegre",
            "Évora", "Setúbal", "Beja", "Faro"
    };
    private static final int[] IBERIA_POP = {
            3332035, 1660419, 807693, 684025, 586384, 232208, 325708, 113066,
            142538, 200753, 111932, 349282, 176498, 460349, 150543, 96129,
            85621, 75191, 53898, 90041, 172816, 297459, 122009,
            144436, 175921, 57739, 51293, 39756, 76812, 59575, 217552, 172726,
            346405, 188102, 253672, 205723, 150808, 682513, 54419,
            35900, 138141, 104213, 140797, 247126, 98025, 104596, 83029,
            423350,
            545796, 231962, 193333, 85778, 50699, 34594, 80880,
            99274, 42541, 140796, 128640, 56109, 60097, 22368,
            53591, 123564, 33855, 67733
    };

    private static final double[][] ZA = {
            {-33.9249, 18.4241}, {-29.0852, 26.1596}, {-26.2041, 28.0473}, {-29.6006, 30.3794},
            {-32.8472, 27.4422}, {-25.4658, 30.9853}, {-23.9045, 29.4689}, {-28.7282, 24.7499},
            {-25.8656, 25.6442}
    };
    private static final String[] ZA_NAMES = {
            "Cape Town", "Bloemfontein", "Johannesburgo", "Pietermaritzburg",
            "Bhisho", "Mbombela", "Polokwane", "Kimberley", "Mahikeng"
    };
    private static final int[] ZA_POP = {
            4778015, 747431, 5782564, 618536, 77662, 588794, 797127, 256991, 314394
    };

    private static final double[][] MDG = {
            {-18.8792, 47.5079}, {-18.1492, 49.4023}, {-15.7167, 46.3167},
            {-23.3500, 43.6667}, {-21.4536, 47.0858}, {-13.3188, 48.3068}
    };
    private static final String[] MDG_NAMES = {
            "Antananarivo", "Toamasina", "Mahajanga",
            "Toliara", "Fianarantsoa", "Antsiranana"
    };
    private static final int[] MDG_POP = {
            1275207, 325857, 246022, 168756, 191776, 129320
    };

    /** Lonjas de importación: aquí se vende la miel que el territorio no produce. */
    private static final Object[][] IBERIA_INTERNATIONAL = {
            {"Algeciras", 36.1408, -5.4526},
            {"Vigo", 42.2406, -8.7207},
            {"Cartagena", 37.6057, -0.9913},
            {"Gijón", 43.5453, -5.6619}
    };
    private static final Object[][] ZA_INTERNATIONAL = {
            {"Durban", -29.8587, 31.0218},
            {"Gqeberha", -33.9608, 25.6022},
            {"Richards Bay", -28.7807, 32.0383}
    };
    private static final Object[][] MDG_INTERNATIONAL = {
            {"Manakara", -22.1486, 48.0106},
            {"Nosy Be", -13.3119, 48.2582}
    };

    /** Municipios repartidos por la península; padrón INE 2025 / concelhos PT 2021. */
    private static final Object[][] IBERIA_LOCAL = {
            {"Viveiro", 43.6622, -7.5944, 15120},
            {"Monforte de Lemos", 42.5167, -7.5167, 18933},
            {"Verín", 41.9408, -7.4358, 13956},
            {"Chaves", 41.7406, -7.4683, 38014},
            {"Ponte de Lima", 41.7672, -8.5831, 42933},
            {"Lamego", 41.0975, -7.8097, 24768},
            {"Mirandela", 41.4897, -7.1814, 21761},
            {"Covilhã", 40.2811, -7.5047, 51045},
            {"Peniche", 39.3558, -9.3811, 30262},
            {"Tomar", 39.6022, -8.4161, 37971},
            {"Elvas", 38.8814, -7.1628, 21929},
            {"Silves", 37.1892, -8.4386, 45884},
            {"Plasencia", 40.0275, -6.0908, 40132},
            {"Trujillo", 39.4608, -5.8814, 8611},
            {"Zafra", 38.4259, -6.4162, 16735},
            {"Mérida", 38.9161, -6.3438, 60225},
            {"Ciudad Rodrigo", 40.5992, -6.5289, 11750},
            {"Ponferrada", 42.5461, -6.5908, 63186},
            {"Benavente", 42.0028, -5.6781, 17309},
            {"Aguilar de Campoo", 42.7944, -4.2606, 6916},
            {"Aranda de Duero", 41.6706, -3.6892, 33956},
            {"El Burgo de Osma", 41.5864, -3.0706, 5283},
            {"Medina del Campo", 41.3081, -4.9153, 20215},
            {"Cangas de Onís", 43.3506, -5.1281, 6344},
            {"Reinosa", 43.0017, -4.1392, 8570},
            {"Oñati", 43.0328, -2.4119, 11480},
            {"Estella-Lizarra", 42.6719, -2.0319, 14317},
            {"Calahorra", 42.3050, -1.9650, 25367},
            {"Jaca", 42.5694, -0.5492, 14024},
            {"Calatayud", 41.3536, -1.6431, 20158},
            {"Alcañiz", 41.0508, -0.1336, 16505},
            {"La Seu d'Urgell", 42.3581, 1.4614, 13009},
            {"Vic", 41.9304, 2.2548, 50796},
            {"Figueres", 42.2663, 2.9616, 49689},
            {"Tortosa", 40.8125, 0.5211, 35997},
            {"Requena", 39.4883, -1.1000, 20982},
            {"Alcoy", 38.7054, -0.4744, 61468},
            {"Caravaca de la Cruz", 38.1069, -1.8631, 26126},
            {"Hellín", 38.5106, -1.7000, 30836},
            {"Valdepeñas", 38.7622, -3.3847, 30782},
            {"Sigüenza", 41.0692, -2.6394, 4911},
            {"San Lorenzo de El Escorial", 40.5936, -4.1481, 18872},
            {"Úbeda", 38.0114, -3.3714, 33588},
            {"Osuna", 37.2375, -5.1031, 17396},
            {"Aracena", 37.8939, -6.5611, 8425},
            {"Arcos de la Frontera", 36.7481, -5.8064, 31267},
            {"Ronda", 36.7461, -5.1611, 33671},
            {"Guadix", 37.3006, -3.1367, 18881},
            {"Inca", 39.7211, 2.9108, 36262},
            {"Cuevas del Almanzora", 37.2971, -1.8815, 15526}
    };

    /** Pueblos repartidos por provincias; población urbana (no metro). */
    private static final Object[][] ZA_LOCAL = {
            {"Springbok", -29.6642, 17.8856, 12790},
            {"Calvinia", -31.4753, 19.7761, 8146},
            {"Vredendal", -31.6650, 18.5064, 26081},
            {"Beaufort West", -32.3567, 22.5830, 39918},
            {"Oudtshoorn", -33.5906, 22.2028, 72337},
            {"Swellendam", -34.0225, 20.4419, 25748},
            {"Cradock", -32.1642, 25.6192, 46553},
            {"Aliwal North", -30.6936, 26.7111, 48243},
            {"Mthatha", -31.5886, 28.7844, 96114},
            {"Port Alfred", -33.5906, 26.8911, 35419},
            {"Harrismith", -28.2728, 29.1294, 30767},
            {"Ladybrand", -29.1931, 27.4569, 34249},
            {"Parys", -26.9033, 27.4572, 55641},
            {"Lichtenburg", -26.1522, 26.1597, 89943},
            {"Vryburg", -26.9564, 24.7300, 68268},
            {"Zeerust", -25.5381, 26.0750, 21916},
            {"Thohoyandou", -22.9786, 30.4647, 89427},
            {"Modimolle", -24.7000, 28.4061, 12185},
            {"Tzaneen", -23.8333, 30.1636, 14571},
            {"Phalaborwa", -23.9431, 31.1406, 13108},
            {"Barberton", -25.7883, 31.0531, 67927},
            {"eMkhondo", -27.0039, 30.8014, 34318},
            {"Standerton", -26.9339, 29.2411, 74021},
            {"Volksrust", -27.3653, 29.8819, 31386},
            {"Ladysmith", -28.5578, 29.7806, 64855},
            {"Port Shepstone", -30.7411, 30.4550, 68781},
            {"Ulundi", -28.3353, 31.4161, 31547},
            {"Dundee", -28.1650, 30.2339, 48511},
            {"Kuruman", -27.4522, 23.4325, 9093},
            {"Colesberg", -30.7200, 25.0972, 25642}
    };

    /** Comunas urbanas repartidas por la isla; censo 2018. */
    private static final Object[][] MDG_LOCAL = {
            {"Betafo", -19.8333, 46.8500, 34336},
            {"Morondava", -20.2833, 44.3167, 53510},
            {"Tôlanaro", -25.0325, 46.9833, 67284},
            {"Sambava", -14.2667, 50.1667, 84039},
            {"Maintirano", -18.0667, 44.0167, 22293},
            {"Ihosy", -22.4033, 46.1261, 39556},
            {"Maevatanana", -16.9500, 46.8333, 25928},
            {"Maroantsetra", -15.4333, 49.7390, 42529},
            {"Ambositra", -20.5167, 47.2500, 41078},
            {"Tsiroanomandidy", -18.7667, 46.0500, 44461},
            {"Ambovombe", -25.1781, 46.0872, 65402},
            {"Morombe", -21.7390, 43.3660, 22625},
            {"Mananjary", -21.2167, 48.3417, 25222},
            {"Antsohihy", -14.8800, 47.9880, 38253},
            {"Ambatondrazaka", -17.8333, 48.4167, 47649},
            {"Bekily", -24.2167, 45.3167, 20915},
            {"Miandrivazo", -19.5333, 45.4667, 20421},
            {"Vohémar", -13.3667, 50.0000, 22047},
            {"Mandritsara", -15.8333, 48.8167, 31135},
            {"Ambalavao", -21.8333, 46.9333, 43231}
    };

    private ProvincialMarketCatalog() {
    }

    @NonNull
    public static List<ProvincialMarket> resolve(@NonNull Context context, @NonNull PlayableMapRegion region) {
        PlayableMapRegion r = region != null ? region : PlayableMapRegion.IBERIA;
        List<ProvincialMarket> cached = CACHE.get(r);
        if (cached != null) {
            return cached;
        }
        List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(context.getApplicationContext(), r);
        if (parcels == null || parcels.isEmpty()) {
            return Collections.emptyList();
        }
        double[][] coords;
        String[] names;
        int[] pops;
        Object[][] locals;
        Object[][] international;
        if (r == PlayableMapRegion.SOUTH_AFRICA) {
            coords = ZA;
            names = ZA_NAMES;
            pops = ZA_POP;
            locals = ZA_LOCAL;
            international = ZA_INTERNATIONAL;
        } else if (r == PlayableMapRegion.MADAGASCAR) {
            coords = MDG;
            names = MDG_NAMES;
            pops = MDG_POP;
            locals = MDG_LOCAL;
            international = MDG_INTERNATIONAL;
        } else {
            coords = IBERIA;
            names = IBERIA_NAMES;
            pops = IBERIA_POP;
            locals = IBERIA_LOCAL;
            international = IBERIA_INTERNATIONAL;
        }
        List<ProvincialMarket> out = new ArrayList<>();
        appendMarkets(out, parcels, r, coords, names, pops, false);
        appendLocalMarkets(out, parcels, r, locals);
        appendInternationalMarkets(out, parcels, r, international);
        CACHE.put(r, out);
        return out;
    }

    @Nullable
    public static ProvincialMarket findByName(@NonNull Context context, @NonNull PlayableMapRegion region,
            @Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (ProvincialMarket m : resolve(context, region)) {
            if (name.equals(m.name)) {
                return m;
            }
        }
        return null;
    }

    @Nullable
    public static ProvincialMarket findByHexId(@NonNull Context context, @Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return null;
        }
        PlayableMapRegion r = PlayableMapRegion.fromHexId(hexId);
        for (ProvincialMarket m : resolve(context, r)) {
            if (hexId.equals(m.hexId)) {
                return m;
            }
        }
        return null;
    }

    public static boolean isMarketHex(@NonNull Context context, @Nullable String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return false;
        }
        PlayableMapRegion r = PlayableMapRegion.fromHexId(hexId);
        for (ProvincialMarket m : resolve(context, r)) {
            if (hexId.equals(m.hexId)) {
                return true;
            }
        }
        return false;
    }

    private static void appendMarkets(@NonNull List<ProvincialMarket> out, @NonNull List<HexParcel> parcels,
            @NonNull PlayableMapRegion region, @NonNull double[][] coords, @NonNull String[] names,
            @NonNull int[] pops, boolean local) {
        int n = Math.min(names.length, Math.min(coords.length, pops.length));
        for (int i = 0; i < n; i++) {
            HexParcel hex = snapParcel(parcels, coords[i][0], coords[i][1]);
            if (hex == null) {
                continue;
            }
            out.add(new ProvincialMarket(names[i], hex.id, coords[i][0], coords[i][1],
                    pops[i], local, region));
        }
    }

    private static void appendLocalMarkets(@NonNull List<ProvincialMarket> out,
            @NonNull List<HexParcel> parcels, @NonNull PlayableMapRegion region,
            @NonNull Object[][] rows) {
        for (Object[] row : rows) {
            if (row == null || row.length < 4) {
                continue;
            }
            String name = String.valueOf(row[0]);
            double lat = ((Number) row[1]).doubleValue();
            double lng = ((Number) row[2]).doubleValue();
            int pop = ((Number) row[3]).intValue();
            HexParcel hex = snapParcel(parcels, lat, lng);
            if (hex == null) {
                continue;
            }
            out.add(new ProvincialMarket(name, hex.id, lat, lng, pop, true, region));
        }
    }

    private static void appendInternationalMarkets(@NonNull List<ProvincialMarket> out,
            @NonNull List<HexParcel> parcels, @NonNull PlayableMapRegion region,
            @NonNull Object[][] rows) {
        for (Object[] row : rows) {
            if (row == null || row.length < 3) {
                continue;
            }
            String name = String.valueOf(row[0]);
            double lat = ((Number) row[1]).doubleValue();
            double lng = ((Number) row[2]).doubleValue();
            HexParcel hex = snapParcel(parcels, lat, lng);
            if (hex == null) {
                continue;
            }
            out.add(new ProvincialMarket(name, hex.id, lat, lng, 0, false, region, true));
        }
    }

    @Nullable
    private static HexParcel snapParcel(@NonNull List<HexParcel> parcels, double lat, double lon) {
        HexParcel hit = com.apiculture.simulator.domain.parcel.HexParcelResolve.findContaining(parcels, lat, lon);
        if (hit != null) {
            return hit;
        }
        HexParcel best = null;
        double bestKm = MAX_SNAP_KM;
        for (HexParcel p : parcels) {
            if (p == null) {
                continue;
            }
            double km = TranshumanceRules.haversineKm(lat, lon, p.centroidLat, p.centroidLon);
            if (km < bestKm) {
                bestKm = km;
                best = p;
            }
        }
        return best;
    }
}
