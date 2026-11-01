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
 * Spiegel auf dem Prozessor ({@link ForestGpu#cullCpu}, über {@link com.dan.forest.ForestCulture}) liefert. Dazu:
 * kein Baum auf einem Platz „fort“, umgelegte Bäume in ihrer Fallrichtung, Atlanten der Impostors gefüllt, Zähler stimmig.
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
        for (int v = 0; v < 4; v++) {
            ForestGl.View cam = ForestGpuDemo.camera(v, 1920, 1080);
            f.update(gl, cam);
            int[] st = f.readStats(gl);
            float[][] gpu = f.readInstances(gl), cpu = f.cullCpu(cam.viewProj, cam.eye);
            long ng = 0, nc = 0, matched = 0, binsOff = 0, gone = 0, fellOff = 0;
            double worst = 0;
            long sum = 0;
            for (int b = 0; b < gpu.length; b++) sum += st[2 + b];
            ForestCulture cu = f.culture();
            TreeStates ts = cu.states;
            int T = f.types(), SV = f.species() * f.variants();
            for (int b = 0; b < gpu.length; b++) {
                ng += gpu[b].length / 8;
                nc += cpu[b].length / 8;
                if (gpu[b].length != cpu[b].length) binsOff++;
                Map<Long, Integer> m = new HashMap<>();
                for (int k = 0; k < cpu[b].length; k += 8) m.put(key(cpu[b][k], cpu[b][k + 2]), k);
                for (int k = 0; k < gpu[b].length; k += 8) {
                    float x = gpu[b][k], z = gpu[b][k + 2];
                    int i = (int) Math.floor(x / cu.spacing), j = (int) Math.floor(z / cu.spacing);
                    int state = ts == null ? 0 : ts.state(i, j);
                    if (state == TreeStates.GONE) gone++;
                    int type = b % T;
                    if (type / SV != state) fellOff++;
                    if (state == TreeStates.FELLED) {
                        float want = ForestCulture.yawToward((float) ts.fallDeg(i, j));
                        double dy = Math.abs(Math.atan2(Math.sin(gpu[b][k + 3] - want), Math.cos(gpu[b][k + 3] - want)));
                        if (dy > 0.151) fellOff++;
                    }
                    Integer kc = m.get(key(x, z));
                    if (kc == null) continue;
                    matched++;
                    for (int e = 0; e < 8; e++) worst = Math.max(worst, Math.abs(cpu[b][kc + e] - gpu[b][k + e]));
                }
            }
            check(String.format(Locale.GERMAN, "%s: Grafikkarte %,d Bäume = Prozessor %,d, alle gleich (%,d), Eimer abweichend %d, größte Abweichung %.1e",
                    ForestGpuDemo.VIEWS[v], ng, nc, matched, binsOff, worst), ng == nc && matched == ng && binsOff == 0 && worst < 1e-3 && ng > 1000);
            check(String.format(Locale.GERMAN, "%s: Zähler (%,d) = Summe der Eimer (%,d), nichts verworfen (%d)", ForestGpuDemo.VIEWS[v], st[0], sum, st[1]),
                    st[0] == sum && st[1] == 0);
            check(String.format(Locale.GERMAN, "%s: kein Baum auf einem Platz „fort“ (%d), Typ passt zum Zustand und umgelegte zeigen in ihre Fallrichtung (%d Abweichungen)",
                    ForestGpuDemo.VIEWS[v], gone, fellOff), gone == 0 && fellOff == 0);
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

    static long key(float x, float z) { return ((long) Float.floatToIntBits(x) << 32) ^ (Float.floatToIntBits(z) & 0xffffffffL); }

    static void check(boolean ok, String what) { check(what, ok); }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) bad++;
    }
}
