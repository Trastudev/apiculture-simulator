package com.apiculture.simulator.domain.parcel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera parcelas hexagonales comprables en una región: rejilla sin solapes, clasificación tierra/agua.
 * El recorte exacto del polígono a la costa no está implementado; las parcelas costeras conservan el hex completo
 * y se marcan con {@link HexParcel#coastal}.
 */
public final class HexParcelGenerator {

    public static final double MIN_AREA_KM2 = 28.0;
    public static final double MAX_AREA_KM2 = 200.0;
    public static final double DEFAULT_AREA_KM2 = 70.0;

    /** Fracción mínima de tierra para parcela “interior”. */
    public static final double DEFAULT_LAND_FRACTION_INLAND = 0.90;
    /** Fracción mínima para admitir parcela costera; por debajo se descarta. */
    public static final double DEFAULT_LAND_FRACTION_MIN = 0.50;

    private final LandMask landMask;
    private final double targetAreaKm2;
    private final double sideMeters;
    private final int landSamplesPerAxis;
    private final double landFractionInland;
    private final double landFractionMin;
    /**
     * Origen fijo de la rejilla en grados; si alguno es {@link Double#NaN}, en {@link #generate} se usa el centro del
     * bbox (solo conviene para tests). En mapas el ancla debe ser constante para que las celdas no “se deslicen” al
     * mover la cámara.
     */
    private final double gridAnchorLat;
    private final double gridAnchorLon;

    public HexParcelGenerator(LandMask landMask, double targetAreaKm2) {
        this(landMask, targetAreaKm2, Double.NaN, Double.NaN);
    }

    /**
     * @param gridAnchorLat ancla latitud en grados (no NaN en producción)
     * @param gridAnchorLon ancla longitud en grados
     */
    public HexParcelGenerator(LandMask landMask, double targetAreaKm2,
                              double gridAnchorLat, double gridAnchorLon) {
        this(
                landMask,
                targetAreaKm2,
                8,
                DEFAULT_LAND_FRACTION_INLAND,
                DEFAULT_LAND_FRACTION_MIN,
                gridAnchorLat,
                gridAnchorLon
        );
    }

    public HexParcelGenerator(
            LandMask landMask,
            double targetAreaKm2,
            int landSamplesPerAxis,
            double landFractionInland,
            double landFractionMin
    ) {
        this(landMask, targetAreaKm2, landSamplesPerAxis, landFractionInland, landFractionMin,
                Double.NaN, Double.NaN);
    }

    public HexParcelGenerator(
            LandMask landMask,
            double targetAreaKm2,
            int landSamplesPerAxis,
            double landFractionInland,
            double landFractionMin,
            double gridAnchorLat,
            double gridAnchorLon
    ) {
        if (landMask == null) {
            throw new IllegalArgumentException("landMask");
        }
        this.landMask = landMask;
        double a = clamp(targetAreaKm2, MIN_AREA_KM2, MAX_AREA_KM2);
        this.targetAreaKm2 = a;
        this.sideMeters = HexGeometry.sideMetersForAreaKm2(a);
        // 0 = muestreo rápido (7 puntos); >=3 = rejilla densa.
        this.landSamplesPerAxis = landSamplesPerAxis == 0 ? 0 : Math.max(3, landSamplesPerAxis);
        this.landFractionInland = landFractionInland;
        this.landFractionMin = landFractionMin;
        this.gridAnchorLat = gridAnchorLat;
        this.gridAnchorLon = gridAnchorLon;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public double configuredSideMeters() {
        return sideMeters;
    }

    public double configuredAreaKm2() {
        return HexGeometry.areaKm2FromSide(sideMeters);
    }

    /**
     * Genera parcelas que intersectan el {@code region} (bounding box).
     *
     * @param zoneId prefijo estable para IDs (p. ej. geohash o código de tesela).
     */
    public List<HexParcel> generate(BoundingBox region, String zoneId) {
        return generate(region, zoneId, 0);
    }

    /**
     * Como {@link #generate(BoundingBox, String)} pero deja de añadir parcelas al alcanzar {@code maxParcels} (&gt; 0).
     * Ahorra CPU en el mapa cuando sólo se van a dibujar unos pocos hex visibles.
     *
     * @param maxParcels máximo de parcelas válidas; {@code ≤ 0} = sin límite.
     */
    public List<HexParcel> generate(BoundingBox region, String zoneId, int maxParcels) {
        String z = sanitizeZoneId(zoneId);
        double anchorLat = region.centerLat();
        double anchorLon = region.centerLon();
        if (!Double.isNaN(gridAnchorLat) && !Double.isNaN(gridAnchorLon)) {
            anchorLat = gridAnchorLat;
            anchorLon = gridAnchorLon;
        }
        LocalTangentPlane plane = new LocalTangentPlane(anchorLat, anchorLon);

        double xSw = plane.toX(region.minLat, region.minLon);
        double ySw = plane.toY(region.minLat, region.minLon);
        double xSe = plane.toX(region.minLat, region.maxLon);
        double ySe = plane.toY(region.minLat, region.maxLon);
        double xNw = plane.toX(region.maxLat, region.minLon);
        double yNw = plane.toY(region.maxLat, region.minLon);
        double xNe = plane.toX(region.maxLat, region.maxLon);
        double yNe = plane.toY(region.maxLat, region.maxLon);

        double xmin = min4(xSw, xSe, xNw, xNe);
        double xmax = max4(xSw, xSe, xNw, xNe);
        double ymin = min4(ySw, ySe, yNw, yNe);
        double ymax = max4(ySw, ySe, yNw, yNe);

        double margin = sideMeters * 2;
        xmin -= margin;
        xmax += margin;
        ymin -= margin;
        ymax += margin;

        double s = sideMeters;
        int[] qr = new int[4];
        axialQrRangeForBBoxPointy(xmin, xmax, ymin, ymax, s, qr);
        int qMin = qr[0];
        int qMax = qr[1];
        int rMin = qr[2];
        int rMax = qr[3];

        List<HexParcel> out = new ArrayList<>();

        for (int q = qMin; q <= qMax; q++) {
            for (int r = rMin; r <= rMax; r++) {
                HexAxialCoord axial = new HexAxialCoord(q, r);
                if (!hexAabbIntersects(xmin, xmax, ymin, ymax, axial, s, plane)) {
                    continue;
                }
                double landFrac = landSamplesPerAxis == 0
                        ? HexLandSampler.estimateLandFractionVertices(landMask, axial, s, plane)
                        : HexLandSampler.estimateLandFraction(
                                landMask, axial, s, plane, landSamplesPerAxis);
                ParcelClassification cls = classify(landFrac);
                if (cls == ParcelClassification.DISCARD) {
                    continue;
                }
                boolean coastal = cls == ParcelClassification.COASTAL;
                double[][] poly = new double[6][2];
                HexGeometry.cornersLatLon(axial, s, plane, poly);
                double[] cen = new double[2];
                HexGeometry.centroidLatLon(axial, s, plane, cen);
                double areaReported = HexGeometry.areaKm2FromSide(s);
                String id = String.format(Locale.US, "hex_%s_%d_%d", z, q, r);
                out.add(new HexParcel(id, poly, cen[0], cen[1], areaReported, coastal));
                if (maxParcels > 0 && out.size() >= maxParcels) {
                    return out;
                }
            }
        }
        return out;
    }

    private ParcelClassification classify(double landFrac) {
        if (landFrac >= landFractionInland) {
            return ParcelClassification.VALID_INLAND;
        }
        if (landFrac >= landFractionMin) {
            return ParcelClassification.COASTAL;
        }
        return ParcelClassification.DISCARD;
    }

    private static String sanitizeZoneId(String zoneId) {
        if (zoneId == null || zoneId.isEmpty()) {
            return "z";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < zoneId.length(); i++) {
            char c = zoneId.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                sb.append(c);
            }
        }
        return sb.length() > 0 ? sb.toString() : "z";
    }

    private static boolean hexAabbIntersects(
            double xmin, double xmax, double ymin, double ymax,
            HexAxialCoord axial, double sideMeters, LocalTangentPlane plane
    ) {
        double[][] corners = new double[6][2];
        HexGeometry.cornersLatLon(axial, sideMeters, plane, corners);
        double hxmin = Double.POSITIVE_INFINITY;
        double hxmax = Double.NEGATIVE_INFINITY;
        double hymin = Double.POSITIVE_INFINITY;
        double hymax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 6; i++) {
            double x = plane.toX(corners[i][0], corners[i][1]);
            double y = plane.toY(corners[i][0], corners[i][1]);
            hxmin = Math.min(hxmin, x);
            hxmax = Math.max(hxmax, x);
            hymin = Math.min(hymin, y);
            hymax = Math.max(hymax, y);
        }
        return !(hxmax < xmin || hxmin > xmax || hymax < ymin || hymin > ymax);
    }

    private static double min4(double a, double b, double c, double d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static double max4(double a, double b, double c, double d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    /**
     * Acota (q,r) enteros que pueden intersectar el rectángulo en metros — rejilla pointy-top (ver {@link HexGeometry}).
     */
    private static void axialQrRangeForBBoxPointy(
            double xmin, double xmax, double ymin, double ymax, double s, int[] outQr) {
        double qLo = Double.POSITIVE_INFINITY;
        double qHi = Double.NEGATIVE_INFINITY;
        double rLo = Double.POSITIVE_INFINITY;
        double rHi = Double.NEGATIVE_INFINITY;
        double[][] pts = {
                {xmin, ymin},
                {xmin, ymax},
                {xmax, ymin},
                {xmax, ymax}
        };
        for (double[] p : pts) {
            double x = p[0];
            double y = p[1];
            double fq = (Math.sqrt(3.0) / 3.0 * x - 1.0 / 3.0 * y) / s;
            double fr = (2.0 / 3.0 * y) / s;
            qLo = Math.min(qLo, fq);
            qHi = Math.max(qHi, fq);
            rLo = Math.min(rLo, fr);
            rHi = Math.max(rHi, fr);
        }
        outQr[0] = (int) Math.floor(qLo) - 2;
        outQr[1] = (int) Math.ceil(qHi) + 2;
        outQr[2] = (int) Math.floor(rLo) - 2;
        outQr[3] = (int) Math.ceil(rHi) + 2;
    }
}
