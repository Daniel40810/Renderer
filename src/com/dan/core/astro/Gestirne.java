package com.dan.core.astro;

import com.dan.core.raum.Erde;
import com.dan.core.zeit.Zeit;

import java.time.Instant;

/**
 * Ort und Abstand von Sonne und Mond zu einem Zeitpunkt (Werkbuch HalligHooge Teil A, 5.2), aus den Ephemeriden von
 * {@link FSun} (VSOP87) und {@link FMoonPosition} (ELP-2000/82 nach Meeus). Daraus folgen
 * <ul>
 * <li>das <b>Gezeitenpotential</b> für {@code physics.tide} ({@link Stand#potential}, {@link Stand#gleichgewicht}):
 * Glieder zweiten und dritten Grades für beide Gestirne, ohne Vorgabe von Partialtiden,</li>
 * <li>der <b>Sonnenstand</b> für {@code atmos} und {@code render.air} ({@link Stand#sonne}, {@link Stand#sonneE}), die
 * Felder {@code sun.dir} und {@code sun.E} des Katalogs,</li>
 * <li>die Richtung zum Mond mit Parallaxe ({@link Stand#mond}).</li>
 * </ul>
 * Ein {@link Stand} wird einmal je Zeitpunkt gerechnet (rund 400 Reihenglieder) und dann für beliebig viele Orte
 * abgefragt (ein Skalarprodukt je Gestirn).
 */
public final class Gestirne {
    /** Astronomische Einheit (m); GM von Sonne und Mond (m³/s²); Solarkonstante (W/m² in 1 AE). */
    public static final double AE = 149_597_870_700.0, GM_SONNE = 1.32712440018e20, GM_MOND = 4.9028695e12, SOLARKONSTANTE = 1361.0;
    /** Lovesche Zahlen der festen Erde; der Faktor (1 + k₂ − h₂) ≈ 0,69 mindert das Potential, das auf das Meer wirkt. */
    public static final double LOVE_K2 = 0.302, LOVE_H2 = 0.609, LOVE = 1 + LOVE_K2 - LOVE_H2;

    /** Richtung zu einem Gestirn am Ort: Einheitsvektor (Ost, Nord, oben), Höhe und Azimut (Grad, von Nord über Ost), Abstand (m). */
    public record Richtung(double ost, double nord, double oben, double hoeheGrad, double azimutGrad, double abstand) { }

    /** Sonne und Mond zu einem Zeitpunkt, erdfest. */
    public static final class Stand {
        public final Instant zeit;
        /** Julianisches Datum (TT) und scheinbare Sternzeit zu Greenwich (rad). */
        public final double jde, sternzeit;
        /** Rektaszension, Deklination (rad) und Abstand vom Erdmittelpunkt (m). */
        public final double sonneRa, sonneDek, sonneAbstand, mondRa, mondDek, mondAbstand;
        /** Einheitsvektoren zum Gestirn im erdfesten System (x zum Nullmeridian am Äquator, z zum Nordpol). */
        private final double[] es, em;

        Stand(Instant zeit) {
            this.zeit = zeit;
            jde = Zeit.jde(zeit);
            sternzeit = Math.toRadians(FAstroTime.apparentSiderealTime(Zeit.jd(zeit)));
            FSun s = FSun.at(jde);
            FMoonPosition m = FMoonPosition.at(jde);
            sonneRa = Math.toRadians(s.rightAscension);
            sonneDek = Math.toRadians(s.declination);
            sonneAbstand = s.distanceAu * AE;
            mondRa = Math.toRadians(m.rightAscension);
            mondDek = Math.toRadians(m.declination);
            mondAbstand = m.distanceKm * 1000;
            es = erdfest(sonneRa, sonneDek);
            em = erdfest(mondRa, mondDek);
        }

        private double[] erdfest(double ra, double dek) {
            double l = ra - sternzeit;
            return new double[] {Math.cos(dek) * Math.cos(l), Math.cos(dek) * Math.sin(l), Math.sin(dek)};
        }

        /** Länge (Grad, −180..180) des Orts, über dem die Sonne im Zenit steht; die Breite ist die Deklination. */
        public double sonneLaengeGrad() { return FAngle.norm180(Math.toDegrees(sonneRa - sternzeit)); }

        /** Länge (Grad, −180..180) des Orts, über dem der Mond im Zenit steht. */
        public double mondLaengeGrad() { return FAngle.norm180(Math.toDegrees(mondRa - sternzeit)); }

        /**
         * Gezeitenpotential (m²/s²) am Ort auf der Erdoberfläche: Σ GM/d · [(R/d)² P₂(cos ψ) + (R/d)³ P₃(cos ψ)] über
         * Mond und Sonne, ψ der geozentrische Winkel zum Gestirn. Ohne den Faktor der festen Erde ({@link #LOVE}).
         */
        public double potential(double lonGrad, double latGrad) {
            double l = Math.toRadians(lonGrad), p = Math.toRadians(Erde.geozentrisch(latGrad)), c = Math.cos(p);
            double x = c * Math.cos(l), y = c * Math.sin(l), z = Math.sin(p);
            return glied(GM_MOND, mondAbstand, x * em[0] + y * em[1] + z * em[2]) + glied(GM_SONNE, sonneAbstand, x * es[0] + y * es[1] + z * es[2]);
        }

        private static double glied(double gm, double d, double c) {
            double q = Erde.R / d;
            return gm / d * q * q * ((3 * c * c - 1) / 2 + q * (5 * c * c * c - 3 * c) / 2);
        }

        /** Gleichgewichtstide (m) am Ort: Potential durch die Normschwere, ohne den Faktor der festen Erde. */
        public double gleichgewicht(double lonGrad, double latGrad) { return potential(lonGrad, latGrad) / Erde.G0; }

        /** Richtung zur Sonne am Ort (ohne Refraktion; die rechnet {@code atmos}). */
        public Richtung sonne(double lonGrad, double latGrad) { return richtung(es, sonneAbstand, lonGrad, latGrad); }

        /** Richtung zum Mond am Ort, mit Parallaxe (bis 1°), ohne Refraktion. */
        public Richtung mond(double lonGrad, double latGrad) { return richtung(em, mondAbstand, lonGrad, latGrad); }

        /** Bestrahlungsstärke der Sonne oberhalb der Atmosphäre, senkrecht zum Strahl (W/m²): {@code sun.E}. */
        public double sonneE() {
            double r = sonneAbstand / AE;
            return SOLARKONSTANTE / (r * r);
        }

        private static Richtung richtung(double[] e, double d, double lonGrad, double latGrad) {
            double[] o = Erde.ecef(lonGrad, latGrad, 0);
            double dx = e[0] * d - o[0], dy = e[1] * d - o[1], dz = e[2] * d - o[2], n = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double l = Math.toRadians(lonGrad), p = Math.toRadians(latGrad), sl = Math.sin(l), cl = Math.cos(l), sp = Math.sin(p), cp = Math.cos(p);
            double ost = (-sl * dx + cl * dy) / n, nord = (-sp * cl * dx - sp * sl * dy + cp * dz) / n, oben = (cp * cl * dx + cp * sl * dy + sp * dz) / n;
            return new Richtung(ost, nord, oben, Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, oben)))),
                    FAngle.norm360(Math.toDegrees(Math.atan2(ost, nord))), n);
        }
    }

    /** Sonne und Mond zum Zeitpunkt t (UTC). */
    public static Stand bei(Instant t) { return new Stand(t); }

    private Gestirne() { }
}
