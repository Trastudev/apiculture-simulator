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
