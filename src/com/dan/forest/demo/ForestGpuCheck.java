package com.dan.forest.demo;

import com.dan.forest.ForestCulture;
import com.dan.forest.TreeStates;
import com.dan.forest.gl.ForestGl;
import com.dan.forest.gl.ForestGpu;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prüfung von {@link ForestGpu} auf der Grafikkarte (seit LituyaBay1958 Phase 6), ohne Fenster: in der Bucht aus
 * {@link BayScene} rechnet die Grafikkarte die Kultur für vier Ansichten; jede Instanz muss genau die sein, die der
 * Spiegel auf dem Prozessor ({@link ForestGpu#cullCpu}, über {@link com.dan.forest.ForestCulture}) liefert, zugeordnet über
 * den Platz; Lage auf 5 cm, Winkel auf 0,05 rad (die Grafikkarte rundet anders: FMA, atan). Dazu:
 * kein Baum auf einem Platz „fort“, umgelegte Bäume in ihrer Fallrichtung, Atlanten der Impostors gefüllt, Zähler stimmig;
 * seit LituyaBay1958 Phase 14 ein fünfter Blick mit Nachwuchs ({@link TreeStates#setGrowth}): Art und Größe wie vorgegeben;
 * seit W6 Runde 3 der Nahschnitt ({@link ForestGpu#nearCut}, {@link ForestGpu#nearTrees}).
 * Endet mit „Alles in Ordnung.“
 */
public final class ForestGpuCheck {
    static int bad;

    public static void main(String[] a) {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, 64, 64);
        dr.display();
        int[] r = new int[1];
        dr.invoke(true, (GLAutoDrawable d) -> { r[0] = run(d.getGL().getGL4()); return true; });
        dr.destroy();
        System.exit(r[0] == 0 ? 0 : 1);
    }

    /** Die Prüfung im GL-Faden; gibt die Zahl der Fehler zurück. */
    public static int run(GL4 gl) {
        bad = 0;
        System.out.println("Grafikkarte: " + gl.glGetString(GL.GL_RENDERER));
        BayScene sc = new BayScene(ForestGpu.Quality.MEDIUM);
        ForestGpu f = sc.forest;
        f.init(gl);
        f.setSeason(190, 0);
        f.awaitBake();
        for (int v = 0; v < 5; v++) {
            if (v == 4) {
                // seit LituyaBay1958 Phase 14: Nachwuchs im Band (Erlen auf jedem zweiten Platz „fort“, ein Drittel groß)
                TreeStates ts0 = f.culture().states;
                for (int j = ts0.j0; j < ts0.j0 + ts0.h; j++)
                    for (int i = ts0.i0; i < ts0.i0 + ts0.w; i++)
                        if (ts0.state(i, j) == TreeStates.GONE && ((i + j) & 1) == 0) { ts0.set(i, j, TreeStates.STANDING, 0); ts0.setGrowth(i, j, 2, 0.33); }
            }
            ForestGl.View cam = ForestGpuDemo.camera(v % 4, 1920, 1080);
            f.update(gl, cam);
            int[] st = f.readStats(gl);
            float[][] gpu = f.readInstances(gl), cpu = f.cullCpu(cam.viewProj, cam.eye);
            long ng = 0, nc = 0, matched = 0, binsOff = 0, gone = 0, fellOff = 0;
            double worstPos = 0, worstAng = 0, worstRest = 0;
            long nach = 0, nachOff = 0;
            long sum = 0;
            for (int b = 0; b < gpu.length; b++) sum += st[2 + b];
            ForestCulture cu = f.culture();
            TreeStates ts = cu.states;
            int T = f.types(), SV = f.species() * f.variants();
            for (int b = 0; b < gpu.length; b++) {
                ng += gpu[b].length / 8;
                nc += cpu[b].length / 8;
                if (gpu[b].length != cpu[b].length) binsOff++;
                // Zuordnung über den Platz (i, j), nicht über die genauen Bits: die Grafikkarte rundet anders (FMA, atan)
                Map<Long, Integer> m = new HashMap<>();
                for (int k = 0; k < cpu[b].length; k += 8) m.put(slot(cpu[b][k], cpu[b][k + 2], cu.spacing), k);
                for (int k = 0; k < gpu[b].length; k += 8) {
                    float x = gpu[b][k], z = gpu[b][k + 2];
                    int i = (int) Math.floor(x / cu.spacing), j = (int) Math.floor(z / cu.spacing);
                    int state = ts == null ? 0 : ts.state(i, j);
                    if (state == TreeStates.GONE) gone++;
                    int type = b % T;
                    int gr = ts == null ? 0 : ts.growth(i, j);
                    if (gr != 0) {
                        nach++;
                        float gs = TreeStates.growthSize(gr) * (float) cu.sizeFactor[(gr & 15) - 1];
                        if ((type % SV) / f.variants() != (gr & 15) - 1 || gpu[b][k + 4] > 1.07f * gs + 1e-4f || gpu[b][k + 4] < 0.62f * gs - 1e-4f) nachOff++;
                    }
                    if (type / SV != state) fellOff++;
                    if (state == TreeStates.FELLED) {
                        float want = ForestCulture.yawToward((float) ts.fallDeg(i, j));
                        double dy = Math.abs(Math.atan2(Math.sin(gpu[b][k + 3] - want), Math.cos(gpu[b][k + 3] - want)));
                        if (dy > 0.2) fellOff++;
                    }
                    Integer kc = m.get(slot(x, z, cu.spacing));
                    if (kc == null) continue;
                    matched++;
                    for (int e = 0; e < 8; e++) {
                        double d = Math.abs(cpu[b][kc + e] - gpu[b][k + e]);
                        if (e == 3 || e == 6) d = Math.abs(Math.atan2(Math.sin(d), Math.cos(d)));
                        if (e <= 2) worstPos = Math.max(worstPos, d); else if (e == 3 || e == 6) worstAng = Math.max(worstAng, d); else worstRest = Math.max(worstRest, d);
                    }
                }
            }
            check(String.format(Locale.GERMAN, "%s: Grafikkarte %,d Bäume = Prozessor %,d, je Platz zugeordnet %,d, Eimer abweichend %d; größte Abweichung Lage %.1e m, Winkel %.1e rad, sonst %.1e",
                    name(v), ng, nc, matched, binsOff, worstPos, worstAng, worstRest), ng == nc && matched == ng && binsOff == 0 && worstPos < 0.05 && worstAng < 0.05 && worstRest < 1e-3 && ng > 1000);
            if (v == 4) check(String.format(Locale.GERMAN, "%s: %,d Bäume aus dem Nachwuchs, Art und Größe wie vorgegeben (%d Abweichungen)", name(v), nach, nachOff), nach > 100 && nachOff == 0);
            check(String.format(Locale.GERMAN, "%s: Zähler (%,d) = Summe der Eimer (%,d), nichts verworfen (%d)", name(v), st[0], sum, st[1]),
                    st[0] == sum && st[1] == 0);
            check(String.format(Locale.GERMAN, "%s: kein Baum auf einem Platz „fort“ (%d), Typ passt zum Zustand und umgelegte zeigen in ihre Fallrichtung (%d Abweichungen)",
                    name(v), gone, fellOff), gone == 0 && fellOff == 0);
        }
        // seit LituyaBay1958 W6 Runde 3: Nahschnitt. Das Auge steht 2 m über einem Baum im Blick; was der Schnitt der
        // Grafikkarte wegnimmt, liefert nearTrees, und kein Baum steht doppelt.
        {
            ForestGl.View cam = ForestGpuDemo.camera(0, 1920, 1080);
            float[][] ohne = f.cullCpu(cam.viewProj, cam.eye);
            float[] fuss = null;
            for (float[] bin : ohne) if (bin.length >= 8) { fuss = bin; break; }
            double[] eye = {fuss[0] + 3, fuss[1] + 2, fuss[2] + 3};
            cam.eye = eye;
            ohne = f.cullCpu(cam.viewProj, eye);
            f.nearCut = new double[f.species()];
            java.util.Arrays.fill(f.nearCut, 35);
            f.update(gl, cam);
            float[][] gpu = f.readInstances(gl), mit = f.cullCpu(cam.viewProj, eye);
            java.util.List<float[]> nah = new java.util.ArrayList<>();
            f.nearTrees(eye, nah);
            java.util.Set<Long> nahSlots = new java.util.HashSet<>(), gpuSlots = new java.util.HashSet<>(), mitSlots = new java.util.HashSet<>();
            for (float[] t : nah) nahSlots.add(slot(t[2], t[4], f.culture().spacing));
            long gpuNah = 0, fehlt = 0, weg = 0, naeher = 0;
            for (float[] bin : gpu) for (int k = 0; k < bin.length; k += 8) {
                long sl = slot(bin[k], bin[k + 2], f.culture().spacing);
                gpuSlots.add(sl);
                if (nahSlots.contains(sl)) gpuNah++;
                double dx = bin[k] - eye[0], dy = bin[k + 1] + 8 - eye[1], dz = bin[k + 2] - eye[2];
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) < 35 - f.nearBand / 2 - 0.01) naeher++;
            }
            for (float[] bin : mit) for (int k = 0; k < bin.length; k += 8) mitSlots.add(slot(bin[k], bin[k + 2], f.culture().spacing));
            for (float[] bin : ohne) for (int k = 0; k < bin.length; k += 8) {
                long sl = slot(bin[k], bin[k + 2], f.culture().spacing);
                if (mitSlots.contains(sl)) continue;
                weg++;
                if (!nahSlots.contains(sl)) fehlt++;
            }
            check(String.format(Locale.GERMAN, "Nahschnitt 35 m: %,d Bäume aus dem Blick genommen, alle in nearTrees (%d fehlen, %,d geliefert); keiner davon auf der Grafikkarte (%d), keiner der Grafikkarte näher als der Schnitt (%d); Grafikkarte = Prozessor (%,d / %,d)",
                    weg, fehlt, nah.size(), gpuNah, naeher, gpuSlots.size(), mitSlots.size()), weg > 0 && fehlt == 0 && gpuNah == 0 && naeher == 0 && gpuSlots.equals(mitSlots));
            f.nearCut = null;
        }
        // Atlanten: Deckung je Schicht (Anteil der Bildpunkte mit Baum)
        double[] cov = f.atlasCoverage(gl);
        double lo = 1, hi = 0;
        for (double c : cov) { lo = Math.min(lo, c); hi = Math.max(hi, c); }
        check(String.format(Locale.GERMAN, "Atlanten der Impostors: %d Schichten zu %d² (%d × %d Ansichten), Deckung %.0f bis %.0f %%",
                cov.length, f.framePx() * ForestGpu.FRAMES, ForestGpu.FRAMES, ForestGpu.FRAMES, lo * 100, hi * 100), cov.length > 0 && lo > 0.03 && hi < 0.9);
        System.out.println(f.info());
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        f.dispose(gl);
        return bad;
    }

    static String name(int v) { return ForestGpuDemo.VIEWS[v % 4] + (v == 4 ? " mit Nachwuchs" : ""); }

    static long slot(float x, float z, double sp) { return ((long) Math.floor(x / sp) << 32) ^ ((long) Math.floor(z / sp) & 0xffffffffL); }

    static void check(boolean ok, String what) { check(what, ok); }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) bad++;
    }
}
