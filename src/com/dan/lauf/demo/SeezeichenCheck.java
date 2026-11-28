package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.raum.Utm;
import com.dan.core.szene.Kennung;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Schwimmerangabe;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.render.schwimmer.Lichterbild;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S7e1 (Seegang Werkbuch 99; SF148 A, SF149 A): Seezeichen und Lichter.
 * <ol>
 * <li><b>Lagen</b> (Prüfstein 30): Tonnen und Pricken der Szenendatei gegen den OSM-Auszug ({@code --osm}, je Zeile
 * {@code typ;id;lat;lon;tags}), ± 1 m; in der Anwendung stehen die Pricken an ihrer Lage (± 1 m).</li>
 * <li><b>Kennungen</b> (Prüfstein 31): je Leuchttonne die Kennung der Datei gegen die Tags (Art, Gruppe, Farbe, Periode)
 * und gegen die Funktion {@link Kennung#hell} (Blitze je Periode, Periode).</li>
 * <li><b>Bild bei Nacht</b> (Prüfsteine 31, 33): ruhigsee 19:00 UTC, das Auge 300 m von SA 13 auf die Laterne gerichtet;
 * ein Bild, wenn das Feuer brennt, eines, wenn es aus ist: am Ort der Laterne mindestens fünfmal heller [G]. Dazu
 * Sichtprüfung: bei Tag die Fähre am Anleger und die nächste Pricke (13:30), der Kutter mit Ankerlicht (19:00), die Fähre
 * in Fahrt in der Dämmerung (16:24), Xaver um 03:00 UTC (Hafen; die Fähre am Anleger mit Decksbeleuchtung, querab); seit
 * L5e1 dazu die Farbe des Feuers und sein Takt über eine Periode; Bilder {@code docs/status/S7e_*.png}. Seit S7e2 (Datei
 * 101) der Adler-Express in Fahrt bei Tag (12:14, aus Wittdün) und nach Sonnenuntergang (17:37, nach Nordstrand), nach 30 s
 * Fahrt aus 150 m querab; ein Trupp Eiderenten aus 12 m und ein Balken aus 15 m (13:30).</li>
 * </ol>
 * <pre>  SeezeichenCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--osm osm_seezeichen.csv] [--teil n]</pre>
 */
public final class SeezeichenCheck {
    static final Attest A = new Attest("Seezeichen und Lichter (S7e1)", Kennung.VERSION);

    public static void main(String[] args) {
        File szenen = null, osm = null;
        int breite = 1280, hoehe = 720;
        java.util.Set<String> nur = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--osm" -> osm = new File(args[++i]);
                case "--teil" -> nur.add(args[++i]);
                default -> { }
            }
        System.out.println("SeezeichenCheck – S7e1 (Seegang Werkbuch 99)");
        long t0 = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            File o = osm;
            if (nur.isEmpty() || nur.contains("1")) SchwimmerCheck.zeit("1", () -> lagen(sz, o));
            if (nur.isEmpty() || nur.contains("2")) SchwimmerCheck.zeit("2", () -> kennungen(sz, o));
            if (nur.isEmpty() || nur.contains("3")) SchwimmerCheck.zeit("3", () -> nacht(sz, breite, hoehe));
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", "S7e_Seezeichen.md"), "S7e1 – Seezeichen und Lichter (Kennung " + Kennung.VERSION + ", Lichterbild " + Lichterbild.VERSION + ", Flotte " + Flotte.VERSION + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Ein Seezeichen aus dem OSM-Auszug in UTM 32. */
    record Osm(String typ, long id, double ost, double nord, Map<String, String> tags) {}

    static List<Osm> osm(File f) {
        List<Osm> l = new ArrayList<>();
        if (f == null || !f.isFile()) return l;
        try {
            for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (z.startsWith("#") || z.isBlank()) continue;
                String[] t = z.split(";", 5);
                double[] u = Utm.vorwaerts(Double.parseDouble(t[3]), Double.parseDouble(t[2]), 32);
                Map<String, String> tg = new TreeMap<>();
                for (String kv : t.length > 4 ? t[4].split("\\|") : new String[0]) {
                    int i = kv.indexOf('=');
                    if (i > 0) tg.put(kv.substring(0, i), kv.substring(i + 1));
                }
                l.add(new Osm(t[0], Long.parseLong(t[1]), u[0], u[1], tg));
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return l;
    }

    static Osm naechstes(List<Osm> l, double o, double n, String typ) {
        Osm best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (Osm q : l) {
            if (!q.typ.startsWith(typ)) continue;
            double d = Math.hypot(q.ost - o, q.nord - n);
            if (d < bd) { bd = d; best = q; }
        }
        return best;
    }

    static List<Schwimmerangabe> angaben(Szenen sz, String name) {
        try { return sz.schwimmer(name); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    // ------------------------------------------------------------------ 1

    static void lagen(Szenen sz, File osmDatei) {
        A.teil("1 Lagen (Prüfstein 30)");
        List<Osm> osm = osm(osmDatei);
        List<Schwimmerangabe> tonnen = angaben(sz, "tonnen"), pricken = angaben(sz, "pricken");
        A.info("Szenendatei", f("%d Tonnen, %d Pricken; OSM-Auszug %s", tonnen.size(), pricken.size(), osm.isEmpty() ? "fehlt" : osm.size() + " Seezeichen"));
        if (!osm.isEmpty()) {
            for (String[] g : new String[][] {{"Tonnen", "buoy"}, {"Pricken", "beacon"}}) {
                List<Schwimmerangabe> l = g[0].equals("Tonnen") ? tonnen : pricken;
                double dmax = 0;
                int ok = 0;
                for (Schwimmerangabe a : l) {
                    Osm q = naechstes(osm, a.ost, a.nord, g[1]);
                    double d = q == null ? Double.POSITIVE_INFINITY : Math.hypot(q.ost - a.ost, q.nord - a.nord);
                    dmax = Math.max(dmax, d);
                    if (d <= 1) ok++;
                }
                A.pruefe(ok == l.size() && !l.isEmpty(), g[0] + " der Szenendatei an den Lagen aus OSM", f("%d von %d, größter Abstand %.3f m", ok, l.size(), dmax), "± 1 m");
            }
            // Pricken: Seite nach der Kategorie
            int seite = 0;
            for (Schwimmerangabe a : pricken) {
                Osm q = naechstes(osm, a.ost, a.nord, "beacon");
                String cat = q == null ? "" : q.tags.getOrDefault("seamark:beacon_lateral:category", "");
                if (cat.equals("port") && "backbord".equals(a.farbe) || cat.equals("starboard") && "steuerbord".equals(a.farbe)) seite++;
            }
            A.pruefe(seite == pricken.size() && !pricken.isEmpty(), "Pricken: Seite (Besen) nach der Kategorie in OSM", f("%d von %d", seite, pricken.size()), "alle");
        }
        // in der Anwendung: die Flotte stellt die Pricken an ihre Lage
        List<Schwimmerangabe> l = new ArrayList<>(pricken);
        try (Flotte fl = new Flotte(l, (x, y) -> -1.5)) {
            Flotte.Umwelt u = new Flotte.Umwelt() {
                @Override public double pegel(double o, double n) { return 0.5; }
                @Override public double[] wind() { return new double[] {5, 0}; }
            };
            for (int i = 0; i < 10; i++) { fl.vorBild(i / 30.0, 1, 1 / 30.0, u); fl.warte(); }
            double dmax = 0, zf = Double.NaN;
            for (Flotte.Schwimmer s : fl.schwimmer) {
                double[] p = s.koerper.punktWelt(0, 0, 0);
                dmax = Math.max(dmax, Math.hypot(p[0] - s.angabe.ost, p[1] - s.angabe.nord));
                zf = p[2];
            }
            A.pruefe(dmax <= 1, "Pricken in der Flotte an ihrer Lage", f("größter Abstand %.3f m; Fuß bei Grund −1,5 m auf %.2f m NHN (%.1f m im Grund)", dmax, zf, -1.5 - zf).replace("-", "−"), "± 1 m");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ 2

    static void kennungen(Szenen sz, File osmDatei) {
        A.teil("2 Kennungen (Prüfstein 31)");
        List<Osm> osm = osm(osmDatei);
        int n = 0, gleich = 0, funktion = 0;
        StringBuilder sb = new StringBuilder();
        for (Schwimmerangabe a : angaben(sz, "tonnen")) {
            if (a.kennung == null) continue;
            n++;
            Kennung k = new Kennung(a.kennung);
            boolean fOk = k.blitzeJePeriode() == k.gruppe && k.periode > 0;
            if (fOk) funktion++;
            String tagText = "–";
            if (!osm.isEmpty()) {
                Osm q = naechstes(osm, a.ost, a.nord, "buoy");
                Map<String, String> t = q.tags;
                String col = t.getOrDefault("seamark:light:colour", "");
                char fc = col.equals("green") ? 'G' : col.equals("red") ? 'R' : col.equals("yellow") ? 'Y' : 'W';
                int grp = Integer.parseInt(t.getOrDefault("seamark:light:group", "1"));
                double per = Double.parseDouble(t.getOrDefault("seamark:light:period", "NaN"));
                boolean ok = k.art.equals(t.getOrDefault("seamark:light:character", "")) && k.gruppe == grp && k.farbe == fc && Math.abs(k.periode - per) < 1e-9;
                if (ok) gleich++;
                tagText = f("%s(%d) %s %.0fs", t.get("seamark:light:character"), grp, fc, per) + (ok ? "" : " ANDERS");
            }
            sb.append(f("%s%s „%s“: Tags %s, Funktion %d Blitze in %.0f s", sb.length() > 0 ? "; " : "", a.titel, a.kennung, tagText, k.blitzeJePeriode(), k.periode));
        }
        A.info("Leuchttonnen", sb.toString());
        if (!osm.isEmpty()) A.pruefe(n > 0 && gleich == n, "Kennungen der Szenendatei gegen die Tags in OSM (Art, Gruppe, Farbe, Periode)", f("%d von %d gleich", gleich, n), "gleich");
        A.pruefe(n > 0 && funktion == n, "Kennung als Funktion: Blitze je Periode gleich der Gruppe", f("%d von %d", funktion, n), "gleich");
    }

    // ------------------------------------------------------------------ 3

    static void nacht(Szenen sz, int breite, int hoehe) {
        A.teil("3 Bild bei Nacht (Prüfsteine 31, 33)");
        try {
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            try {
                Szenario s = sz.szenario("ruhigsee");
                SchwimmerCheck.pegel(b, sz);
                b.oeffne(s);
                b.wasserbild().seegang = true;
                Flotte fl = b.flotte();
                double t = sek(b, "2026-10-30T19:00:00Z");
                // ---- SA 13: Auge 300 m im Südosten, 4 m NHN, auf die Laterne gerichtet
                Flotte.Schwimmer sa = null;
                for (Flotte.Schwimmer x : fl.schwimmer) if ("SA_13".equals(x.angabe.titel)) sa = x;
                if (sa == null) throw new IllegalStateException("SA_13 fehlt in der Flotte");
                Blick bl = new Blick();
                bl.ost = sa.angabe.ost + 300 * Math.sin(Math.toRadians(135));
                bl.nord = sa.angabe.nord + 300 * Math.cos(Math.toRadians(135));
                bl.hoehe = 4;
                bl.bildwinkel = 30;
                bl.groesse(breite, hoehe);
                einschwingen(b, fl, bl, t);
                // Zeiten, zu denen das Feuer ganz an und ganz aus ist; das Auge auf die Laterne
                double tAn = Double.NaN, tAus = Double.NaN;
                double[] ort = null;
                for (int i = 0; i < 400 && (Double.isNaN(tAn) || Double.isNaN(tAus)); i++) {
                    double ti = t + 0.05 * i;
                    double[] li = fl.lichter(ti, fl.lagen());
                    double[] hier = null;
                    for (int k = 0; k + Lichterbild.JE <= li.length; k += Lichterbild.JE)
                        if (Math.hypot(li[k] - sa.angabe.ost, li[k + 1] - sa.angabe.nord) < 30) hier = java.util.Arrays.copyOfRange(li, k, k + Lichterbild.JE);
                    if (hier != null && hier[3] >= 0.999 * TONNE_CD && Double.isNaN(tAn)) { tAn = ti; ort = hier; }
                    if (hier == null && Double.isNaN(tAus)) tAus = ti;
                }
                if (Double.isNaN(tAn) || Double.isNaN(tAus)) throw new IllegalStateException(f("kein Wechsel des Feuers gefunden (an %.2f, aus %.2f)", tAn, tAus));
                double dx = ort[0] - bl.ost, dy = ort[1] - bl.nord;
                bl.richtung = (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360;
                bl.neigung = Math.toDegrees(Math.atan2(ort[2] - bl.hoehe, Math.hypot(dx, dy)));
                float[] an = b.standbild(bl, tAn), aus = b.standbild(bl, tAus);
                int gez = b.lichterbild() != null ? b.lichterbild().gezeichnet : -1;
                double hAn = mitte(an, breite, hoehe, 6), hAus = mitte(aus, breite, hoehe, 6);
                bild(an, breite, hoehe, "S7e_nacht_SA13_an.png");
                bild(aus, breite, hoehe, "S7e_nacht_SA13_aus.png");
                A.info("SA 13 bei Nacht", f("Feuer an um %s, aus um %s UTC; Laterne %.0f m vom Auge, %.2f m NHN; Lichter im Bild %d", uhr(b, tAn), uhr(b, tAus),
                        Math.hypot(dx, dy), ort[2], gez));
                A.pruefe(hAn >= 5 * Math.max(hAus, 1e-3) && hAn > 0.3, "Tonnenfeuer im Bild: an gegen aus am Ort der Laterne (größte Helligkeit im Quadrat ± 6 Bildpunkte)",
                        f("an %.3f, aus %.3f (Faktor %.1f)", hAn, hAus, hAn / Math.max(hAus, 1e-3)), "≥ 5 [G]");
                double[] fa = farbe(an, breite, hoehe, 6);
                A.info("Farbe des Feuers im Bild (der grünste helle Bildpunkt, R G B, linear)", f("%.2f %.2f %.2f; Kennung grün", fa[0], fa[1], fa[2]));
                A.pruefe(fa[3] >= 4, "Grünes Feuer im Bild grün (seit L5e1, Befund 1): Bildpunkte mit G > 1,5 R und Luma über 0,3 am Ort der Laterne", f("%.0f", fa[3]), "≥ 4 [G]");
                // seit L5e1 (Prüfstein 31, Takt im Bild): eine Periode in Schritten von 0,1 s, an im Bild gegen an in der Kennung
                takt(b, fl, bl, sa, tAn - 1, breite, hoehe);
                // ---- Sichtprüfung
                String[][] bilder = {{"ruhigsee", "2026-10-30T13:30:00Z", "faehre", "S7e_tag_faehre.png"},     // seit L5e1: die Fähre nach Foto bei Tag
                        {"ruhigsee", "2026-10-30T13:30:00Z", "@pricke", "S7e_tag_pricke.png"},                       // seit L5e1: die nächste Pricke aus 30 m
                        {"ruhigsee", "2026-10-30T19:00:00Z", "kutter", "S7e_nacht_kutter.png"},
                        {"ruhigsee", "2026-10-30T16:24:00Z", "@faehre", "S7e_daemmerung_faehre.png"},
                        {"xaver", "2013-12-06T03:00:00Z", "hafen", "S7e_nacht_xaver.png"},
                        // seit L5e1: am 30.10. um 19:00 ist die Fähre nicht da (WEG); in der Sturmnacht liegt sie am Anleger
                        {"xaver", "2013-12-06T03:00:00Z", "@faehre", "S7e_nacht_faehre.png"},
                        // seit S7e2 (Datei 101): der Adler-Express in Fahrt bei Tag und bei Nacht, Enten und Treibgut
                        {"ruhigsee", "2026-10-30T12:14:00Z", "@adler", "S7e_tag_adler.png"},
                        {"ruhigsee", "2026-10-30T17:37:00Z", "@adler", "S7e_nacht_adler.png"},
                        {"ruhigsee", "2026-10-30T13:30:00Z", "@enten", "S7e_tag_enten.png"},
                        {"ruhigsee", "2026-10-30T13:30:00Z", "@treibgut", "S7e_tag_treibgut.png"}};
                StringBuilder sb = new StringBuilder();
                String offen = "ruhigsee";
                for (String[] bb : bilder) {
                    if (!bb[0].equals(offen)) {
                        SchwimmerCheck.pegel(b, sz);
                        b.oeffne(sz.szenario(bb[0]));
                        b.wasserbild().seegang = true;
                        fl = b.flotte();
                        offen = bb[0];
                    }
                    double tb = sek(b, bb[1]);
                    boolean ziel = bb[2].equals("@faehre"), pricke = bb[2].equals("@pricke");
                    boolean adler = bb[2].equals("@adler"), enten = bb[2].equals("@enten"), treib = bb[2].equals("@treibgut");   // seit S7e2
                    Ansicht ans = sz.ansicht(ziel || pricke || adler || enten || treib ? "faehre" : bb[2]);
                    Blick bk = new Blick();
                    ans.setze(bk, b.gelaende, b.utm(), b.uhr().bei(tb));
                    bk.groesse(breite, hoehe);
                    double[] wa = b.wasserUndSeegang(bk.ost, bk.nord);
                    Ansicht.hebe(bk, wa[0], wa[1], ans.ueberWasser);
                    einschwingen(b, fl, bk, adler ? tb - 30 : tb);
                    String mehr = adler ? adlerBild(b, fl, bk, tb) : enten || treib ? nahBild(b, fl, bk, tb, enten) : "";   // seit S7e2
                    if (pricke) {
                        // das Auge 30 m von der Pricke, die dem Anleger am nächsten steht, 2 m über dem Wasser, auf ihren Kopf gerichtet
                        Flotte.Schwimmer pn = null;
                        double dn = Double.POSITIVE_INFINITY;
                        for (Flotte.Schwimmer x : fl.schwimmer)
                            if (x.steht && Math.hypot(x.angabe.ost - 471381, x.angabe.nord - 6047983) < dn) { dn = Math.hypot(x.angabe.ost - 471381, x.angabe.nord - 6047983); pn = x; }
                        if (pn != null) {
                            double ex = 471381 - pn.angabe.ost, ey = 6047983 - pn.angabe.nord, el = Math.hypot(ex, ey);
                            bk.ost = pn.angabe.ost + 30 * ex / el;
                            bk.nord = pn.angabe.nord + 30 * ey / el;
                            double[] wz = b.wasserUndSeegang(bk.ost, bk.nord);
                            bk.hoehe = (Double.isNaN(wz[0]) ? 1 : wz[0]) + 2;
                            double[] kopf = pn.koerper.punktWelt(0, 0, 5.0);
                            bk.richtung = (Math.toDegrees(Math.atan2(pn.angabe.ost - bk.ost, pn.angabe.nord - bk.nord)) + 360) % 360;
                            bk.neigung = Math.toDegrees(Math.atan2(0.5 * (kopf[2] + bk.hoehe) - bk.hoehe, 30));
                            bk.bildwinkel = 30;
                            einschwingen(b, fl, bk, tb);
                            sb.append(f("%sPricke %s (%s, %.0f m vom Anleger, Kopf %.2f m NHN)", sb.length() > 0 ? "; " : "", pn.angabe.titel, pn.angabe.farbe, dn, kopf[2]));
                        }
                    }
                    if (ziel) {
                        // das Auge 220 m querab der Fähre, 5 m über dem Wasser, auf sie gerichtet (wenn sie da ist)
                        Flotte.Schwimmer f0 = fl.schwimmer.get(0);
                        double[] p = f0.koerper.punktWelt(0, 0, 0), v = f0.koerper.punktWelt(1, 0, 0);
                        double ke = v[0] - p[0], kn = v[1] - p[1], kl = Math.hypot(ke, kn);
                        String ph = f0.phase();
                        if (ph != null && !ph.startsWith("WEG") && kl > 0) {
                            bk.ost = p[0] - 220 * kn / kl;
                            bk.nord = p[1] + 220 * ke / kl;
                            double[] wz = b.wasserUndSeegang(bk.ost, bk.nord);
                            bk.hoehe = (Double.isNaN(wz[0]) ? 0 : Math.max(wz[0], 0)) + 5;
                            bk.richtung = (Math.toDegrees(Math.atan2(p[0] - bk.ost, p[1] - bk.nord)) + 360) % 360;
                            bk.neigung = -1;
                            einschwingen(b, fl, bk, tb);
                        }
                    }
                    float[] px = b.standbild(bk, tb);
                    bild(px, breite, hoehe, bb[3]);
                    Flotte.Schwimmer fae = fl.schwimmer.get(0);
                    sb.append(f("%s%s (%s, %s UTC, Ansicht %s): %d Lichter im Bild, Fähre „%s“", sb.length() > 0 ? "; " : "", bb[3], bb[0], bb[1].substring(11, 16), bb[2],
                            b.lichterbild() != null ? b.lichterbild().gezeichnet : -1, fae.phase()));
                    if (!mehr.isEmpty()) sb.append(" – ").append(mehr);
                }
                A.info("Sichtprüfung (Prüfstein 33)", "docs/status/" + sb);
            } finally {
                b.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Lichtstärke eines Tonnenfeuers (3 sm, wie {@code lauf.Lichter}). */
    static final double TONNE_CD = 12;

    static double sek(Buehne b, String utc) { return (Instant.parse(utc).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0; }

    static final java.time.format.DateTimeFormatter UHR = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SS").withZone(java.time.ZoneOffset.UTC);

    /** Uhrzeit UTC mit Hundertsteln (seit L5e1: {@code Instant.toString} lässt volle Sekunden ohne Bruch). */
    static String uhr(Buehne b, double t) { return UHR.format(b.uhr().bei(t)); }

    /** Bilder wie die Bildschleife, bis die Fläche steht (höchstens 900 Bilder oder 180 s), dann die Flotte gesetzt. */
    static void einschwingen(Buehne b, Flotte fl, Blick bl, double t) throws InterruptedException {
        Flaechenwart w = b.wart();
        b.standbild(bl, t);
        fl.neuSetzen();
        long uhrNs = 0, w0 = System.nanoTime();
        double dt = 1.0 / 30;
        int bereit = -1;
        for (int i = 0; i < 900; i++) {
            w.zeitraffer(1);
            w.vorBild(bl, t, uhrNs);
            b.flotteBild(t, 1, dt);
            b.bildzeiten(bl, t, 1, true);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt() && (w.koerpermitte() == null || w.koerperBereit())) bereit = i;
            fl.warte();
            if (bereit >= 0 && i >= bereit + 30) break;
            if ((System.nanoTime() - w0) / 1e9 > 180) break;
            uhrNs += Math.round(dt * 1e9);
        }
    }

    /**
     * Seit S7e2: der Adler-Express nach 30 s Fahrt bis tb (eingeschwungen bei tb − 30); dann das Auge 150 m querab an
     * Steuerbord, 6 m über dem Wasser, auf ihn gerichtet, Bildwinkel 35°; die Fläche schwingt bei tb ein (ohne neu zu setzen).
     */
    static String adlerBild(Buehne b, Flotte fl, Blick bk, double tb) throws InterruptedException {
        Flotte.Schwimmer ad = null;
        for (Flotte.Schwimmer x : fl.schwimmer) if (x.angabe.name.equals("adler")) ad = x;
        if (ad == null) return "kein Adler-Express";   // (xaver: 2013)
        Flaechenwart w = b.wart();
        long uhrNs = 0;
        double dt = 1.0 / 30, t = tb - 30, tauMax = 0;
        while (t < tb) {
            t = Math.min(tb, t + dt);
            w.zeitraffer(1);
            w.vorBild(bk, t, uhrNs);
            b.flotteBild(t, 1, dt);
            b.bildzeiten(bk, t, 1, true);
            w.nachBild();
            fl.warte();
            if (ad.koerper.gleiten != null) tauMax = Math.max(tauMax, ad.koerper.gleiten.tau);
            uhrNs += Math.round(dt * 1e9);
        }
        double[] p = ad.koerper.punktWelt(0, 0, 0), v = ad.koerper.geschwindigkeit(), r = ad.koerper.richtungWelt(1, 0, 0);
        double rl = Math.hypot(r[0], r[1]), re = r[0] / rl, rn = r[1] / rl;
        bk.ost = p[0] + 150 * rn;
        bk.nord = p[1] - 150 * re;
        double[] wz = b.wasserUndSeegang(bk.ost, bk.nord);
        bk.hoehe = (Double.isNaN(wz[0]) ? 0 : Math.max(wz[0], 0)) + 6;
        bk.richtung = (Math.toDegrees(Math.atan2(p[0] - bk.ost, p[1] - bk.nord)) + 360) % 360;
        bk.neigung = -1;
        bk.bildwinkel = 35;
        ruhe(b, fl, bk, tb);
        return f("Adler-Express „%s“, %.1f m/s, Trimm %.2f° (in den 30 s bis %.2f°), Gleiten w %.2f, Kurs %.0f°", ad.phase(), Math.hypot(v[0], v[1]),
                ad.koerper.trimm(), tauMax, ad.koerper.gleiten != null ? ad.koerper.gleiten.anteil : 0, ad.koerper.kurs());
    }

    /** Seit S7e2: das Auge nah an einem Trupp Enten (12 m, 1,2 m hoch) oder am ersten Balken (15 m, 2 m hoch). */
    static String nahBild(Buehne b, Flotte fl, Blick bk, double tb, boolean enten) throws InterruptedException {
        double sx = 0, sy = 0, sz = 0;
        int n = 0, da = 0;
        for (Flotte.Schwimmer x : fl.schwimmer) {
            boolean passt = enten ? x.angabe.name.startsWith("enten.E1.") : x.angabe.name.equals("treibgut.T1");
            if (!passt) continue;
            double[] o = x.koerper.lage();
            sx += o[0]; sy += o[1]; sz += o[2]; n++;
            if (!x.weg) da++;
        }
        if (n == 0) return enten ? "keine Enten" : "kein Treibgut";
        sx /= n; sy /= n; sz /= n;
        double ab = enten ? 12 : 15, ho = enten ? 1.2 : 2.0, ri = Math.toRadians(enten ? 200 : 160);   // das Auge von Südwest bzw. Südost [G]
        bk.ost = sx + ab * Math.sin(ri);
        bk.nord = sy + ab * Math.cos(ri);
        double[] wz = b.wasserUndSeegang(bk.ost, bk.nord);
        bk.hoehe = (Double.isNaN(wz[0]) ? sz : wz[0]) + ho;
        bk.richtung = (Math.toDegrees(Math.atan2(sx - bk.ost, sy - bk.nord)) + 360) % 360;
        bk.neigung = Math.toDegrees(Math.atan2(sz - bk.hoehe, ab));
        bk.bildwinkel = 40;
        ruhe(b, fl, bk, tb);
        return enten ? f("Trupp E1: %d Enten, %d da", n, da) : "Balken T1";
    }

    /** Seit S7e2: die Fläche zum Blick bk bei t einschwingen, ohne die Körper neu zu setzen (die Zeit steht). */
    static void ruhe(Buehne b, Flotte fl, Blick bk, double t) throws InterruptedException {
        Flaechenwart w = b.wart();
        b.standbild(bk, t);
        long uhrNs = 0, w0 = System.nanoTime();
        double dt = 1.0 / 30;
        int bereit = -1;
        for (int i = 0; i < 900; i++) {
            w.zeitraffer(1);
            w.vorBild(bk, t, uhrNs);
            b.flotteBild(t, 1, dt);
            b.bildzeiten(bk, t, 1, true);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt() && (w.koerpermitte() == null || w.koerperBereit())) bereit = i;
            fl.warte();
            if (bereit >= 0 && i >= bereit + 30) break;
            if ((System.nanoTime() - w0) / 1e9 > 180) break;
            uhrNs += Math.round(dt * 1e9);
        }
    }

    /** Prüfstein 31: das Feuer von SA 13 über eine Periode im Bild, Schritt 0,1 s (ein „Bild“), gegen die Kennung. */
    static void takt(Buehne b, Flotte fl, Blick bl, Flotte.Schwimmer sa, double t0, int breite, int hoehe) {
        double periode = new Kennung(sa.angabe.kennung).periode, dt = 0.1;
        int n = (int) Math.round(periode / dt);
        boolean[] bild = new boolean[n], soll = new boolean[n];
        double[] h = new double[n];
        double hMax = 0;
        long w0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            double ti = t0 + i * dt;
            h[i] = mitte(b.standbild(bl, ti), breite, hoehe, 6);
            hMax = Math.max(hMax, h[i]);
            double[] li = fl.lichter(ti, fl.lagen());
            for (int k = 0; k + Lichterbild.JE <= li.length; k += Lichterbild.JE)
                if (Math.hypot(li[k] - sa.angabe.ost, li[k + 1] - sa.angabe.nord) < 30 && li[k + 3] >= 0.5 * TONNE_CD) soll[i] = true;
        }
        int blitzeBild = 0, blitzeSoll = 0, falsch = 0;
        for (int i = 0; i < n; i++) {
            bild[i] = h[i] >= 0.5 * hMax;
            if (bild[i] != soll[i]) falsch++;
            int v = (i + n - 1) % n;
            if (bild[i] && !bild[v]) blitzeBild++;
            if (soll[i] && !soll[v]) blitzeSoll++;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(bild[i] ? '#' : soll[i] ? '+' : '.');
        A.info("Takt im Bild (je 0,1 s: # an, + nur in der Kennung, . aus)", sb + f(" – %d Bilder in %.0f s", n, (System.nanoTime() - w0) * 1e-9));
        A.pruefe(blitzeBild == blitzeSoll && falsch <= 2 * blitzeSoll, "Takt im Bild über eine Periode gegen die Kennung (jede Flanke ± 1 Bild)",
                f("%d Blitze im Bild, %d in der Kennung; %d von %d Bildern anders", blitzeBild, blitzeSoll, falsch, n), "gleich, ± 1 Bild je Flanke");
    }

    /**
     * Farbe des Feuers (seit L5e1d): der Bildpunkt mit dem größten G − R unter denen mit Luma über 0,3 im Quadrat ± r um die
     * Mitte (R, G, B), dazu als vierter Wert die Zahl der Bildpunkte mit G > 1,5 R und Luma über 0,3. Der hellste Punkt
     * oder ein Mittel liegen im blassen Ring, den die Stäbchen um den Kern legen.
     */
    static double[] farbe(float[] p, int breite, int hoehe, int r) {
        double[] c = new double[4];
        double best = -1;
        for (int y = hoehe / 2 - r; y <= hoehe / 2 + r; y++)
            for (int x = breite / 2 - r; x <= breite / 2 + r; x++) {
                int k = 4 * (y * breite + x);
                if (0.2126 * p[k] + 0.7152 * p[k + 1] + 0.0722 * p[k + 2] <= 0.3) continue;
                if (p[k + 1] > 1.5 * p[k]) c[3]++;
                if (p[k + 1] - p[k] > best) { best = p[k + 1] - p[k]; c[0] = p[k]; c[1] = p[k + 1]; c[2] = p[k + 2]; }
            }
        return c;
    }

    /** Größte Helligkeit (Luma) im Quadrat ± r Bildpunkte um die Mitte. */
    static double mitte(float[] p, int breite, int hoehe, int r) {
        double m = 0;
        for (int y = hoehe / 2 - r; y <= hoehe / 2 + r; y++)
            for (int x = breite / 2 - r; x <= breite / 2 + r; x++) {
                int k = 4 * (y * breite + x);
                m = Math.max(m, 0.2126 * p[k] + 0.7152 * p[k + 1] + 0.0722 * p[k + 2]);
            }
        return m;
    }

    static void bild(float[] px, int breite, int hoehe, String datei) throws java.io.IOException {
        javax.imageio.ImageIO.write(SeegangBilder.bild(px, breite, hoehe), "png", Path.of("docs", "status", datei).toFile());
    }
}
