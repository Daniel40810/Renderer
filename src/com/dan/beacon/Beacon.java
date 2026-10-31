package com.dan.beacon;

import java.util.Locale;

/**
 * Ein Leuchtfeuer: Kennung, Sektoren, Optik und Lampe, dazu alles, was andere daraus brauchen. Reines Java ohne
 * Grafikkarte; {@code gl/BeaconGl} zeichnet Strahl, Lichtpunkt und Lichtflecken, fremde Shader lesen die Verteilung über
 * den Baustein {@code beacon.glsl}.
 * <p>
 * Zwei Optiken:
 * <ul>
 * <li>{@link Optic#DRUM}: eine feste Gürtellinse (Trommel). Sie bündelt das Licht nur in der Höhe und leuchtet rundum;
 * die Kennung entsteht durch Schalten der Lampe, die Farben durch Farbscheiben vor den Sektoren.</li>
 * <li>{@link Optic#ROTATING}: eine Drehlinse aus Ringlinsen (Bullaugen), jede bündelt in beiden Richtungen zu einem
 * Strahl. Die Blitze sind die Strahlen, die am Beobachter vorbeistreichen. Aus der Kennung folgen Zahl und Lage der
 * Linsen und die Drehzeit; die Blitzdauer folgt aus Strahlbreite und Drehgeschwindigkeit.</li>
 * </ul>
 * Die Lichtstärke folgt aus der Nenntragweite jedes Sektors ({@link Range}). Die Linse ist echt gerechnet
 * ({@link Lens}): Ihre Verteilung über die Höhe, ihre Strahlbreite und das Licht im Laternenraum kommen aus der
 * Strahlverfolgung durch die Prismenringe.
 * <p>
 * Lage: x Osten, y oben, z Süden (Nord = −z), Meter. Azimut von Nord im Uhrzeigersinn. Die Linse dreht von oben
 * gesehen im Uhrzeigersinn.
 */
public final class Beacon {

    public enum Optic { DRUM, ROTATING }

    /** Stufen: Schritte im Strahl, Größe von Verteilungs- und Streugitter. */
    public enum Quality {
        LOW(24, 256, 32, 32, 16, 24, 2), MEDIUM(32, 512, 64, 48, 24, 32, 2), HIGH(48, 512, 64, 64, 24, 40, 2), ULTRA(64, 1024, 128, 64, 32, 48, 1);
        public final int beamSteps, patternW, patternH, gridAz, gridEl, gridR, beamDownscale;
        Quality(int s, int pw, int ph, int ga, int ge, int gr, int ds) {
            beamSteps = s; patternW = pw; patternH = ph; gridAz = ga; gridEl = ge; gridR = gr; beamDownscale = ds;
        }
    }

    // ------------------------------------------------------------------ Eingänge (öffentlich, zur Laufzeit änderbar)

    public volatile String name = "Leuchtfeuer";
    /** Mitte der Linse (Lichtschwerpunkt) in m. */
    public volatile double x, y = 24, z;
    public volatile Optic optic = Optic.DRUM;
    /** Halbmesser der leuchtenden Kugel in m (Glühfaden 1–2 cm, Gasglühlicht 3–4 cm, Lichtbogen unter 1 cm). */
    public volatile double lampRadius = 0.016;
    /** Drehen (nur Drehlinse). */
    public volatile boolean turning = true;
    /** Dämmerungsschalter: an unter onLux, aus über offLux (Beleuchtungsstärke am Turm). */
    public volatile boolean automatic = true, manualOn = true;
    public volatile double ambientLux = 0, onLux = 150, offLux = 300;
    /** Halbmesser der Farbscheiben vor den Sektoren in m (innerhalb davon ist das Licht weiß). */
    public volatile double screenRadius = 1.2;
    /** Gleichmäßiger Dunst in 1/m (aus der Sicht: 3/V); Regen, Gischt und Nebel kommen über {@link Scatter}. */
    public volatile double hazePerM = 1e-4;
    /** Gewünschte Drehzeit einer Drehlinse in s (die Teilung richtet sich nach der Kennung). */
    public volatile double rotationTarget = 20;
    public volatile Quality quality = Quality.MEDIUM;
    public volatile int seed = 1885;

    private Characteristic characteristic;
    private SectorTable sectors;
    private final Lens lens;
    private Lens.Field field;
    private double fieldRadius = -1;
    private double time, rot, onFade = 1;
    private boolean lampOnState = true;
    private volatile Snapshot snapshot;

    public Beacon() { this(Lens.drum()); }

    public Beacon(Lens lens) {
        this.lens = lens;
        characteristic = Characteristic.tryParse("F W");
        sectors = SectorTable.all(LightColour.W, 10);
        snapshot = build();
    }

    // ------------------------------------------------------------------ Voreinstellungen

    /**
     * Roter Sand nach dem Verzeichnis von 1985 (F21 A): Festfeuer mit Sektoren, Trommellinse, Glühlicht. Grenzen als
     * Peilung von See: Weiß 167,5–185°, Rot 185–225°, Weiß 225–254,5°, Rot 254,5–293,5°, Weiß 293,5–312°, Rot 312–324°,
     * Leitsektor Weiß 324–328°, Grün 328–341°, Schwachlicht 341–167,5°. Tragweiten 9/7/6 sm, Schwachlicht 3 sm [G].
     */
    public static Beacon roterSand1985() {
        Beacon b = new Beacon();
        b.name = "Roter Sand 1985";
        b.optic = Optic.DRUM;
        b.lampRadius = 0.016;
        b.set("F WRG", SectorTable.parse("W 167.5-185 9, R 185-225 7, W 225-254.5 9, R 254.5-293.5 7, W 293.5-312 9, R 312-324 7, "
                + "W 324-328 9 Leitsektor, G 328-341 6, W 341-167.5 3 Schwachlicht", 9));
        return b;
    }

    /** „Sturmnacht“ (F21 A): Drehfeuer Fl W 5s, vier Ringlinsen, 20 s je Umdrehung, Nenntragweite 20 sm [G]. */
    public static Beacon stormNight() {
        Beacon b = new Beacon();
        b.name = "Sturmnacht";
        b.optic = Optic.ROTATING;
        b.lampRadius = 0.016;
        b.set("Fl W 5s", SectorTable.all(LightColour.W, 20));
        return b;
    }

    /** Fl(3) W 15s als Drehfeuer mit drei Ringlinsen in einer Gruppe, 15 s je Umdrehung, 18 sm [G]. */
    public static Beacon flash3() {
        Beacon b = new Beacon();
        b.name = "Fl(3) W 15s";
        b.optic = Optic.ROTATING;
        b.lampRadius = 0.016;
        b.rotationTarget = 15;
        b.set("Fl(3) W 15s", SectorTable.all(LightColour.W, 18));
        return b;
    }

    /**
     * Alte Weser (seit 1964): F WRG, Feuerhöhe 33 m, Tragweite 23 sm weiß. Die Sektorgrenzen hier sind geschätzt [G]:
     * weißes Leitfeuer im Fahrwasser, Rot und Grün zu beiden Seiten.
     */
    public static Beacon alteWeser() {
        Beacon b = new Beacon();
        b.name = "Alte Weser";
        b.y = 33 + 1.45;
        b.set("F WRG", SectorTable.parse("W 288-352 23, R 352-60.5 19, W 60.5-63 23 Leitsektor, G 63-125 18, R 125-288 19", 23));
        return b;
    }

    /**
     * Tegeler Plate (1966): Oc(3) WRG 12s, Feuerhöhe 20,9 m über MHW, Tragweite W 21, R 18, G 17 sm; Sektoren nach dem
     * Verzeichnis: W 14–100°, G 100–116°, W 116–119° (Leitsektor Neue Weser), R 119–123°, G 123–148,3°, W 148,3–150,3°
     * (Leitsektor Alte Weser), R 150,3–264°, W 329–340°, R 340–14°.
     */
    public static Beacon tegelerPlate() {
        Beacon b = new Beacon();
        b.name = "Tegeler Plate";
        b.y = 20.9 + 1.45;
        b.set("Oc(3) WRG 12s", SectorTable.parse("W 14-100 21, G 100-116 17, W 116-119 21 Neue Weser, R 119-123 18, G 123-148.3 17, "
                + "W 148.3-150.3 21 Alte Weser, R 150.3-264 18, W 329-340 21, R 340-14 18", 21));
        return b;
    }

    /**
     * Seit Sylt Phase 10: Kampen („Langer Christian“, 1856), Gleichtaktfeuer Iso WR 7s mit Gürtellinse (Trommel),
     * Feuerhöhe 62 m, Tragweite weiß 20 sm, rot 16 sm (Wikipedia). Der rote Sektor ersetzt seit 1975 das Quermarkenfeuer
     * Rotes Kliff; seine Grenzen sind geschätzt [G: Peilung von See 105–120°, also vom Turm nach Westnordwest über das
     * Seegebiet vor dem Roten Kliff].
     */
    public static Beacon kampen() {
        Beacon b = new Beacon();
        b.name = "Kampen";
        b.optic = Optic.DRUM;
        b.lampRadius = 0.016;
        b.y = 62;
        b.set("Iso WR 7s", SectorTable.parse("W 120-105 20, R 105-120 16 Rotes Kliff", 20));
        return b;
    }

    /**
     * Seit Sylt Phase 10: das Quermarkenfeuer Rotes Kliff wie früher (1913–1975): Festfeuer mit Sektoren F WRG,
     * Feuerhöhe 23 m, Tragweite 21 sm (Wikipedia). Als Quermarke zeigt es Schiffen auf dem Weg längs der Küste den Ort an,
     * an dem sie wenden: Grün südlich, ein schmaler weißer Leitsektor, Rot nördlich [G: Grenzen und Tragweiten von Rot und
     * Grün geschätzt]. Heute dunkel, eine Tagesmarke (F19 A).
     */
    public static Beacon rotesKliff() {
        Beacon b = new Beacon();
        b.name = "Quermarkenfeuer Rotes Kliff";
        b.optic = Optic.DRUM;
        b.lampRadius = 0.016;
        b.y = 23;
        b.set("F WRG", SectorTable.parse("G 30-87 17, W 87-93 21 Quermarke, R 93-150 16", 21));
        return b;
    }

    /**
     * Seit Timmendorf Phase 9: das Leuchtfeuer Travemünde im Dachgeschoss des Maritim (seit 1974, ersetzt den Alten
     * Leuchtturm): Blitzfeuer Fl WR 4s, Feuerhöhe 114,7 m, Tragweite weiß 19 sm, rot 15 sm (Wikipedia, visit-luebeck.com).
     * Drehlinse. Der rote Sektor ist geschätzt [G: Peilung von See 160–205°, also vom Feuer nach Nordnordwest über die
     * Steine vor dem Brodtener Ufer].
     */
    public static Beacon travemuende() {
        Beacon b = new Beacon();
        b.name = "Travemünde (Maritim)";
        b.optic = Optic.ROTATING;
        b.lampRadius = 0.016;
        b.y = 114.7;
        b.set("Fl WR 4s", SectorTable.parse("W 205-160 19, R 160-205 15 Brodtener Ufer", 19));
        return b;
    }

    /**
     * Seit Timmendorf Phase 9: Molenfeuer Travemünde Nordermole (Verzeichnis C 1362, nach OpenStreetMap): unterbrochenes
     * Feuer Oc RG 4s (1 s dunkel, 3 s Licht), Feuerhöhe 12,3 m, Tragweite 5,5 sm; Rot in Peilung 123–201°, Grün
     * 201–66°. Trommel.
     */
    public static Beacon nordermole() {
        Beacon b = new Beacon();
        b.name = "Nordermole";
        b.optic = Optic.DRUM;
        b.lampRadius = 0.008;
        b.y = 12.3;
        b.set("Oc RG 4s", SectorTable.parse("R 123-201 5.5, G 201-66 5.5", 5.5));
        return b;
    }

    /**
     * Seit Timmendorf Phase 9: Hafenfeuer Niendorf an der Nordmole [G: weder im Overpass-Export noch in den gefundenen
     * Quellen; als Festfeuer F R mit 3 sm und 5 m Feuerhöhe angenommen, das Gegenstück an der Südmole ist Grün].
     */
    public static Beacon niendorf() {
        Beacon b = new Beacon();
        b.name = "Hafenfeuer Niendorf";
        b.optic = Optic.DRUM;
        b.lampRadius = 0.005;
        b.y = 5;
        b.set("F R", SectorTable.all(LightColour.R, 3));
        return b;
    }

    /**
     * Seit Timmendorf Phase 9: der Alte Leuchtturm Travemünde (1539, Backstein, 31 m), bis 1972 in Betrieb. Historisch:
     * Festfeuer F W mit Trommel, Feuerhöhe 30 m, Tragweite 16 sm [G: Kennung und Tragweite der letzten Jahre nicht
     * gefunden]. {@code historisch = false}: dunkel wie heute (Tagesmarke, Museum).
     */
    public static Beacon alterLeuchtturm(boolean historisch) {
        Beacon b = new Beacon();
        b.name = historisch ? "Alter Leuchtturm (bis 1972)" : "Alter Leuchtturm (dunkel)";
        b.optic = Optic.DRUM;
        b.lampRadius = 0.016;
        b.y = 30;
        b.set("F W", SectorTable.all(LightColour.W, 16));
        if (!historisch) { b.automatic = false; b.manualOn = false; }
        return b;
    }

    /** Die Voreinstellungen nach Namen (für Auswahllisten). */
    public static final String[] PRESETS = {"Roter Sand 1985", "Sturmnacht (Fl W 5s)", "Fl(3) W 15s", "Alte Weser", "Tegeler Plate", "Kampen", "Rotes Kliff (bis 1975)",
            "Travemünde (Maritim)", "Nordermole Travemünde", "Hafenfeuer Niendorf", "Alter Leuchtturm (bis 1972)"};

    public static Beacon preset(int i) {
        return switch (i) {
            case 1 -> stormNight(); case 2 -> flash3(); case 3 -> alteWeser(); case 4 -> tegelerPlate(); case 5 -> kampen(); case 6 -> rotesKliff();
            case 7 -> travemuende(); case 8 -> nordermole(); case 9 -> niendorf(); case 10 -> alterLeuchtturm(true);
            default -> roterSand1985();
        };
    }

    /** Übernimmt Kennung, Sektoren und Optik eines anderen Feuers (Lage und Schalter bleiben). */
    public synchronized void copyLight(Beacon o) {
        name = o.name;
        optic = o.optic;
        lampRadius = o.lampRadius;
        rotationTarget = o.rotationTarget;
        characteristic = o.characteristic;
        sectors = o.sectors;
        snapshot = build();
    }

    // ------------------------------------------------------------------ Kennung und Sektoren

    /** Setzt Kennung und Sektoren; wirft bei Fehlern in der Kennung. */
    public synchronized void set(String characteristicText, SectorTable table) {
        try {
            characteristic = Characteristic.parse(characteristicText);
        } catch (Characteristic.ParseException e) {
            throw new IllegalArgumentException(e.getMessage() + " (Stelle " + (e.position + 1) + ")", e);
        }
        sectors = table;
        snapshot = build();
    }

    /**
     * Neue Kennung aus Text; die Sektoren bleiben. Gibt null zurück, wenn es geklappt hat, sonst die Fehlermeldung mit
     * Stelle (für das Textfeld der Oberfläche).
     */
    public synchronized String setCharacteristic(String text) {
        try {
            characteristic = Characteristic.parse(text);
            snapshot = build();
            return null;
        } catch (Characteristic.ParseException e) {
            return e.getMessage() + (e.position > 0 ? " (bei Zeichen " + (e.position + 1) + ")" : "");
        }
    }

    public synchronized void setSectors(SectorTable t) { sectors = t; snapshot = build(); }

    public Characteristic characteristic() { return characteristic; }
    public SectorTable sectors() { return sectors; }
    public Lens lens() { return lens; }

    /** Die Optik, die wirklich arbeitet: Eine Drehlinse kann nur Blitze; Fest-, Gleichtakt- und unterbrochene Feuer brauchen die Trommel. */
    public Optic effectiveOptic() { return optic == Optic.ROTATING && characteristic.flashing() ? Optic.ROTATING : Optic.DRUM; }

    /** Die Strahlverfolgung der Linse für den aktuellen Lampenhalbmesser (einmal je Halbmesser, 200 000 Strahlen). */
    public synchronized Lens.Field field() {
        if (field == null || fieldRadius != lampRadius) {
            field = lens.trace(lampRadius, 200_000, seed);
            fieldRadius = lampRadius;
        }
        return field;
    }

    // ------------------------------------------------------------------ Zeit

    public double time() { return time; }

    /** Weiter um dt Sekunden: Kennung, Drehung, Dämmerungsschalter. */
    public synchronized void step(double dt) {
        time += dt;
        Snapshot s = snapshot;
        if (turning && s.optic == Optic.ROTATING) rot = (rot + s.omega * dt) % 360;
        boolean target = automatic ? (lampOnState ? ambientLux < offLux : ambientLux < onLux) : manualOn;
        lampOnState = target;
        // Glühlicht braucht rund eine Sekunde bis zur vollen Helligkeit
        double k = dt <= 0 ? 0 : Math.min(1, dt / 0.8);
        onFade += ((target ? 1 : 0) - onFade) * k;
        if (dt > 5) onFade = target ? 1 : 0;
        snapshot = build();
    }

    /** Zeit und Drehung setzen (für Standbilder). */
    public synchronized void setTime(double t, double rotationDeg) {
        time = t;
        rot = ((rotationDeg % 360) + 360) % 360;
        snapshot = build();
    }

    /** Lampe sofort an oder aus (ohne Anlaufen). */
    public synchronized void snapOn(boolean on) {
        lampOnState = on;
        onFade = on ? 1 : 0;
        snapshot = build();
    }

    public boolean isOn() { return onFade > 0.5; }

    public Snapshot snapshot() { return snapshot; }

    // ------------------------------------------------------------------ Zustand

    /** Stufen des Höhenprofils: −90° bis +90° in 0,05°. */
    public static final double PROFILE_MIN = -90, PROFILE_STEP = 0.05;
    public static final int PROFILE_N = 3601, LUT_N = 720;
    /** Anteil des Lampenlichts, der im Glas der Linse gestreut wird (Grundleuchten). */
    public static final double LENS_GLOW = 0.01;

    /**
     * Unveränderlicher Zustand eines Augenblicks. Alle Abfragen rechnen wie die Shader: Sektortabelle in 0,5°-Schritten
     * (Peilung von See, linear, rundum wiederholt), Höhenprofil in 0,05°-Schritten (linear).
     */
    public record Snapshot(String name, double time, double x, double y, double z, Optic optic, double level, double[] tint,
                           double rotDeg, double omega, float[] panels, double gain, double flashSeconds, float[] lut, float[] profile,
                           double lumen, double lumenPerCd, Lens.Field field, double lensOuter, double lensHalfHeight, double screenRadius,
                           double panelWidth, double hazePerM, Quality quality, String characteristic, double maxCd) {

        /** Lichtstärke in cd (lineares RGB) in Richtung (wx, wy, wz) vom Feuer (Einheitsvektor). */
        public double[] intensity(double wx, double wy, double wz) {
            double az = Math.toDegrees(Math.atan2(wx, -wz));
            double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, wy))));
            return intensityAzEl(az, el);
        }

        /** Lichtstärke in cd (RGB) beim Azimut az und Höhenwinkel el (Grad). */
        public double[] intensityAzEl(double az, double el) {
            double[] c = lutAt(az + 180);
            double v = profileAt(el) * level;
            if (optic == Optic.ROTATING) v *= gain * horizontal(az);
            return new double[]{c[0] * v * tint[0], c[1] * v * tint[1], c[2] * v * tint[2]};
        }

        /** Summe der Ringlinsen-Strahlen beim Azimut az (1 in der Mitte eines Strahls). */
        public double horizontal(double az) {
            double s = 0;
            for (float p : panels) s += profileAt(wrap180(az - p - rotDeg));
            return s;
        }

        /** Sektortabelle bei der Peilung von See (Grad), wie {@code texture(lut, b/360)} mit REPEAT. */
        public double[] lutAt(double bearing) {
            double u = ((bearing % 360) + 360) % 360 / 360 * LUT_N - 0.5;
            int i = (int) Math.floor(u);
            double f = u - i;
            int a = Math.floorMod(i, LUT_N), b = Math.floorMod(i + 1, LUT_N);
            return new double[]{lut[a * 3] * (1 - f) + lut[b * 3] * f, lut[a * 3 + 1] * (1 - f) + lut[b * 3 + 1] * f, lut[a * 3 + 2] * (1 - f) + lut[b * 3 + 2] * f};
        }

        /** Höhenprofil (1 im Höchstwert) beim Winkel e (Grad), wie {@code texture(profile, …)} mit CLAMP. */
        public double profileAt(double e) {
            double u = (e - PROFILE_MIN) / PROFILE_STEP;
            if (u <= 0) return profile[0];
            if (u >= PROFILE_N - 1) return profile[PROFILE_N - 1];
            int i = (int) Math.floor(u);
            double f = u - i;
            return profile[i] * (1 - f) + profile[i + 1] * f;
        }

        /** Ob das Feuer leuchtet (über 1 %). */
        public boolean lit() { return level > 0.01; }

        /**
         * Beleuchtungsstärke in lx (RGB) im Laternenraum am Ort p mit der Flächennormale n (zum Raum hin): das Nahfeld
         * der Linse aus der Strahlverfolgung. Trommel: Fluss je Radiant Azimut, verteilt auf den Bogen r. Drehlinse:
         * jeder Strahl hält die Breite seiner Linse (gebündelt), weiche Ränder aus der Lampengröße.
         */
        public double[] nearIrradiance(double px, double py, double pz, double nx, double ny, double nz) {
            double rx = px - x, ry = py - y, rz = pz - z, r = Math.hypot(rx, rz);
            if (r < 1e-4 || !lit()) return new double[3];
            double[] fs = field.near(r, ry);
            double F = fs[0], S = fs[1];
            double ux = rx / r, uz = rz / r;
            // Flussdichte: radial F/r je m², dazu die senkrechte Komponente S·F/r
            double jx = ux, jy = S, jz = uz;
            double e = Math.max(0, -(nx * jx + ny * jy + nz * jz));
            double base;
            if (optic == Optic.ROTATING) {
                double az = Math.toDegrees(Math.atan2(rx, -rz)), blur = Math.max(0.01, r * 2 * field.lampRadius() / 0.39);
                double h = 0;
                for (float p : panels) {
                    double d = Math.toRadians(wrap180(az - p - rotDeg));
                    if (Math.cos(d) <= 0) continue;
                    double u = r * Math.sin(d);
                    h += smooth(-panelWidth / 2 - blur, -panelWidth / 2 + blur, u) * (1 - smooth(panelWidth / 2 - blur, panelWidth / 2 + blur, u));
                }
                base = F / lensOuter * h;
            } else {
                base = F / r;
            }
            double ev = base * e * lumen * level;
            double[] c = {tint[0], tint[1], tint[2]};
            if (r > screenRadius && maxCd > 0) {
                // hinter den Farbscheiben: Farbe und Dämpfung des Sektors
                double az = Math.toDegrees(Math.atan2(rx, -rz));
                double[] l = lutAt(az + 180);
                for (int k = 0; k < 3; k++) c[k] *= l[k] / maxCd;
            }
            return new double[]{ev * c[0], ev * c[1], ev * c[2]};
        }

        /** Leuchtdichte der Linse in cd/m² (RGB), von außen aus Richtung w (vom Feuer zum Auge) gesehen. */
        public double[] lensLuminance(double wx, double wy, double wz) {
            double[] i = intensity(wx, wy, wz);
            double a = 2 * lensOuter * 2 * lensHalfHeight, g = lensGlow();
            return new double[]{i[0] / a + g * tint[0], i[1] / a + g * tint[1], i[2] / a + g * tint[2]};
        }

        /**
         * Grundleuchten der Linse in cd/m²: 1 % des Lampenlichts streut im Glas, an Schlieren und Staub und lassen die
         * ganze Mantelfläche schimmern, auch abseits des Strahls.
         */
        public double lensGlow() { return lumen * level * LENS_GLOW / (Math.PI * 2 * Math.PI * lensOuter * 2 * lensHalfHeight); }

        /**
         * Strahldichte des Strahls in cd/m² (RGB) längs des Blicks o + t·d, t von 0 bis tmax, aus dem Streugitter g (darf
         * null sein: nur Dunst). Gleichwinklige Abtastung um das Feuer (Kulla und Fajardo): Mit t = Δ + D·tan θ fällt
         * das 1/r² heraus, und die Schritte liegen dort dicht, wo das Licht am hellsten ist. jitter 0..1 verschiebt die
         * Schritte (0,5 = Mitte). Genau so rechnet {@code beam.frag}.
         */
        public double[] beamRadiance(double[] o, double[] d, double tmax, ScatterGrid g, int steps, double jitter) {
            return beamRadiance(o, d, tmax, g, steps, jitter, w -> intensity(w[0], w[1], w[2]));
        }

        /** Wie oben, aber mit einer anderen Lichtquelle je Richtung (z. B. dem gelesenen Rundumbild der Grafikkarte). */
        public double[] beamRadiance(double[] o, double[] d, double tmax, ScatterGrid g, int steps, double jitter,
                                     java.util.function.Function<double[], double[]> source) {
            double[] L = new double[3];
            if (!lit()) return L;
            if (optic != Optic.ROTATING) {
                segment(o, d, 0, tmax, g, steps, jitter, source, L);
                return L;
            }
            // Drehlinse: Licht gibt es nur in den schmalen Strahlen. Jeder Strahl bekommt eigene Schritte auf dem Stück
            // des Blicks, das in seinem Keil liegt (sonst fielen Strahlen von 5° Breite zwischen die Schritte).
            int m = panelSteps(steps);
            for (float p : panels) {
                double[] iv = wedge(o, d, p + rotDeg, wedgeDeg(), tmax);
                if (iv != null) segment(o, d, iv[0], iv[1], g, m, jitter, source, L);
            }
            return L;
        }

        /** Halbe Breite des Keils um einen Strahl, in dem gerechnet wird (Grad): die volle Halbwertsbreite. */
        public double wedgeDeg() { return field.fwhmDeg(); }

        /** Schritte je Strahl einer Drehlinse. */
        public static int panelSteps(int steps) { return Math.max(8, steps / 3); }

        /**
         * Das Stück [t1, t2] des Blicks o + t·d (0 ≤ t ≤ tmax), dessen Grundriss im Keil um den Azimut alpha ± w (Grad)
         * vom Feuer liegt; null, wenn der Blick den Keil nicht trifft. Der Keil ist schmaler als 180°, also konvex: Es
         * gibt höchstens ein Stück. Genau so in beam.frag.
         */
        public double[] wedge(double[] o, double[] d, double alpha, double w, double tmax) {
            double ax = o[0] - x, az = o[2] - z, dx = d[0], dz = d[2];
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double t : new double[]{0, tmax}) {
                if (inWedge(ax + dx * t, az + dz * t, alpha, w)) { lo = Math.min(lo, t); hi = Math.max(hi, t); }
            }
            for (int k = -1; k <= 1; k += 2) {
                double b = Math.toRadians(alpha + k * w), ux = Math.sin(b), uz = -Math.cos(b);
                double det = ux * dz - dx * uz;
                if (Math.abs(det) < 1e-12) continue;
                double t = (ax * uz - ux * az) / det, s = (dz * ax - dx * az) / det;
                if (s >= 0 && t >= 0 && t <= tmax) { lo = Math.min(lo, t); hi = Math.max(hi, t); }
            }
            return hi > lo ? new double[]{lo, hi} : null;
        }

        static boolean inWedge(double px, double pz, double alpha, double w) {
            if (px * px + pz * pz < 1e-12) return true;
            return Math.abs(wrap180(Math.toDegrees(Math.atan2(px, -pz)) - alpha)) <= w;
        }

        /** Gleichwinklige Schritte auf t1..t2, aufaddiert in L. */
        void segment(double[] o, double[] d, double t1, double t2, ScatterGrid g, int steps, double jitter,
                     java.util.function.Function<double[], double[]> source, double[] L) {
            double lx = x - o[0], ly = y - o[1], lz = z - o[2];
            double delta = lx * d[0] + ly * d[1] + lz * d[2];
            double px = o[0] + d[0] * delta - x, py = o[1] + d[1] * delta - y, pz = o[2] + d[2] * delta - z;
            double D = Math.max(0.05, Math.sqrt(px * px + py * py + pz * pz));
            double ta = Math.atan((t1 - delta) / D), tb = Math.atan((t2 - delta) / D);
            double dth = (tb - ta) / steps;
            for (int i = 0; i < steps; i++) {
                double th = ta + (i + jitter) * dth;
                double t = delta + D * Math.tan(th);
                double qx = o[0] + d[0] * t - x, qy = o[1] + d[1] * t - y, qz = o[2] + d[2] * t - z;
                double r = Math.max(1e-3, Math.sqrt(qx * qx + qy * qy + qz * qz));
                double wx = qx / r, wy = qy / r, wz = qz / r;
                double sig = hazePerM + (g != null ? g.sample(wx, wy, wz, r) : 0);
                double[] I = source.apply(new double[]{wx, wy, wz});
                double cs = -(wx * d[0] + wy * d[1] + wz * d[2]);
                double tr = Math.exp(-sig * (r + Math.max(0, t)));
                double k = sig * phase(cs) * tr * dth / D;
                L[0] += I[0] * k;
                L[1] += I[1] * k;
                L[2] += I[2] * k;
            }
        }

        /** Beleuchtungsstärke in lx (RGB) am Ort e (Auge), mit Dunst; für Lichtpunkt und Anzeige. */
        public double[] illuminanceAt(double ex, double ey, double ez, double sigma) {
            double qx = ex - x, qy = ey - y, qz = ez - z, r = Math.max(0.1, Math.sqrt(qx * qx + qy * qy + qz * qz));
            double[] i = intensity(qx / r, qy / r, qz / r);
            double k = Math.exp(-sigma * r) / (r * r);
            return new double[]{i[0] * k, i[1] * k, i[2] * k};
        }
    }

    /** Streufunktion je sr für Wassertropfen und Dunst: 0,85 · HG(0,8) + 0,15 · HG(−0,3). */
    public static double phase(double cosTheta) { return 0.85 * hg(cosTheta, 0.8) + 0.15 * hg(cosTheta, -0.3); }

    static double hg(double c, double g) { return (1 - g * g) / (4 * Math.PI * Math.pow(1 + g * g - 2 * g * c, 1.5)); }

    static double wrap180(double a) { a = ((a + 180) % 360 + 360) % 360; return a - 180; }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Baut den Zustand aus Eingängen, Kennung, Optik und Linse. */
    private Snapshot build() {
        Lens.Field f = field();
        Characteristic ch = characteristic;
        Optic op = effectiveOptic();
        // Höhenprofil (1 im Höchstwert)
        float[] prof = new float[PROFILE_N];
        for (int i = 0; i < PROFILE_N; i++) prof[i] = (float) f.relative(PROFILE_MIN + i * PROFILE_STEP);
        // Sektortabelle in cd (RGB) je 0,5° Peilung; bei Wechselfeuern ohne Farbe (die kommt mit tint)
        float[] lut = new float[LUT_N * 3];
        double maxCd = 0;
        for (int i = 0; i < LUT_N; i++) {
            double b = (i + 0.5) * 360.0 / LUT_N;
            double[] c = sectors.rgbAt(b, Range::intensityForNominal);
            if (ch.alternating) {
                double l = LightColour.luminance(c);
                c = new double[]{l, l, l};
            }
            for (int k = 0; k < 3; k++) lut[i * 3 + k] = (float) c[k];
            maxCd = Math.max(maxCd, LightColour.luminance(c));
        }
        double[] tint = {1, 1, 1};
        if (ch.alternating) tint = ch.colourAt(time).rgb();
        double level = onFade;
        float[] panels = new float[0];
        double omega = 0, gain = 1, flash = ch.flashing() ? ch.longestOn() : 0, width = 0;
        if (op == Optic.ROTATING) {
            double p = ch.period;
            int k = Math.max(1, (int) Math.round(rotationTarget / p));
            int n = 0;
            for (int i = 0; i < ch.on.length; i += 2) if (ch.on[i] < p - 1e-9) n++;
            panels = new float[k * n];
            double rotPeriod = k * p;
            omega = 360 / rotPeriod;
            int j = 0;
            for (int r = 0; r < k; r++)
                for (int i = 0; i < ch.on.length && j < panels.length; i += 2) {
                    if (ch.on[i] >= p - 1e-9) continue;
                    double mid = (ch.on[i] + Math.min(ch.on[i + 1], p)) / 2;
                    panels[j++] = (float) (-(r * p + mid) * omega);
                }
            flash = f.fwhmDeg() / omega;
            gain = (0.2 + flash) / flash;
            width = 2 * Math.PI * lens.outer / Math.max(1, panels.length);
        } else {
            level *= ch.level(time);
        }
        // Lampe: Lichtstrom aus der stärksten Richtung. Trommel: I = Φ · (cd/lm); Drehlinse: eine Ringlinse fasst den
        // Fluss ihres Teilkreises (2π/N) und bündelt ihn auf die Strahlbreite.
        double peakCd = maxCd * (op == Optic.ROTATING ? gain : 1);
        double perLm = f.peakCdPerLm();
        if (op == Optic.ROTATING) perLm *= (2 * Math.PI / Math.max(1, panels.length)) / (Math.toRadians(f.fwhmDeg()) * 1.064);
        double lumen = peakCd / Math.max(1e-9, perLm);
        Characteristic shown = ch;
        String text = shown.text + (op != optic ? " (Trommel: " + (ch.flashing() ? "" : "keine Blitze") + ")" : "");
        return new Snapshot(name, time, x, y, z, op, level, tint, rot, omega, panels, gain, flash, lut, prof, lumen, perLm, f,
                lens.outer, lens.halfHeight, screenRadius, width, hazePerM, quality, text, maxCd);
    }

    /** „Roter Sand 1985 · F WRG · Trommel · 835 cd · Tragweite 9 sm“ */
    public String describe() {
        Snapshot s = snapshot;
        String opt = s.optic == Optic.ROTATING
                ? String.format(Locale.GERMAN, "Drehlinse %d Strahlen, %.0f s je Umdrehung, Blitz %.2f s", s.panels.length, 360 / s.omega, s.flashSeconds)
                : "Trommellinse";
        double cd = s.maxCd * (s.optic == Optic.ROTATING ? s.gain : 1);
        return String.format(Locale.GERMAN, "%s · %s · %s · %,.0f cd · Nenntragweite %.0f sm · Lampe %.0f lm", s.name, characteristic.text, opt, cd,
                sectors.maxRangeNm(), s.lumen);
    }
}
