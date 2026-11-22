package com.dan.aufbereitung;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein kleiner Leser für HDF5 (und damit NetCDF-4), so weit EOT20 ihn braucht (Werkbuch HalligHooge Teil A, 12.6,
 * Befund 1, P5b): Datensätze in der Wurzelgruppe, zusammenhängend oder kompakt abgelegt, Zahlen fester Größe (float32,
 * float64, ganze Zahlen) in beiden Byte-Folgen. Superblock 0–3, Objektköpfe Version 1 und 2 mit Fortsetzungen,
 * Gruppen alter Art (B-Baum und lokaler Heap) und neuer Art (Verweise im Objektkopf). Nicht: gestückelte oder
 * gepackte Datensätze, dichte Gruppen (Verweise im Fractal Heap), Untergruppen. Was nicht geht, wirft eine
 * {@link IOException} mit Grund.
 * <p>
 * Die Datei liegt ganz im Speicher (EOT20: 133 MB je Tide).
 */
public final class Hdf5 {
    static final byte[] SIGNATUR = {(byte) 0x89, 'H', 'D', 'F', '\r', '\n', 0x1a, '\n'};

    /** Ein Datensatz: Form, Zahlenart, Lage der Werte in der Datei. */
    public record Datensatz(String name, long[] form, int klasse, int groesse, boolean gross, boolean mitVorzeichen, long adresse, long laenge, byte[] kompakt, String nichtLesbar) {
        public long werte() {
            long n = 1;
            for (long d : form) n *= d;
            return n;
        }

        @Override public String toString() {
            if (nichtLesbar != null) return name + " " + Arrays.toString(form) + " (" + nichtLesbar + ")";
            return name + " " + Arrays.toString(form) + (klasse == 1 ? " float" : " int") + groesse * 8 + (gross ? " BE" : " LE") + (kompakt != null ? " kompakt" : " @" + adresse);
        }
    }

    private final ByteBuffer b;
    private final long basis;
    private int so = 8, sl = 8;
    private final Map<String, Long> wurzel = new LinkedHashMap<>();
    private final Map<String, Datensatz> datensaetze = new LinkedHashMap<>();

    private Hdf5(byte[] daten) {
        b = ByteBuffer.wrap(daten).order(ByteOrder.LITTLE_ENDIAN);
        long s = -1;
        for (long o = 0; o + 8 <= daten.length; o = o == 0 ? 512 : 2 * o) {
            if (Arrays.equals(Arrays.copyOfRange(daten, (int) o, (int) o + 8), SIGNATUR)) {
                s = o;
                break;
            }
        }
        basis = s;
    }

    /** Liest die Struktur (Wurzelgruppe, Kopf jedes Datensatzes); die Werte erst bei Bedarf. */
    public static Hdf5 lies(byte[] daten) throws IOException {
        Hdf5 h = new Hdf5(daten);
        if (h.basis < 0) throw new IOException("HDF5: keine Signatur");
        h.superblock();
        return h;
    }

    /** Die Namen der Datensätze in der Wurzelgruppe. */
    public List<String> namen() { return new ArrayList<>(datensaetze.keySet()); }

    public Datensatz datensatz(String name) throws IOException {
        Datensatz d = datensaetze.get(name);
        if (d == null) throw new IOException("HDF5: kein Datensatz " + name + " (da: " + datensaetze.keySet() + ")");
        return d;
    }

    private Datensatz lesbar(String name) throws IOException {
        Datensatz d = datensatz(name);
        if (d.nichtLesbar != null) throw new IOException("HDF5: " + name + " " + d.nichtLesbar + " (wird nicht gelesen)");
        return d;
    }

    /** Alle Werte als double. */
    public double[] doubles(String name) throws IOException {
        Datensatz d = lesbar(name);
        if (d.werte() > Integer.MAX_VALUE) throw new IOException("HDF5: " + name + " zu groß");
        double[] w = new double[(int) d.werte()];
        for (int k = 0; k < w.length; k++) w[k] = wert(d, k);
        return w;
    }

    /** Ein Ausschnitt aus einem zweidimensionalen Datensatz: Zeilen j0 … j1 − 1, Spalten i0 … i1 − 1, zeilenweise. */
    public double[] doubles(String name, int j0, int j1, int i0, int i1) throws IOException {
        Datensatz d = lesbar(name);
        if (d.form.length != 2) throw new IOException("HDF5: " + name + " hat " + d.form.length + " Dimensionen");
        long nj = d.form[0], ni = d.form[1];
        if (j0 < 0 || i0 < 0 || j1 > nj || i1 > ni || j1 < j0 || i1 < i0) throw new IOException("HDF5: Ausschnitt außerhalb von " + name + Arrays.toString(d.form));
        double[] w = new double[(j1 - j0) * (i1 - i0)];
        int k = 0;
        for (int j = j0; j < j1; j++)
            for (int i = i0; i < i1; i++) w[k++] = wert(d, (long) j * ni + i);
        return w;
    }

    private double wert(Datensatz d, long k) throws IOException {
        long o = k * d.groesse;
        ByteBuffer q;
        if (d.kompakt != null) q = ByteBuffer.wrap(d.kompakt);
        else {
            if (d.adresse == undefiniert()) throw new IOException("HDF5: " + d.name + " hat keine Werte");
            q = b;
            o += basis + d.adresse;
        }
        q.order(d.gross ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        try {
            if (d.klasse == 1) return d.groesse == 8 ? q.getDouble((int) o) : q.getFloat((int) o);
            return switch (d.groesse) {
                case 1 -> d.mitVorzeichen ? q.get((int) o) : q.get((int) o) & 0xff;
                case 2 -> d.mitVorzeichen ? q.getShort((int) o) : q.getShort((int) o) & 0xffff;
                case 4 -> d.mitVorzeichen ? q.getInt((int) o) : q.getInt((int) o) & 0xffffffffL;
                default -> q.getLong((int) o);
            };
        } finally {
            b.order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    // ------------------------------------------------------------------ Struktur

    private long undefiniert() { return so == 8 ? -1L : (1L << (8 * so)) - 1; }

    private long zahl(long o, int n) {
        long v = 0;
        for (int k = n - 1; k >= 0; k--) v = (v << 8) | (b.get((int) (basis + o + k)) & 0xff);
        return v;
    }

    private int u8(long o) { return b.get((int) (basis + o)) & 0xff; }

    private long adr(long o) { return zahl(o, so); }

    private String signatur(long o) { return new String(new byte[] {b.get((int) (basis + o)), b.get((int) (basis + o + 1)), b.get((int) (basis + o + 2)), b.get((int) (basis + o + 3))}, StandardCharsets.US_ASCII); }

    private void superblock() throws IOException {
        int v = u8(8);
        long wurzelKopf;
        if (v == 0 || v == 1) {
            so = u8(13);
            sl = u8(14);
            long o = 24 + (v == 1 ? 4 : 0);
            o += 4L * so;                                           // Basis, freier Platz, Ende, Treiber
            wurzelKopf = adr(o + so);                               // Eintrag der Wurzel: Name, Objektkopf, …
            gruppe(wurzelKopf, adr(o + 2L * so + 8), adr(o + 2L * so + 8 + so), u8(o + 2L * so) == 1);
        } else if (v == 2 || v == 3) {
            so = u8(9);
            sl = u8(10);
            wurzelKopf = adr(12 + 3L * so);
            gruppe(wurzelKopf, 0, 0, false);
        } else throw new IOException("HDF5: Superblock Version " + v);
        for (Map.Entry<String, Long> e : wurzel.entrySet()) {
            Datensatz d = datensatz(e.getKey(), e.getValue());
            if (d != null) datensaetze.put(e.getKey(), d);
        }
    }

    /** Die Verweise der Wurzelgruppe: aus der Symboltabelle (alte Art) oder aus Verweisen im Objektkopf. */
    private void gruppe(long kopf, long baumCache, long heapCache, boolean cache) throws IOException {
        List<long[]> m = nachrichten(kopf);
        for (long[] n : m) {
            int typ = (int) n[0];
            long o = n[1];
            if (typ == 0x11) symbole(adr(o), adr(o + so));
            else if (typ == 0x06) verweis(o);
            else if (typ == 0x02) {
                int flags = u8(o + 1);
                long heap = adr(o + 2 + ((flags & 1) != 0 ? 8 : 0));
                if (heap != undefiniert()) throw new IOException("HDF5: Gruppe mit dichter Ablage (Fractal Heap) wird nicht gelesen");
            }
        }
        if (wurzel.isEmpty() && cache) symbole(baumCache, heapCache);
    }

    private void verweis(long o) throws IOException {
        int flags = u8(o + 1);
        long p = o + 2;
        int art = 0;
        if ((flags & 0x08) != 0) art = u8(p++);
        if ((flags & 0x04) != 0) p += 8;
        if ((flags & 0x10) != 0) p++;
        int ln = 1 << (flags & 3);
        long laenge = zahl(p, ln);
        p += ln;
        byte[] name = new byte[(int) laenge];
        for (int k = 0; k < laenge; k++) name[k] = b.get((int) (basis + p + k));
        p += laenge;
        if (art == 0) wurzel.put(new String(name, StandardCharsets.UTF_8), adr(p));
    }

    private void symbole(long baum, long heap) throws IOException {
        if (!signatur(heap).equals("HEAP")) throw new IOException("HDF5: kein lokaler Heap bei " + heap);
        long daten = adr(heap + 8 + 2L * sl);
        knoten(baum, daten);
    }

    private void knoten(long a, long heapDaten) throws IOException {
        String s = signatur(a);
        if (s.equals("SNOD")) {
            int n = (int) zahl(a + 6, 2);
            long o = a + 8;
            for (int k = 0; k < n; k++, o += 2L * so + 24) {
                long nameOff = adr(o), kopf = adr(o + so);
                StringBuilder name = new StringBuilder();
                for (long p = heapDaten + nameOff; u8(p) != 0; p++) name.append((char) u8(p));
                wurzel.put(name.toString(), kopf);
            }
            return;
        }
        if (!s.equals("TREE")) throw new IOException("HDF5: weder B-Baum noch Symbolknoten bei " + a);
        if (u8(a + 4) != 0) throw new IOException("HDF5: B-Baum der Art " + u8(a + 4) + " in einer Gruppe");
        int n = (int) zahl(a + 6, 2);
        long o = a + 8 + 2L * so;                                   // nach den Geschwistern
        for (int k = 0; k < n; k++) {
            o += sl;                                                // Schlüssel
            knoten(adr(o), heapDaten);
            o += so;
        }
    }

    /** Alle Nachrichten eines Objektkopfs als {Typ, Adresse der Daten, Größe}, mit Fortsetzungen. */
    private List<long[]> nachrichten(long kopf) throws IOException {
        List<long[]> m = new ArrayList<>();
        if (signatur(kopf).equals("OHDR")) {
            int flags = u8(kopf + 5);
            long o = kopf + 6;
            if ((flags & 0x20) != 0) o += 16;
            if ((flags & 0x10) != 0) o += 4;
            int g = 1 << (flags & 3);
            long laenge = zahl(o, g);
            o += g;
            block2(o, o + laenge, (flags & 0x04) != 0, m);
        } else {
            if (u8(kopf) != 1) throw new IOException("HDF5: Objektkopf Version " + u8(kopf) + " bei " + kopf);
            long laenge = zahl(kopf + 8, 4);
            block1(kopf + 16, kopf + 16 + laenge, m);
        }
        return m;
    }

    private void block1(long o, long ende, List<long[]> m) throws IOException {
        while (o + 8 <= ende) {
            int typ = (int) zahl(o, 2), g = (int) zahl(o + 2, 2);
            long d = o + 8;
            if (typ == 0x10) block1(adr(d), adr(d) + zahl(d + so, sl), m);
            else m.add(new long[] {typ, d, g});
            o = d + g;
        }
    }

    private void block2(long o, long ende, boolean ordnung, List<long[]> m) throws IOException {
        int kopf = ordnung ? 6 : 4;
        while (o + kopf <= ende) {
            int typ = u8(o), g = (int) zahl(o + 1, 2);
            long d = o + kopf;
            if (typ == 0x10) {
                long a = adr(d), l = zahl(d + so, sl);
                if (!signatur(a).equals("OCHK")) throw new IOException("HDF5: Fortsetzung ohne OCHK bei " + a);
                block2(a + 4, a + l - 4, ordnung, m);
            } else if (typ != 0) m.add(new long[] {typ, d, g});
            o = d + g;
        }
    }

    /** Der Kopf eines Datensatzes; null, wenn das Objekt keiner ist (eine Gruppe). */
    private Datensatz datensatz(String name, long kopf) throws IOException {
        long[] form = null;
        int klasse = -1, groesse = 0;
        boolean gross = false, vorzeichen = false, gefiltert = false;
        long adresse = undefiniert(), laenge = 0;
        byte[] kompakt = null;
        int art = -1;
        for (long[] n : nachrichten(kopf)) {
            int typ = (int) n[0];
            long o = n[1];
            switch (typ) {
                case 0x01 -> {
                    int v = u8(o), dim = u8(o + 1);
                    long p = o + (v == 1 ? 8 : 4);
                    form = new long[dim];
                    for (int k = 0; k < dim; k++) form[k] = zahl(p + (long) k * sl, sl);
                }
                case 0x03 -> {
                    klasse = u8(o) & 0x0f;
                    int bits = u8(o + 1);
                    gross = (bits & 1) != 0;
                    vorzeichen = (bits & 8) != 0;
                    groesse = (int) zahl(o + 4, 4);
                }
                case 0x08 -> {
                    int v = u8(o);
                    if (v >= 3) {
                        art = u8(o + 1);
                        if (art == 1) {
                            adresse = adr(o + 2);
                            laenge = zahl(o + 2 + so, sl);
                        } else if (art == 0) {
                            int g = (int) zahl(o + 2, 2);
                            kompakt = new byte[g];
                            for (int k = 0; k < g; k++) kompakt[k] = b.get((int) (basis + o + 4 + k));
                        }
                    } else {
                        int dim = u8(o + 1);
                        art = u8(o + 2);
                        if (art == 1) adresse = adr(o + 8);
                        else if (art == 0) {
                            long p = o + 8 + 4L * dim;
                            int g = (int) zahl(p, 4);
                            kompakt = new byte[g];
                            for (int k = 0; k < g; k++) kompakt[k] = b.get((int) (basis + p + 4 + k));
                        }
                    }
                }
                case 0x0B -> gefiltert = true;
                default -> { }
            }
        }
        if (art < 0) return null;
        if (form == null) form = new long[0];
        String grund = art == 2 ? "ist gestückelt abgelegt" : art != 0 && art != 1 ? "hat das Layout " + art : gefiltert ? "ist gefiltert (gepackt)"
                : klasse != 0 && klasse != 1 ? "hat die Zahlenart " + klasse : null;
        return new Datensatz(name, form, klasse, groesse, gross, vorzeichen, adresse, laenge, kompakt, grund);
    }
}
