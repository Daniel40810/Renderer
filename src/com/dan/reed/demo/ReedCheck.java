package com.dan.reed.demo;

import com.dan.reed.Reed;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Läuft ohne Grafikkarte und ohne die App.
 */
public final class ReedCheck {
    private static int bad;

    public static void main(String[] args) {
        Reed.Density band = (x, z, k) -> k == Reed.Kind.SCHILF ? (Math.abs(z) < 10 ? 1 : 0) : k == Reed.Kind.GRAS ? (z > 15 ? 0.8 : 0) : 0;
        Reed.Ground flat = (x, z) -> 0.25;
        Reed a = Reed.breeze(), b = Reed.breeze();
        a.seed = b.seed = 7;
        float[] ca = a.cell(2, 0, Reed.Kind.SCHILF, band, flat), cb = b.cell(2, 0, Reed.Kind.SCHILF, band, flat);
        check(java.util.Arrays.equals(ca, cb) && ca.length > 0, "gleicher seed, gleiche Halme (" + ca.length / Reed.STRIDE + " in einer Zelle)");
        b.seed = 8;
        check(!java.util.Arrays.equals(ca, b.cell(2, 0, Reed.Kind.SCHILF, band, flat)), "anderer seed, andere Halme");
        boolean inBand = true, onGround = true;
        for (int i = 0; i < ca.length; i += Reed.STRIDE) {
            if (Math.abs(ca[i + 2]) >= 10) inBand = false;
            if (ca[i + 1] != 0.25f) onGround = false;
        }
        check(inBand, "Schilf nur, wo die Dichte > 0 ist");
        check(onGround, "Halme stehen auf dem Boden der Schnittstelle Ground");
        float[] none = a.cell(0, 3, Reed.Kind.BINSE, band, flat);
        check(none.length == 0, "keine Halme bei Dichte 0");
        double per = a.cell(0, -1, Reed.Kind.SCHILF, (x, z, k) -> 1, flat).length / (double) Reed.STRIDE / (Reed.CELL * Reed.CELL);
        check(Math.abs(per - Reed.Kind.SCHILF.perM2) < 0.6, String.format("Dichte 1 ergibt %.1f Halme je m² (Soll %.1f)", per, Reed.Kind.SCHILF.perM2));
        double avgH = 0;
        int n = 0;
        for (int i = 0; i < ca.length; i += Reed.STRIDE) { avgH += ca[i + 3]; n++; }
        avgH /= Math.max(1, n);
        check(avgH > 1.8 && avgH < 3.0, String.format("Schilf im Mittel %.2f m hoch", avgH));
        check(Reed.calm().windSpeed < Reed.breeze().windSpeed && Reed.breeze().windSpeed < Reed.fresh().windSpeed && Reed.fresh().windSpeed < Reed.gale().windSpeed,
                "Presets steigen von calm bis gale");
        check(Reed.lean(12, Reed.Kind.SCHILF.stiffness) > Reed.lean(4, Reed.Kind.SCHILF.stiffness)
                && Reed.lean(12, Reed.Kind.BINSE.stiffness) < Reed.lean(12, Reed.Kind.SCHILF.stiffness), "Neigung wächst mit dem Wind, steife Binsen neigen weniger");
        Reed w = Reed.fresh();
        double lo = 9, hi = -9;
        for (int i = 0; i < 400; i++) { double s = w.sway(0, 0, 1.3, Reed.Kind.SCHILF.stiffness, i * 0.05)[0]; lo = Math.min(lo, s); hi = Math.max(hi, s); }
        check(hi - lo > 0.05 && lo > 0, String.format("Böen bewegen den Schilf sichtbar (%.2f bis %.2f der Höhe)", lo, hi));
        double[] s1 = w.sway(0, 0, 1, 0.55, 2), s2 = w.sway(9, 0, 1, 0.55, 2);
        check(Math.abs(s1[0] - s2[0]) > 1e-3, "Böenwellen laufen über die Fläche (Nachbarn biegen sich anders)");
        Reed c = Reed.calm();
        check(Math.abs(c.sway(3, 4, 0, 0.55, 5)[0]) < 0.05, "Windstille: fast keine Bewegung");
        double[] summer, autumn;
        c.season = 0; summer = c.color(Reed.Kind.SCHILF, 0.5);
        c.season = 0.75; autumn = c.color(Reed.Kind.SCHILF, 0.5);
        check(summer[1] > summer[0] && autumn[0] > autumn[1], "Sommer grün, Herbst gold");
        float[] around = a.around(0, 0, 40, Reed.Kind.SCHILF, band, flat);
        boolean within = true;
        for (int i = 0; i < around.length; i += Reed.STRIDE) if (Math.hypot(around[i], around[i + 2]) > 40 + Reed.CELL * 1.5) within = false;
        check(around.length > 0 && within, "around() sammelt die Zellen im Umkreis");
        check(Reed.Quality.LOW.range < Reed.Quality.ULTRA.range && Reed.Quality.LOW.density < Reed.Quality.ULTRA.density, "Qualitätsstufen steigen");
        // seit Brocken Phase 5: Wind von außen
        Reed f = Reed.breeze();
        check(f.wind == null && Math.abs(f.windAt(5, 0, 5)[2] - f.windSpeed) < 1e-12, "ohne Wind von außen gilt der eine Wind wie bisher");
        f.wind = (x, y, z) -> new double[]{x > 0 ? 12 : 3, 0, 0};    // Ost: Wind nach Osten, 12 m/s; West: 3 m/s
        double[] wo = f.windAt(10, 0, 0), ww = f.windAt(-10, 0, 0);
        check(Math.abs(wo[2] - 12) < 1e-9 && Math.abs(wo[0] - 1) < 1e-9 && Math.abs(ww[2] - 3) < 1e-9, "mit Wind von außen gilt der Wind am Halm (12 und 3 m/s)");
        double so = 0, sw = 0;
        for (int i = 0; i < 200; i++) { so += f.sway(10, 0, 0, 1, Reed.Kind.GRAS.stiffness, i * 0.1)[0]; sw += f.sway(-10, 0, 0, 1, Reed.Kind.GRAS.stiffness, i * 0.1)[0]; }
        check(so > 3 * sw, String.format(java.util.Locale.GERMAN, "Gras im starken Wind neigt sich mehr (%.2f gegen %.2f der Höhe)", so / 200, sw / 200));
        double[] hgt = {Double.NaN};
        f.wind = (x, y, z) -> { hgt[0] = y; return new double[]{0, 0, 5}; };
        f.windAt(0, 100, 0);
        check(Math.abs(hgt[0] - 110) < 1e-9 && f.windAt(0, 100, 0)[1] > 0.99, "der Wind von außen wird 10 m über dem Boden gefragt, Süd heißt +z");
        float[] map = com.dan.reed.gl.ReedGl.windMap(f, f.wind, flat, new double[]{-32, -32, 64});
        check(map.length == com.dan.reed.gl.ReedGl.MAP_N * com.dan.reed.gl.ReedGl.MAP_N * 2 && map[1] == 5f && map[0] == 0f, "Windkarte für die Grafikkarte: Ost und Süd je Zelle");
        // seit Sylt (Phase 9): Strandhafer und Übersandung
        Reed s = Reed.breeze();
        Reed.Density hafer = (x, z, k) -> k == Reed.Kind.STRANDHAFER ? 1 : 0;
        float[] frei = s.cell(0, 0, Reed.Kind.STRANDHAFER, hafer, flat);
        s.burial = (x, z) -> 0.3;
        float[] halb = s.cell(0, 0, Reed.Kind.STRANDHAFER, hafer, flat);
        s.burial = (x, z) -> 2.0;
        float[] weg = s.cell(0, 0, Reed.Kind.STRANDHAFER, hafer, flat);
        double hf = 0, hh = 0;
        for (int i = 3; i < frei.length; i += Reed.STRIDE) hf += frei[i];
        for (int i = 3; i < halb.length; i += Reed.STRIDE) hh += halb[i];
        hf /= Math.max(1, frei.length / Reed.STRIDE);
        hh /= Math.max(1, halb.length / Reed.STRIDE);
        check(frei.length > 0 && hf > 0.6 && hf < 1.0 && Math.abs((hf - hh) - 0.3) < 0.05 && weg.length == 0,
                String.format("Strandhafer %.2f m; 0,3 m übersandet ragen %.2f m heraus, unter 2 m Sand keiner", hf, hh));
        s.season = 0.2;
        double[] sommer = s.color(Reed.Kind.STRANDHAFER, 0.5);
        check(sommer[2] > sommer[0] * 0.85 && sommer[1] > sommer[0], "Strandhafer im Sommer graugrün");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private ReedCheck() { }
}
