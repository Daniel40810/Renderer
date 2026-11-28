package com.dan.aufbereitung;

import com.dan.core.gitter.Gradraster;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ein kleiner Leser für GRIB, Edition 1 und 2, so weit ERA5 aus dem Climate Data Store ihn braucht (Werkbuch HalligHooge
 * Teil A, 12.6, P5b, F28): reguläre Gitter in Länge und Breite, einfach gepackt, mit oder ohne Bitmap, in jeder
 * Richtung abgetastet (nicht abwechselnd je Zeile). Nicht: Gaußsche und reduzierte Gitter, Kugelfunktionen, komplexe oder JPEG-Packung; was nicht geht,
 * wirft eine {@link IOException} mit Grund.
 * <p>
 * Jedes Feld kommt als {@link Feld} heraus: die Werte von Südwest nach Nordost (i schnell, j nach Norden), fehlende NaN,
 * dazu die Kennung der Größe und die gültige Zeit (Bezugszeit plus Vorhersageschritt).
 */
public final class Grib {
    private Grib() { }

    /**
     * Ein Feld. {@code tabelle}/{@code parameter} (Edition 1) oder {@code disziplin}/{@code kategorie}/{@code parameter}
     * (Edition 2); {@code flaeche}/{@code hoehe} die Art der Fläche und ihr Wert (Edition 1: Typ und Höhe, Edition 2:
     * erste feste Fläche, skaliert).
     */
    public record Feld(int edition, int tabelle, int disziplin, int kategorie, int parameter, int flaeche, double hoehe, Instant zeit, Gradraster raster, float[] werte) {
        /**
         * Kurzname nach ECMWF für die Größen des Antriebs: {@code u10}, {@code v10}, {@code msl}, seit P5e (F34) auch
         * {@code iews}, {@code inss} (Schubspannung an der Oberfläche, N/m²) und {@code charnock}; sonst eine Kennung.
         */
        public String name() {
            if (edition == 1 && tabelle == 128) {
                if (parameter == 165) return "u10";
                if (parameter == 166) return "v10";
                if (parameter == 151) return "msl";
                if (parameter == 229) return "iews";
                if (parameter == 230) return "inss";
                if (parameter == 148) return "charnock";
                switch (parameter) {                                               // Luft, Strahlung, Wassertemperatur (P10b, F90 A)
                    case 167: return "t2";
                    case 168: return "d2";
                    case 34: return "sst";
                    case 169: return "ssrd";
                    case 175: return "strd";
                    case 146: return "sshf";
                    case 147: return "slhf";
                    case 164: return "tcc";
                    case 228: return "tp";
                    case 49: return "fg10";                                         // Böe in 10 m (Seegang S8b, SF156 A)
                    default: break;
                }
            }
            if (edition == 1 && tabelle == 140) {                                   // Seegang (P8b, F74 A), Parametertafel 140 von ECMWF
                switch (parameter) {
                    case 229: return "swh";
                    case 230: return "mwd";
                    case 231: return "pp1d";
                    case 232: return "mwp";
                    case 234: return "shww";
                    case 235: return "mdww";
                    case 236: return "mpww";
                    case 237: return "shts";
                    case 238: return "mdts";
                    case 239: return "mpts";
                    case 225: return "dwww";
                    case 228: return "dwps";
                    default: break;
                }
            }
            if (edition == 2 && disziplin == 0) {
                if (kategorie == 2 && parameter == 2 && flaeche == 103 && hoehe == 10) return "u10";
                if (kategorie == 2 && parameter == 3 && flaeche == 103 && hoehe == 10) return "v10";
                if (kategorie == 3 && parameter == 0 && flaeche == 101) return "msl";
                if (kategorie == 3 && parameter == 1) return "msl";
                if (kategorie == 2 && parameter == 22 && flaeche == 103 && hoehe == 10) return "fg10";   // Böe (S8b)
            }
            return edition == 1 ? "t" + tabelle + "p" + parameter : "d" + disziplin + "c" + kategorie + "p" + parameter + "f" + flaeche;
        }
    }

    /** Liest alle Felder einer Datei, eins nach dem anderen an {@code an}. */
    public static void lies(Path datei, Consumer<Feld> an) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(datei), 1 << 20)) {
            lies(in, an);
        }
    }

    /** Liest alle Felder eines Stroms; Bytes zwischen den Nachrichten werden übersprungen. */
    public static void lies(InputStream in, Consumer<Feld> an) throws IOException {
        int nr = 0;
        while (true) {
            if (!suche(in)) return;
            byte[] k = in.readNBytes(4);
            if (k.length < 4) throw new EOFException("GRIB: Nachricht " + nr + " abgeschnitten");
            int edition = k[3] & 0xff;
            byte[] m;
            if (edition == 1) {
                int laenge = u(k, 0, 3);
                m = new byte[laenge];
                System.arraycopy(new byte[] {'G', 'R', 'I', 'B'}, 0, m, 0, 4);
                System.arraycopy(k, 0, m, 4, 4);
                if (in.readNBytes(m, 8, laenge - 8) != laenge - 8) throw new EOFException("GRIB: Nachricht " + nr + " abgeschnitten");
                an.accept(edition1(m, nr));
            } else if (edition == 2) {
                byte[] l = in.readNBytes(8);
                long laenge = 0;
                for (byte x : l) laenge = (laenge << 8) | (x & 0xff);
                if (laenge > Integer.MAX_VALUE) throw new IOException("GRIB: Nachricht " + nr + " zu groß");
                m = new byte[(int) laenge];
                System.arraycopy(new byte[] {'G', 'R', 'I', 'B'}, 0, m, 0, 4);
                System.arraycopy(k, 0, m, 4, 4);
                System.arraycopy(l, 0, m, 8, 8);
                if (in.readNBytes(m, 16, m.length - 16) != m.length - 16) throw new EOFException("GRIB: Nachricht " + nr + " abgeschnitten");
                for (Feld f : edition2(m, nr)) an.accept(f);
            } else throw new IOException("GRIB: Edition " + edition + " in Nachricht " + nr);
            if (m[m.length - 4] != '7' || m[m.length - 1] != '7') throw new IOException("GRIB: Nachricht " + nr + " endet nicht mit 7777");
            nr++;
        }
    }

    /** Liest bis hinter „GRIB“; false am Ende. */
    private static boolean suche(InputStream in) throws IOException {
        int s = 0;
        while (true) {
            int c = in.read();
            if (c < 0) return false;
            s = c == "GRIB".charAt(s) ? s + 1 : (c == 'G' ? 1 : 0);
            if (s == 4) return true;
        }
    }

    static int u(byte[] b, int o, int n) {
        int v = 0;
        for (int k = 0; k < n; k++) v = (v << 8) | (b[o + k] & 0xff);
        return v;
    }

    /** Ganze Zahl mit Vorzeichen im höchsten Bit (GRIB). */
    static int s(byte[] b, int o, int n) {
        int v = u(b, o, n), bit = 1 << (8 * n - 1);
        return (v & bit) != 0 ? -(v & (bit - 1)) : v;
    }

    /** Gleitkommazahl nach IBM (GRIB 1): Vorzeichen, 7 Bit Exponent zur Basis 16 (Versatz 64), 24 Bit Mantisse. */
    static double ibm(byte[] b, int o) {
        int e = b[o] & 0x7f;
        double m = u(b, o + 1, 3) / (double) (1 << 24);
        double v = m * Math.pow(16, e - 64);
        return (b[o] & 0x80) != 0 ? -v : v;
    }

    // ------------------------------------------------------------------ Edition 1

    static Feld edition1(byte[] m, int nr) throws IOException {
        int p = 8, lp = u(m, p, 3);
        int tabelle = m[p + 3] & 0xff, flags = m[p + 7] & 0xff, param = m[p + 8] & 0xff, flaeche = m[p + 9] & 0xff, hoehe = u(m, p + 10, 2);
        int jahr = (m[p + 12] & 0xff) + ((m[p + 24] & 0xff) - 1) * 100;
        LocalDateTime t = LocalDateTime.of(jahr, m[p + 13] & 0xff, m[p + 14] & 0xff, m[p + 15] & 0xff, m[p + 16] & 0xff);
        int einheit = m[p + 17] & 0xff, p1 = m[p + 18] & 0xff, p2 = m[p + 19] & 0xff, art = m[p + 20] & 0xff;
        // Art 2–5 (seit P10b): Mittel, Summe oder Unterschied von P1 bis P2 – gültig am Ende, bei P2 (ERA5: Strahlung als Summe der Stunde davor)
        long schritt = art == 10 ? u(m, p + 18, 2) : art == 0 || art == 1 ? p1 : art >= 2 && art <= 5 ? p2 : 0;
        Instant zeit = t.toInstant(ZoneOffset.UTC).plusSeconds(schritt * einheitSekunden(einheit, nr));
        int dezimal = s(m, p + 26, 2);
        if ((flags & 0x80) == 0) throw new IOException("GRIB 1, Nachricht " + nr + ": ohne Gitterbeschreibung");
        int g = p + lp, lg = u(m, g, 3);
        if ((m[g + 5] & 0xff) != 0) throw new IOException("GRIB 1, Nachricht " + nr + ": Gitter der Art " + (m[g + 5] & 0xff) + " (nur 0: Länge und Breite)");
        int ni = u(m, g + 6, 2), nj = u(m, g + 8, 2);
        double la1 = s(m, g + 10, 3) / 1000.0, lo1 = s(m, g + 13, 3) / 1000.0, la2 = s(m, g + 17, 3) / 1000.0, lo2 = s(m, g + 20, 3) / 1000.0;
        int abtast = m[g + 27] & 0xff;
        int o = g + lg;
        boolean[] bitmap = null;
        if ((flags & 0x40) != 0) {
            int lb = u(m, o, 3);
            if (u(m, o + 4, 2) != 0) throw new IOException("GRIB 1, Nachricht " + nr + ": vordefinierte Bitmap");
            bitmap = bits(m, o + 6, ni * nj);
            o += lb;
        }
        int fl = m[o + 3] & 0xff;
        if ((fl & 0xf0) != 0) throw new IOException("GRIB 1, Nachricht " + nr + ": Packung " + Integer.toHexString(fl >> 4) + " (nur einfach, Gitterpunkte, Gleitkomma)");
        int e = s(m, o + 4, 2);
        double r = ibm(m, o + 6);
        int bits = m[o + 10] & 0xff;
        float[] w = entpacken(m, o + 11, r, e, dezimal, bits, ni * nj, bitmap);
        return new Feld(1, tabelle, 0, 0, param, flaeche, hoehe, zeit, raster(la1, lo1, la2, lo2, ni, nj, nr), ordnen(w, ni, nj, abtast, lo1, lo2, la1, la2));
    }

    static long einheitSekunden(int e, int nr) throws IOException {
        return switch (e) {
            case 0 -> 60;
            case 1 -> 3600;
            case 2 -> 86400;
            case 10 -> 3 * 3600;
            case 11 -> 6 * 3600;
            case 12 -> 12 * 3600;
            case 13 -> 1;
            case 254 -> 1;
            default -> throw new IOException("GRIB, Nachricht " + nr + ": Zeiteinheit " + e);
        };
    }

    // ------------------------------------------------------------------ Edition 2

    static List<Feld> edition2(byte[] m, int nr) throws IOException {
        List<Feld> out = new ArrayList<>();
        int disziplin = m[6] & 0xff, o = 16;
        Instant bezug = null;
        Gradraster raster = null;
        int ni = 0, nj = 0, abtast = 0, kategorie = 0, param = 0, flaeche = 0, bits = 0, dezimal = 0, e = 0, packung = -1;
        double hoehe = 0, r = 0, lo1 = 0, lo2 = 0, la1 = 0, la2 = 0;
        long schritt = 0;
        boolean[] bitmap = null;
        while (o + 4 <= m.length) {
            if (m[o] == '7' && m[o + 1] == '7' && m[o + 2] == '7' && m[o + 3] == '7') break;
            int l = u(m, o, 4), sec = m[o + 4] & 0xff;
            switch (sec) {
                case 1 -> bezug = LocalDateTime.of(u(m, o + 12, 2), m[o + 14] & 0xff, m[o + 15] & 0xff, m[o + 16] & 0xff, m[o + 17] & 0xff, m[o + 18] & 0xff).toInstant(ZoneOffset.UTC);
                case 3 -> {
                    int vorlage = u(m, o + 12, 2);
                    if (vorlage != 0) throw new IOException("GRIB 2, Nachricht " + nr + ": Gittervorlage 3." + vorlage + " (nur 3.0: Länge und Breite)");
                    ni = u(m, o + 30, 4);
                    nj = u(m, o + 34, 4);
                    long winkel = u(m, o + 38, 4) & 0xffffffffL, teil = u(m, o + 42, 4) & 0xffffffffL;
                    double einheit = (winkel == 0 || winkel == 0xffffffffL) ? 1e-6 : (double) winkel / teil;
                    la1 = s(m, o + 46, 4) * einheit;
                    lo1 = s(m, o + 50, 4) * einheit;
                    la2 = s(m, o + 55, 4) * einheit;
                    lo2 = s(m, o + 59, 4) * einheit;
                    abtast = m[o + 71] & 0xff;
                    raster = raster(la1, lo1, la2, lo2, ni, nj, nr);
                }
                case 4 -> {
                    int vorlage = u(m, o + 7, 2);
                    if (vorlage != 0 && vorlage != 1 && vorlage != 8) throw new IOException("GRIB 2, Nachricht " + nr + ": Produktvorlage 4." + vorlage);
                    kategorie = m[o + 9] & 0xff;
                    param = m[o + 10] & 0xff;
                    int ze = m[o + 17] & 0xff;
                    schritt = (u(m, o + 18, 4) & 0xffffffffL) * einheitSekunden(ze, nr);
                    if (vorlage == 8 && bezug != null)                      // Summe oder Mittel: gültig am Ende des Zeitraums
                        schritt = LocalDateTime.of(u(m, o + 34, 2), m[o + 36] & 0xff, m[o + 37] & 0xff, m[o + 38] & 0xff, m[o + 39] & 0xff, m[o + 40] & 0xff)
                                .toInstant(ZoneOffset.UTC).getEpochSecond() - bezug.getEpochSecond();
                    flaeche = m[o + 22] & 0xff;
                    int sk = (byte) m[o + 23];
                    long wert = u(m, o + 24, 4) & 0xffffffffL;
                    hoehe = wert == 0xffffffffL ? Double.NaN : wert / Math.pow(10, sk);
                }
                case 5 -> {
                    packung = u(m, o + 9, 2);
                    if (packung != 0) throw new IOException("GRIB 2, Nachricht " + nr + ": Datenvorlage 5." + packung + " (nur 5.0: einfach gepackt)");
                    r = Float.intBitsToFloat(u(m, o + 11, 4));
                    e = s(m, o + 15, 2);
                    dezimal = s(m, o + 17, 2);
                    bits = m[o + 19] & 0xff;
                }
                case 6 -> {
                    int a = m[o + 5] & 0xff;
                    if (a == 0) bitmap = bits(m, o + 6, ni * nj);
                    else if (a != 255) throw new IOException("GRIB 2, Nachricht " + nr + ": Bitmap " + a);
                    else bitmap = null;
                }
                case 7 -> {
                    if (raster == null || bezug == null || packung < 0) throw new IOException("GRIB 2, Nachricht " + nr + ": Daten vor Gitter, Zeit oder Packung");
                    float[] w = entpacken(m, o + 5, r, e, dezimal, bits, ni * nj, bitmap);
                    out.add(new Feld(2, 0, disziplin, kategorie, param, flaeche, hoehe, bezug.plusSeconds(schritt), raster, ordnen(w, ni, nj, abtast, lo1, lo2, la1, la2)));
                }
                default -> { }
            }
            o += l;
        }
        return out;
    }

    // ------------------------------------------------------------------ gemeinsam

    static boolean[] bits(byte[] m, int o, int n) {
        boolean[] b = new boolean[n];
        for (int k = 0; k < n; k++) b[k] = (m[o + (k >> 3)] & (0x80 >> (k & 7))) != 0;
        return b;
    }

    /** Wert = (R + X · 2^E) / 10^D je gepackter Zahl X; ohne Bitmap jeder Punkt, mit nur die gesetzten. */
    static float[] entpacken(byte[] m, int o, double r, int e, int d, int bits, int n, boolean[] bitmap) throws IOException {
        float[] w = new float[n];
        double f2 = Math.pow(2, e), f10 = Math.pow(10, -d);
        long bit = 8L * o, da = n;
        if (bitmap != null) {
            da = 0;
            for (boolean x : bitmap) if (x) da++;
        }
        if (bits > 0 && 8L * o + (long) bits * da > 8L * m.length) throw new IOException("GRIB: zu wenige Daten (" + da + " Werte zu " + bits + " Bit)");
        for (int k = 0; k < n; k++) {
            if (bitmap != null && !bitmap[k]) {
                w[k] = Float.NaN;
                continue;
            }
            long x = 0;
            for (int q = 0; q < bits; q++, bit++) x = (x << 1) | ((m[(int) (bit >> 3)] >> (7 - (bit & 7))) & 1);
            w[k] = (float) ((r + x * f2) * f10);
        }
        return w;
    }

    /** Das Raster aus den Ecken (Länge auf −180 … 180, West vor Ost, Süd vor Nord). */
    static Gradraster raster(double la1, double lo1, double la2, double lo2, int ni, int nj, int nr) throws IOException {
        double w = Math.min(lo1, lo2), o = Math.max(lo1, lo2), s = Math.min(la1, la2), n = Math.max(la1, la2);
        if (lo1 > lo2 && ni > 1 && lo1 - lo2 > 180) {                 // über den Nullmeridian: Lo1 > Lo2 bei Abtastung nach Osten
            w = lo1 - 360;
            o = lo2;
        }
        if (w >= 180) {
            w -= 360;
            o -= 360;
        }
        double dl = ni > 1 ? (o - w) / (ni - 1) : 1, db = nj > 1 ? (n - s) / (nj - 1) : 1;
        return new Gradraster(w, s, dl, db, ni, nj);
    }

    /** Ordnet die Werte nach der Abtastfolge auf Südwest → Nordost, i schnell. */
    static float[] ordnen(float[] w, int ni, int nj, int abtast, double lo1, double lo2, double la1, double la2) throws IOException {
        boolean iNeg = (abtast & 0x80) != 0, jPos = (abtast & 0x40) != 0, jSchnell = (abtast & 0x20) != 0, wechselnd = (abtast & 0x10) != 0;
        if (wechselnd) throw new IOException("GRIB: Zeilen abwechselnd abgetastet (wird nicht gelesen)");
        float[] o = new float[ni * nj];
        for (int k = 0; k < w.length; k++) {
            int a, b;
            if (jSchnell) {
                a = k / nj;
                b = k % nj;
            } else {
                b = k / ni;
                a = k % ni;
            }
            int i = iNeg ? ni - 1 - a : a, j = jPos ? b : nj - 1 - b;
            o[j * ni + i] = w[k];
        }
        return o;
    }
}
