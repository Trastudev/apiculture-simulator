package com.apiculture.simulator.domain.game;

import com.apiculture.simulator.domain.parcel.HexParcel;

/**
 * Zonas climáticas de la península ibérica (y Portugal) a partir de lat/lon y altitud.
 * No sustituye Open-Meteo día a día: desplaza la floración y etiqueta el emplazamiento.
 */
public enum IberianClimateZone {
    ATLANTIC,
    MOUNTAIN,
    MEDITERRANEAN,
    SOUTH,
    CONTINENTAL;

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
            case CONTINENTAL:
            default:
                return "Continental";
        }
    }

    public int bloomShiftDays() {
        switch (this) {
            case ATLANTIC:
                return GameBalanceConfig.bloomShiftAtlantic;
            case MOUNTAIN:
                return GameBalanceConfig.bloomShiftMountain;
            case MEDITERRANEAN:
                return GameBalanceConfig.bloomShiftMediterranean;
            case SOUTH:
                return GameBalanceConfig.bloomShiftSouth;
            case CONTINENTAL:
            default:
                return GameBalanceConfig.bloomShiftContinental;
        }
    }

    public String bloomHintEs() {
        int s = bloomShiftDays();
        if (s < 0) {
            return "floración " + (-s) + " d antes";
        }
        if (s > 0) {
            return "floración +" + s + " d";
        }
        return "floración en fechas medias";
    }

    public String dialogLineEs() {
        return "Clima: " + labelEs() + " (" + bloomHintEs() + ")";
    }

    /**
     * Néctar extra o de menos según estación: montaña florece en verano y se cierra
     * en invierno; costa y sur mantienen más mielada invernal.
     */
    public double nectarSeasonMultiplier(int dayOfYear) {
        return GameBalanceConfig.nectarSeasonMultiplier(this, Season.fromDayOfYear(dayOfYear));
    }

    /**
     * Recorte de puesta por estación: fuerte en el sur en verano, nulo en alta montaña.
     */
    public double layingSeasonMultiplier(int dayOfYear) {
        return GameBalanceConfig.layingSeasonMultiplier(this, Season.fromDayOfYear(dayOfYear));
    }

    public static IberianClimateZone forParcel(HexParcel parcel) {
        if (parcel == null) {
            return CONTINENTAL;
        }
        int elev = parcel.maxElevationMeters != null ? parcel.maxElevationMeters : -1;
        return fromLatLonElev(parcel.centroidLat, parcel.centroidLon, elev);
    }

    /**
     * Clasificación jugable alineada con el clima real: cornisa húmeda, meseta, levante, valle del
     * Guadalquivir y alta montaña (sierras con cumbres &gt; 2000 m, o altitud medida ≥ umbral).
     */
    public static IberianClimateZone fromLatLonElev(double lat, double lon, int elevM) {
        if (lat < 20.0) {
            return CONTINENTAL;
        }
        int elev = elevM;
        if (elev >= GameBalanceConfig.mountainMinM) {
            return MOUNTAIN;
        }
        if (isHighSierraOver2000m(lat, lon)) {
            return MOUNTAIN;
        }
        if (lat >= 41.7 && lon <= -7.0) {
            return ATLANTIC;
        }
        if (lat >= 42.6 && lon <= -1.4 && lon >= -9.5 && elev < 800) {
            return ATLANTIC;
        }
        if (lat <= 38.35 && lon >= -8.9 && lon <= -3.2 && elev < 700) {
            return SOUTH;
        }
        if (lat <= 37.8 && lon >= -8.9 && lon <= -7.2) {
            return SOUTH;
        }
        if (lon >= -0.8 || (lon >= -1.6 && lat <= 41.0)) {
            return MEDITERRANEAN;
        }
        if (lat <= 38.2 && lon >= -2.4) {
            return MEDITERRANEAN;
        }
        return CONTINENTAL;
    }

    /**
     * Núcleos de sierras ibéricas con cumbres por encima de 2000 m (sin DEM en el overlay).
     * Cajas ajustadas a la cresta, no a toda la comarca.
     */
    public static boolean isHighSierraOver2000m(double lat, double lon) {
        // Pirineos occidentales (Collarada, Bisaurín, Aspe)
        if (inBox(lat, lon, 42.70, 43.02, -0.95, -0.18)) {
            return true;
        }
        // Pirineos centrales (Monte Perdido, Posets, Aneto, Maladeta)
        if (inBox(lat, lon, 42.48, 42.88, -0.18, 0.95)) {
            return true;
        }
        // Pirineos orientales y Prepirineo catalán (Puigmal, Cadí-Moixeró, Ribes de Freser, Serra Cavallera)
        if (inBox(lat, lon, 42.18, 42.90, 0.90, 2.42)) {
            return true;
        }
        // Picos de Europa
        if (inBox(lat, lon, 43.10, 43.28, -5.08, -4.68)) {
            return true;
        }
        // Montaña palentina (Curavacas, Espigüete)
        if (inBox(lat, lon, 42.96, 43.12, -4.88, -4.52)) {
            return true;
        }
        // Peña Ubiña
        if (inBox(lat, lon, 42.98, 43.08, -6.00, -5.86)) {
            return true;
        }
        // Sierra Nevada (Mulhacén, Veleta, Alcazaba)
        if (inBox(lat, lon, 36.92, 37.20, -3.55, -2.70)) {
            return true;
        }
        // Gredos (Almanzor)
        if (inBox(lat, lon, 40.18, 40.38, -5.40, -4.98)) {
            return true;
        }
        // Béjar / Candelario (Calvitero)
        if (inBox(lat, lon, 40.26, 40.38, -5.82, -5.60)) {
            return true;
        }
        // Guadarrama / Peñalara
        if (inBox(lat, lon, 40.80, 40.92, -4.02, -3.76)) {
            return true;
        }
        // Moncayo
        if (inBox(lat, lon, 41.72, 41.83, -1.92, -1.74)) {
            return true;
        }
        // Urbión / Cebollera
        if (inBox(lat, lon, 41.95, 42.06, -2.94, -2.70)) {
            return true;
        }
        // Sierra de la Demanda (San Lorenzo)
        if (inBox(lat, lon, 42.18, 42.30, -3.10, -2.88)) {
            return true;
        }
        // Mágina
        if (inBox(lat, lon, 37.70, 37.80, -3.54, -3.38)) {
            return true;
        }
        // Sierra de Baza
        if (inBox(lat, lon, 37.34, 37.50, -2.92, -2.66)) {
            return true;
        }
        // Filabres / Calar Alto
        if (inBox(lat, lon, 37.16, 37.32, -2.62, -2.38)) {
            return true;
        }
        // Cazorla–Segura (Empanadas, Cabrilla)
        if (inBox(lat, lon, 37.92, 38.18, -2.92, -2.48)) {
            return true;
        }
        // Gúdar (Peñarroya)
        if (inBox(lat, lon, 40.34, 40.46, -0.74, -0.50)) {
            return true;
        }
        // Javalambre
        if (inBox(lat, lon, 40.06, 40.18, -1.10, -0.92)) {
            return true;
        }
        return false;
    }

    private static boolean inBox(double lat, double lon,
                                 double latMin, double latMax, double lonMin, double lonMax) {
        return lat >= latMin && lat <= latMax && lon >= lonMin && lon <= lonMax;
    }

    public static IberianClimateZone forHive(Double lat, Double lon, int elevM) {
        if (lat == null || lon == null || Double.isNaN(lat) || Double.isNaN(lon)) {
            return CONTINENTAL;
        }
        return fromLatLonElev(lat, lon, elevM);
    }
}
