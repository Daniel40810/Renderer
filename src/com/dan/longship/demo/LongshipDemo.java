package com.dan.longship.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.longship.Longship;
import com.dan.longship.ShipType;
import com.dan.longship.Water;
import com.dan.longship.gl.LongshipGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eigene Vorführung (Paketvertrag Punkt 3 und 10), ohne Haithabu-Code: die drei Typen auf einer einfachen Dünung.
 * <ul>
 * <li><b>typen:</b> Langschiff unter Riemen, Knorr unter Segel, Faering daneben – alle fahren langsam im Kreis.</li>
 * <li><b>anlegen:</b> die Knorr segelt an, birgt das Segel, rudert an einen Steg (ein Balken) und macht fest; danach von vorn.</li>
 * <li><b>nah:</b> das Langschiff von schräg vorn: Klinker, Schilde, Riemen, Tierkopf.</li>
 * </ul>
 * Fenster: Ziehen dreht, Rad zoomt. Ohne Fenster: {@code LongshipDemo --bild datei.png [typen|anlegen|nah] [breite höhe] [sekunden]}.
 */
public final class LongshipDemo implements GLEventListener {
    final LongshipGl gl = new LongshipGl();
    final List<Longship> ships = new ArrayList<>();
    volatile String scene = "typen";
    volatile double windFrom = 255, wave = 0.25, yaw, pitch, dist = 1, sunAz = 200;
    volatile boolean rebuild = true;
    double time, frameMs;
    long last;
    int waterProg, waterVao;
    final Water swell = (x, z) -> wave * (Math.sin(x * 0.21 + time * 1.1) * 0.6 + Math.sin(z * 0.17 - x * 0.05 + time * 0.8) * 0.4);

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            LongshipDemo d = new LongshipDemo();
            d.scene = a.length > 2 ? a[2] : "typen";
            int w = a.length > 3 ? Integer.parseInt(a[3]) : 1280, h = a.length > 4 ? Integer.parseInt(a[4]) : 720;
            double secs = a.length > 5 ? Double.parseDouble(a[5]) : 20;
            d.build();
            d.rebuild = false;
            for (double t = 0; t < secs; t += 0.05) d.advance(0.05);
            GLProfile p = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(p);
            caps.setOnscreen(false);
            caps.setFBO(true);
            caps.setDepthBits(24);
            GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, w, h);
            d.last = -1;
            dr.addGLEventListener(d);
            dr.display();
            dr.display();
            BufferedImage[] img = new BufferedImage[1];
            dr.invoke(true, x -> { img[0] = grab(x.getGL().getGL4(), w, h); return true; });
            javax.imageio.ImageIO.write(img[0], "png", new File(a[1]));
            System.out.println("Bild: " + a[1] + " · " + d.gl.info());
            for (Longship s : d.ships) System.out.println("  " + s.describe());
            dr.destroy();
            System.exit(0);
        }
        SwingUtilities.invokeLater(() -> {
            LongshipDemo d = new LongshipDemo();
            FFrame f = new FFrame("Longship-Package · Schiffe der Wikingerzeit");
            JPanel root = f.getComponentPane();
            root.setLayout(new BorderLayout());
            root.setBackground(new Color(15, 19, 17));
            GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
            caps.setDepthBits(24);
            GLCanvas c = new GLCanvas(caps);
            c.addGLEventListener(d);
            root.add(c, BorderLayout.CENTER);
            MouseAdapter ma = new MouseAdapter() {
                int lx, ly;
                @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
                @Override public void mouseDragged(MouseEvent e) {
                    d.yaw += (e.getX() - lx) * 0.3;
                    d.pitch = Math.max(-25, Math.min(60, d.pitch + (e.getY() - ly) * 0.2));
                    lx = e.getX(); ly = e.getY();
                }
                @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { d.dist = Math.max(0.2, Math.min(4, d.dist * Math.pow(1.1, e.getWheelRotation()))); }
            };
            c.addMouseListener(ma);
            c.addMouseMotionListener(ma);
            c.addMouseWheelListener(ma);
            FComboBox sc = new FComboBox(new Object[]{"typen", "anlegen", "nah"});
            FSlider wind = new FSlider(0, 359, 255), wv = new FSlider(0, 100, 25), sun = new FSlider(0, 359, 200);
            JLabel lw = new JLabel("Wind aus 255°"), lv = new JLabel("Dünung 0,25 m"), ls = new JLabel("Sonne aus 200°"), info = new JLabel(" ");
            for (JLabel l : new JLabel[]{lw, lv, ls, info}) l.setForeground(new Color(232, 230, 222));
            sc.addActionListener(e -> { d.scene = (String) sc.getSelectedItem(); d.yaw = d.pitch = 0; d.dist = 1; d.rebuild = true; });
            wind.addChangeListener(e -> { d.windFrom = wind.getValue(); lw.setText("Wind aus " + wind.getValue() + "°"); });
            wv.addChangeListener(e -> { d.wave = wv.getValue() / 100.0; lv.setText(String.format(Locale.GERMAN, "Dünung %.2f m", d.wave)); });
            sun.addChangeListener(e -> { d.sunAz = sun.getValue(); ls.setText("Sonne aus " + sun.getValue() + "°"); });
            JPanel south = new JPanel(new java.awt.GridBagLayout());
            south.setBackground(new Color(15, 19, 17));
            south.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
            java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
            g.insets = new java.awt.Insets(2, 6, 2, 6);
            g.fill = java.awt.GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            java.awt.Component[][] grid = {{new JLabel(" "), lw, lv, ls}, {sc, wind, wv, sun}};
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 4; x++) {
                    java.awt.Component k = grid[y][x];
                    k.setPreferredSize(new java.awt.Dimension(200, y == 0 ? 22 : 34));
                    g.gridx = x; g.gridy = y;
                    south.add(k, g);
                }
            info.setPreferredSize(new java.awt.Dimension(400, 22));
            g.gridx = 0; g.gridy = 2; g.gridwidth = 4;
            south.add(info, g);
            root.add(south, BorderLayout.SOUTH);
            c.setPreferredSize(new java.awt.Dimension(1200, 640));
            c.setMinimumSize(new java.awt.Dimension(200, 150));
            new javax.swing.Timer(500, e -> {
                StringBuilder sb = new StringBuilder(String.format(Locale.GERMAN, "%.1f ms · %s", d.frameMs, d.gl.info()));
                for (Longship s : d.ships) sb.append("   ·   ").append(s.describe());
                info.setText(sb.toString());
            }).start();
            f.setPreferredFrameSize(new java.awt.Dimension(1280, 860));
            f.setSize(1280, 860);
            f.setBackground(new Color(15, 19, 17));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Animator(c).start();
        });
    }

    // ------------------------------------------------------------------ Szenen

    /** Steg der Szene „anlegen“: Liegeplatz und Kurs. */
    static final double[] BERTH = {0, -40, 0};

    void build() {
        ships.clear();
        time = 0;
        switch (scene) {
            case "anlegen" -> {
                Longship k = new Longship(ShipType.KNORR, "Knorr");
                k.place(-160, 140, 45);
                k.sailSet = k.sailTarget = 1;
                ships.add(k);
                Longship f = new Longship(ShipType.FAERING, "Faering");
                f.mooredAt(BERTH[0] + 8, BERTH[1] + 2, 0);
                ships.add(f);
            }
            case "nah" -> {
                Longship l = new Longship(ShipType.LANGSCHIFF, "Langschiff");
                l.place(0, 0, 70);
                ships.add(l);
            }
            default -> {
                double[][] at = {{-30, 0, 80}, {12, 22, 60}, {28, -14, 100}};
                ShipType[] ts = {ShipType.LANGSCHIFF, ShipType.KNORR, ShipType.FAERING};
                for (int i = 0; i < 3; i++) {
                    Longship s = new Longship(ts[i], ts[i].label.split(" ")[0]);
                    s.place(at[i][0], at[i][1], at[i][2]);
                    if (ts[i] == ShipType.KNORR) s.sailSet = s.sailTarget = 1;
                    ships.add(s);
                }
            }
        }
        for (Longship s : ships) s.water = swell;
        orders();
    }

    /** Neue Befehle, wenn ein Schiff fertig ist. */
    void orders() {
        for (Longship s : ships) {
            s.windFromDeg = windFrom;
            s.windSpeed = 6;
            if (!s.commands.isEmpty()) continue;
            switch (scene) {
                case "anlegen" -> {
                    if (s.type != ShipType.KNORR) continue;
                    // nach dem Festmachen und einer Pause von vorn
                    if (s.mode != Longship.Mode.TREIBEN || s.x > -100) { s.place(-160, 140, 45); s.sailSet = s.sailTarget = 1; }
                    s.commands.add(Longship.Command.sailTo(-50, 40, 15));
                    s.commands.add(Longship.Command.sail(0));
                    s.commands.add(Longship.Command.moor(BERTH[0], BERTH[1], BERTH[2]));
                    s.commands.add(Longship.Command.waitFor(15));
                }
                case "nah" -> { }
                default -> {
                    // ein Kreis um die Mitte: Segelschiff segelt (Rahsegel dreht mit), die anderen rudern
                    double r = 60, a = Math.atan2(s.x, -s.z) + 0.5;
                    double tx = Math.sin(a) * r, tz = -Math.cos(a) * r;
                    s.commands.add(s.type == ShipType.KNORR ? Longship.Command.sailTo(tx, tz, 10) : Longship.Command.rowTo(tx, tz, 10));
                }
            }
        }
    }

    void advance(double dt) {
        if (rebuild) { rebuild = false; build(); }
        time += dt;
        orders();
        for (Longship s : ships) {
            if (scene.equals("nah")) {
                // im Stand rudern: Riemen arbeiten, das Schiff bleibt im Bild
                if (s.commands.isEmpty()) s.commands.add(Longship.Command.rowTo(s.x + 1000 * s.forward()[0], s.z + 1000 * s.forward()[1], 5));
                s.step(dt);
                s.x = 0; s.z = 0; s.heading = 70;
            } else s.step(dt);
        }
    }

    // ------------------------------------------------------------------ GL

    static final String WATER_VS = """
            layout(location = 0) in vec2 aXZ;
            uniform mat4 uVP;
            uniform float uTime, uWave;
            out vec3 vW;
            float h(vec2 p) { return uWave * (sin(p.x * 0.21 + uTime * 1.1) * 0.6 + sin(p.y * 0.17 - p.x * 0.05 + uTime * 0.8) * 0.4); }
            void main() { vec3 w = vec3(aXZ.x, h(aXZ), aXZ.y); vW = w; gl_Position = uVP * vec4(w, 1.0); }
            """;
    static final String WATER_FS = """
            in vec3 vW;
            out vec4 frag;
            uniform vec3 uCam, uSunDir, uSky, uFog;
            uniform float uTime, uWave;
            float h(vec2 p) { return uWave * (sin(p.x * 0.21 + uTime * 1.1) * 0.6 + sin(p.y * 0.17 - p.x * 0.05 + uTime * 0.8) * 0.4); }
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() {
                float e = 0.2;
                vec2 p = vW.xz;
                float r1 = sin(p.x * 2.1 + p.y * 1.3 + uTime * 2.3) * 0.02 + sin(p.y * 2.7 - p.x * 0.7 + uTime * 1.9) * 0.02;
                vec3 n = normalize(vec3(h(p - vec2(e, 0)) - h(p + vec2(e, 0)) + r1, 2.0 * e, h(p - vec2(0, e)) - h(p + vec2(0, e)) + r1 * 0.7));
                vec3 v = normalize(uCam - vW);
                float fr = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
                vec3 deep = vec3(0.015, 0.045, 0.05);
                vec3 rf = reflect(-v, n);
                vec3 sky = mix(uFog, uSky * 2.4, clamp(rf.y * 2.0, 0.0, 1.0));
                vec3 c = mix(deep * (0.6 + 0.6 * max(uSunDir.y, 0.0)), sky, fr);
                c += vec3(3.0, 2.8, 2.4) * pow(max(dot(rf, uSunDir), 0.0), 400.0) * 3.0;
                float d = length(uCam - vW);
                c = mix(c, uFog, 1.0 - exp(-d / 2500.0));
                frag = vec4(pow(aces(c * 0.95), vec3(1.0 / 2.2)), 1.0);
            }
            """;

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        waterProg = program(g, WATER_VS, WATER_FS);
        // Gitter 240 × 240 Zellen über 1,2 km, fein genug für die Dünung
        int n = 240;
        double half = 600;
        float[] v = new float[(n + 1) * (n + 1) * 2];
        int k = 0;
        for (int j = 0; j <= n; j++) for (int i = 0; i <= n; i++) { v[k++] = (float) (-half + 2 * half * i / n); v[k++] = (float) (-half + 2 * half * j / n); }
        int[] ix = new int[n * n * 6];
        k = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int a = j * (n + 1) + i, b = a + 1, c = a + n + 1, e = c + 1;
                ix[k++] = a; ix[k++] = c; ix[k++] = b; ix[k++] = b; ix[k++] = c; ix[k++] = e;
            }
        int[] t = new int[3];
        g.glGenVertexArrays(1, t, 0);
        g.glGenBuffers(2, t, 1);
        waterVao = t[0];
        g.glBindVertexArray(waterVao);
        g.glBindBuffer(GL.GL_ARRAY_BUFFER, t[1]);
        g.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, com.jogamp.common.nio.Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        g.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, t[2]);
        g.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ix.length * 4L, com.jogamp.common.nio.Buffers.newDirectIntBuffer(ix), GL.GL_STATIC_DRAW);
        g.glEnableVertexAttribArray(0);
        g.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 8, 0);
        g.glBindVertexArray(0);
        waterCount = ix.length;
    }

    int waterCount;

    @Override public void display(GLAutoDrawable d) {
        long now = System.nanoTime();
        if (last > 0) advance(Math.min(0.1, (now - last) / 1e9));
        else if (rebuild) advance(0);
        last = now;
        GL4 g = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        double az = Math.toRadians(sunAz), el = Math.toRadians(30);
        double[] sunDir = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        double[] fogLin = {0.95, 1.08, 1.25}, skyLin = {0.30, 0.37, 0.48};
        float[] clr = tone(fogLin, 0.95);
        g.glClearColor(clr[0], clr[1], clr[2], 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        double[] eye, at;
        switch (scene) {
            case "anlegen" -> { Longship k = ships.get(0); at = new double[]{(k.x + BERTH[0]) / 2, 2, (k.z + BERTH[1]) / 2}; double r = Math.max(45, Math.hypot(k.x - BERTH[0], k.z - BERTH[1]) * 0.9); eye = new double[]{at[0] + r * 0.6, r * 0.35, at[2] + r * 0.8}; }
            case "nah" -> { eye = new double[]{16, 4.2, -14}; at = new double[]{3, 1.6, -1}; }
            default -> { eye = new double[]{0, 22, 105}; at = new double[]{0, 2, 0}; }
        }
        eye = orbit(eye, at);
        float[] vp = mul(persp(48, w / (double) h, 0.3, 6000), look(eye, at));
        // Wasser
        g.glUseProgram(waterProg);
        g.glUniformMatrix4fv(g.glGetUniformLocation(waterProg, "uVP"), 1, false, vp, 0);
        g.glUniform1f(g.glGetUniformLocation(waterProg, "uTime"), (float) time);
        g.glUniform1f(g.glGetUniformLocation(waterProg, "uWave"), (float) wave);
        g.glUniform3f(g.glGetUniformLocation(waterProg, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        g.glUniform3f(g.glGetUniformLocation(waterProg, "uSunDir"), (float) sunDir[0], (float) sunDir[1], (float) sunDir[2]);
        g.glUniform3f(g.glGetUniformLocation(waterProg, "uSky"), (float) skyLin[0], (float) skyLin[1], (float) skyLin[2]);
        g.glUniform3f(g.glGetUniformLocation(waterProg, "uFog"), (float) fogLin[0], (float) fogLin[1], (float) fogLin[2]);
        g.glBindVertexArray(waterVao);
        g.glDrawElements(GL.GL_TRIANGLES, waterCount, GL.GL_UNSIGNED_INT, 0);
        g.glBindVertexArray(0);
        // Schiffe
        LongshipGl.View v = new LongshipGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.tonemap = true;
        v.exposure = 0.95;
        LongshipGl.Light l = new LongshipGl.Light();
        l.sunDir = sunDir;
        l.sunColor = new double[]{2.7, 2.45, 2.1};
        l.skyColor = skyLin;
        l.fogColor = fogLin;
        l.fogDistance = 2500;
        l.waterY = 0;
        gl.draw(g, ships, v, l);
        frameMs = frameMs * 0.9 + (System.nanoTime() - now) / 1e6 * 0.1;
    }

    double[] orbit(double[] eye, double[] at) {
        double dx = eye[0] - at[0], dy = eye[1] - at[1], dz = eye[2] - at[2];
        double r0 = Math.sqrt(dx * dx + dy * dy + dz * dz), r = r0 * dist, a = Math.atan2(dx, dz) + Math.toRadians(yaw);
        double e = Math.asin(dy / r0) + Math.toRadians(pitch);
        e = Math.max(Math.toRadians(1), Math.min(Math.toRadians(85), e));
        return new double[]{at[0] + r * Math.cos(e) * Math.sin(a), at[1] + r * Math.sin(e), at[2] + r * Math.cos(e) * Math.cos(a)};
    }

    static float[] tone(double[] c, double exp) {
        float[] o = new float[3];
        for (int i = 0; i < 3; i++) {
            double x = c[i] * exp;
            double y = Math.max(0, Math.min(1, (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14)));
            o[i] = (float) Math.pow(y, 1 / 2.2);
        }
        return o;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }

    static int program(GL4 g, String vs, String fs) {
        int p = g.glCreateProgram();
        for (int[] s : new int[][]{{GL4.GL_VERTEX_SHADER, 0}, {GL4.GL_FRAGMENT_SHADER, 1}}) {
            int sh = g.glCreateShader(s[0]);
            g.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + (s[1] == 0 ? vs : fs)}, null, 0);
            g.glCompileShader(sh);
            int[] ok = new int[1];
            g.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                g.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Wasser-Shader: " + new String(b, 0, len[0]));
            }
            g.glAttachShader(p, sh);
        }
        g.glLinkProgram(p);
        return p;
    }

    static BufferedImage grab(GL4 g, int w, int h) {
        ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        g.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                im.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return im;
    }

    // kleine Matrizen (Spaltenreihenfolge), damit die Demo ohne andere Packages auskommt
    static float[] persp(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect); m[5] = (float) t; m[10] = (float) ((f + n) / (n - f)); m[11] = -1; m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] look(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sz = fx, sl = Math.hypot(sx, sz);
        sx /= sl; sz /= sl;
        double ux = -sz * fy, uy = sz * fx - sx * fz, uz = sx * fy;
        float[] m = new float[16];
        m[0] = (float) sx; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * e[0] + sz * e[2]); m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]); m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) { float s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k]; r[c * 4 + row] = s; }
        return r;
    }
}
