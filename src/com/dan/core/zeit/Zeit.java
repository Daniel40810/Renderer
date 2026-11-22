package com.dan.core.zeit;

import com.dan.core.astro.FAstroTime;

import java.time.Instant;

/**
 * Zeitskalen (Werkbuch HalligHooge Teil A, 5.2): Die Hauptuhr zählt UTC ({@link Instant}); die Ephemeriden brauchen
 * TT (dynamische Zeit). TT = UTC + (TAI − UTC) + 32,184 s; seit 2017 sind das 69,184 s. UT1 − UTC (unter 0,9 s) bleibt
 * unberücksichtigt: Für die Sternzeit gilt UTC als Weltzeit, das sind höchstens 0,004° im Stundenwinkel.
 */
public final class Zeit {
    /** Schaltsekunden: Beginn (Sekunden seit 1970 UTC) und TAI − UTC ab diesem Tag. Stand: keine neue seit 2017. */
    private static final long[][] SCHALT = {
            {tag(1972, 1), 10}, {tag(1972, 7), 11}, {tag(1973, 1), 12}, {tag(1974, 1), 13}, {tag(1975, 1), 14}, {tag(1976, 1), 15},
            {tag(1977, 1), 16}, {tag(1978, 1), 17}, {tag(1979, 1), 18}, {tag(1980, 1), 19}, {tag(1981, 7), 20}, {tag(1982, 7), 21},
            {tag(1983, 7), 22}, {tag(1985, 7), 23}, {tag(1988, 1), 24}, {tag(1990, 1), 25}, {tag(1991, 1), 26}, {tag(1992, 7), 27},
            {tag(1993, 7), 28}, {tag(1994, 7), 29}, {tag(1996, 1), 30}, {tag(1997, 7), 31}, {tag(1999, 1), 32}, {tag(2006, 1), 33},
            {tag(2009, 1), 34}, {tag(2012, 7), 35}, {tag(2015, 7), 36}, {tag(2017, 1), 37}};

    private static long tag(int jahr, int monat) { return java.time.LocalDate.of(jahr, monat, 1).toEpochDay() * 86_400L; }

    /** TAI − UTC in Sekunden; vor 1972 nicht definiert (NaN). */
    public static double taiMinusUtc(Instant t) {
        long s = t.getEpochSecond();
        if (s < SCHALT[0][0]) return Double.NaN;
        int i = SCHALT.length - 1;
        while (SCHALT[i][0] > s) i--;
        return SCHALT[i][1];
    }

    /** TT − UTC in Sekunden; vor 1972 die Näherung ΔT = TT − UT aus {@link FAstroTime}. */
    public static double ttMinusUtc(Instant t) {
        double d = taiMinusUtc(t);
        if (!Double.isNaN(d)) return d + 32.184;
        double jd = jd(t);
        return FAstroTime.deltaTDays(jd) * 86_400;
    }

    /** Julianisches Datum in Weltzeit (UTC als UT). */
    public static double jd(Instant t) { return 2_440_587.5 + (t.getEpochSecond() + t.getNano() * 1e-9) / 86_400; }

    /** Julianisches Datum in dynamischer Zeit (TT), für die Ephemeriden. */
    public static double jde(Instant t) { return jd(t) + ttMinusUtc(t) / 86_400; }

    /** Julianische Jahrhunderte seit J2000,0 in TT. */
    public static double jahrhunderte(Instant t) { return (jde(t) - 2_451_545.0) / 36_525; }

    /** Stunden seit 1970 (UTC) – die Zeitachse von {@code com.dan.tide}. */
    public static double stundenSeit1970(Instant t) { return (t.getEpochSecond() + t.getNano() * 1e-9) / 3600; }

    /** Zeitpunkt zu Sekunden seit 1970 (UTC). */
    public static Instant ausSekunden(double sekundenSeit1970) {
        long s = (long) Math.floor(sekundenSeit1970);
        return Instant.ofEpochSecond(s, Math.round((sekundenSeit1970 - s) * 1e9));
    }

    private Zeit() { }
}
