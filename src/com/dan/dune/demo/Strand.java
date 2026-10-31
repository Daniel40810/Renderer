package com.dan.dune.demo;

import com.dan.dune.Dune;
import com.dan.dune.FluxField;
import com.dan.dune.SandBed;

/**
 * Die Bühne der Vorführung (ohne Sylt-Code): ein Strand von West nach Ost, der vom Wasser bei x = 0 mit 1 : 40 ansteigt,
 * nasser Sand bis zum Auflauf, dann trocken; bei x = 110 m eine Vordüne, 7 m hoch, Luv flach (≈ 12°), Lee steil
 * (≈ 30°), oben Strandhafer in Büscheln; dahinter eine Senke. Die Düne läuft von Nord nach Süd mit einer leichten Welle.
 */
public final class Strand {
    /** Nässe des Strands nach Regen (Massenanteil, 0 = trocken bis auf den Saum). */
    public double rain;
    /** Wo der Auflauf endet (m von der Wasserlinie). */
    public double swashTop = 22;
    public double duneX = 110, duneH = 7;

    /** Lage des Kamms bei z (m von der Wasserlinie). */
    public double crest(double z) { return duneX + 6 * Math.sin(z / 37.0) + 3 * Math.sin(z / 11.0 + 1); }

    public double height(double x, double z) {
        double c = crest(z), base = Math.max(-0.5, Math.min(x, c - 40) / 40.0), h;
        if (x < c - 40) h = 0;
        else if (x < c) { double s = (x - (c - 40)) / 40; h = duneH * s * s * (3 - 2 * s); }
        else if (x < c + 3) h = duneH + 0.15 * Math.sin(Math.PI * (x - c) / 3);
        else if (x < c + 15) h = duneH * (1 - (x - c - 3) / 12) - 0.8 * (x - c - 3) / 12;
        else h = -0.8;
        return base + h;
    }

    public double moisture(double x, double z) {
        double saum = x < swashTop ? 0.20 : 0.20 * Math.exp(-(x - swashTop) / 5.0) + 0.0003;
        return Math.max(saum, rain);
    }

    public double cover(double x, double z) {
        double c = crest(z);
        if (x < c - 12 || Math.sin(z / 19.0) > 0.25) return 0;   // Windrisse ohne Halme: dort weht der Kamm aus
        // Büschel: Rauschen aus Sinus, oben dichter
        double tuft = 0.5 + 0.5 * Math.sin(x * 0.9 + Math.sin(z * 0.7) * 2) * Math.sin(z * 1.3 + x * 0.2);
        double dense = Math.max(0, Math.min(1, (x - (c - 12)) / 10));
        return 0.10 * dense * Math.max(0, tuft - 0.25);
    }

    /** Hängt die Bühne an das Dune-Objekt. */
    public Dune wire(Dune d) {
        d.ground = this::height;
        d.moisture = this::moisture;
        d.cover = this::cover;
        return d;
    }

    /**
     * Das Sandbett der Bühne für den Zeitraffer (seit Phase 9): fester Grund 2 m unter dem Sand (am Wasser kein loser
     * Sand), Strandhafer, wo {@link #cover} ihn hat, in Zellen zu {@code cell} m, Platten zu 5 cm.
     */
    public SandBed bed(Dune d, double cell) {
        SandBed b = new SandBed(d, 80, 0, 170, 120, cell, 0.05, (x, z) -> height(x, z) - (x > 0 ? 2 : 0), this::height);
        b.plant(this::cover, 1);
        b.sea((x, z) -> x < crest(z) - 40 ? 1 : 0);   // den flachen Strand hält das Meer
        return b;
    }

    /** Ein Feld über die Bühne: Mitte bei x = 80, z = 0, in Windrichtung 170 m, quer 120 m. */
    public FluxField field(Dune d, double cell) {
        return new FluxField(d, 80, 0, 170, 120, cell).compute();
    }
}
