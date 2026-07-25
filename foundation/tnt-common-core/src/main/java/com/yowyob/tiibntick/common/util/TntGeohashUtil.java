package com.yowyob.tiibntick.common.util;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Geohash encoding utility for tile-based real-time fan-out (Chantier G — Link BFF +
 * temps réel par tuiles geohash, see docs/audits/remediation/phase-1-hardening.md).
 *
 * <p>Self-contained base32 geohash implementation — no external dependency. The
 * algorithm is the standard interleaved-bit geohash (as used by geohash.org): each
 * character narrows a lat/lng bounding box by one more bit, alternating longitude and
 * latitude, encoded 5 bits at a time in the {@link #BASE32} alphabet.
 *
 * <p>Precision guide (matches the Chantier G spec, ~1–5 km tiles):
 * <ul>
 *   <li>precision 5 → ~4.9km x 4.9km cell</li>
 *   <li>precision 6 → ~1.2km x 0.61km cell</li>
 * </ul>
 *
 * Author: MANFOUO Braun
 */
public final class TntGeohashUtil {

    /** Default tile precision for Link real-time fan-out (Chantier G spec: precision 5–6). */
    public static final int DEFAULT_PRECISION = 6;

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private TntGeohashUtil() {
    }

    /**
     * Encodes a coordinate into a geohash string of the given precision.
     *
     * @param latitude  in [-90, 90]
     * @param longitude in [-180, 180]
     * @param precision number of base32 characters (1-12)
     * @return the geohash string, e.g. {@code "u4pruy"}
     */
    public static String encode(double latitude, double longitude, int precision) {
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("precision must be between 1 and 12, got " + precision);
        }
        double minLat = -90.0, maxLat = 90.0;
        double minLng = -180.0, maxLng = 180.0;
        StringBuilder geohash = new StringBuilder(precision);
        boolean evenBit = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            if (evenBit) {
                double mid = (minLng + maxLng) / 2.0;
                if (longitude >= mid) {
                    ch |= (1 << (4 - bit));
                    minLng = mid;
                } else {
                    maxLng = mid;
                }
            } else {
                double mid = (minLat + maxLat) / 2.0;
                if (latitude >= mid) {
                    ch |= (1 << (4 - bit));
                    minLat = mid;
                } else {
                    maxLat = mid;
                }
            }
            evenBit = !evenBit;
            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return geohash.toString();
    }

    /** Encodes at {@link #DEFAULT_PRECISION}. */
    public static String encode(double latitude, double longitude) {
        return encode(latitude, longitude, DEFAULT_PRECISION);
    }

    /**
     * Returns the set of geohash tiles (at the given precision) covering a viewport bounding
     * box, including the box's edges. Used by clients/BFF to compute which tile channels to
     * subscribe to for a given map viewport.
     *
     * <p>Samples the box on a grid narrow enough to not skip any tile it overlaps — safe for
     * the Link map's viewport sizes (a few tiles across at precision 5-6) but not intended for
     * continent-scale boxes.
     *
     * @param minLat southern edge
     * @param minLng western edge
     * @param maxLat northern edge
     * @param maxLng eastern edge
     * @param precision tile precision
     * @return the set of geohash tile codes covering the box
     */
    public static Set<String> coverBoundingBox(double minLat, double minLng, double maxLat, double maxLng, int precision) {
        if (minLat > maxLat || minLng > maxLng) {
            throw new IllegalArgumentException("min bound must not exceed max bound");
        }
        double latStep = tileLatSpan(precision) / 2.0;
        double lngStep = tileLngSpan(precision) / 2.0;
        Set<String> tiles = new LinkedHashSet<>();
        for (double lat = minLat; lat <= maxLat + latStep; lat += latStep) {
            for (double lng = minLng; lng <= maxLng + lngStep; lng += lngStep) {
                tiles.add(encode(Math.min(lat, 90.0), Math.min(lng, 180.0), precision));
            }
        }
        return tiles;
    }

    private static double tileLatSpan(int precision) {
        // Latitude gets one bit every other character → 5*precision/2 latitude bits total.
        int latBits = (5 * precision) / 2;
        return 180.0 / Math.pow(2, latBits);
    }

    private static double tileLngSpan(int precision) {
        int lngBits = (5 * precision + 1) / 2;
        return 360.0 / Math.pow(2, lngBits);
    }
}
