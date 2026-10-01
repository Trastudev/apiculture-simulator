package com.apiculture.simulator.domain.parcel;

import java.util.Objects;

/** Coordenadas axiales (q, r) de una rejilla hexagonal. */
public final class HexAxialCoord {
    public final int q;
    public final int r;

    public HexAxialCoord(int q, int r) {
        this.q = q;
        this.r = r;
    }

    public HexAxialCoord[] neighbors() {
        return new HexAxialCoord[]{
                new HexAxialCoord(q + 1, r),
                new HexAxialCoord(q + 1, r - 1),
                new HexAxialCoord(q, r - 1),
                new HexAxialCoord(q - 1, r),
                new HexAxialCoord(q - 1, r + 1),
                new HexAxialCoord(q, r + 1)
        };
    }

    /** Distancia axial (pasos de hexágono) hasta {@code other}. */
    public int distanceTo(HexAxialCoord other) {
        if (other == null) {
            return Integer.MAX_VALUE;
        }
        return (Math.abs(q - other.q) + Math.abs(r - other.r) + Math.abs((q + r) - (other.q + other.r))) / 2;
    }

    public static int distance(HexAxialCoord a, HexAxialCoord b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        return a.distanceTo(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HexAxialCoord that = (HexAxialCoord) o;
        return q == that.q && r == that.r;
    }

    @Override
    public int hashCode() {
        return Objects.hash(q, r);
    }

    @Override
    public String toString() {
        return "HexAxialCoord(" + q + "," + r + ")";
    }
}
