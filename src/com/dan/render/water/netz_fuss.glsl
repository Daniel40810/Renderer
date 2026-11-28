// netz_fuss.glsl – render.water, seit S3c-6 (Seegang Werkbuch 22, N6): der Fuß eines Bildpunkts auf dem Netz der Clipmap,
// aus netz.frag herausgelöst, damit der Vorlauf der Tiefe (netz_vorlauf.frag) und die Schattierung (netz.frag) dieselbe
// Regel rechnen. Wo am Ort kein Wasser ist (η_Bild) und kein Wasser nah (vNah aus netz.comp), fällt der Bildpunkt weg.
// B47 (S3c-5): weicht der Fuß des Netzes (η_Bild der Scheitel, gemischt) am Bildpunkt um mehr als FUSS_TOL von η_Bild ab
// (an Kanten zwischen zwei Spiegeln, an trockenen Scheiteln), sucht der Bildpunkt die Fläche η_Bild + η der Welle längs
// seines Strahls wie wasser.glsl (suche) und nimmt deren Tiefe. Braucht wasser.glsl (etaBild, treffen, netz, vorn).

const float FUSS_TOL = 0.02;   // Abweichung des Fußes, ab der der Bildpunkt die Fläche selbst sucht (m) [G]

// B47: feste Stelle t = treffen(d, η_Bild(d t) + w) ab dem Spiegel e0, höchstens 8 Schritte (wie suche in wasser.glsl);
// false ohne Treffer oder wo der Strahl ins Trockene führt
bool festeStelle(vec3 d, float e0, float w, out float t, out float e) {
    int g;
    e = e0;
    t = -1.0;
    for (int k = 0; k < 8; k++) {
        float tt = treffen(d, e + w);
        if (tt <= 0.0) return false;
        float en = etaBild(d.xz * tt, g);
        if (en == KEIN) return false;
        bool fest = abs(en - e) < 1e-4;
        e = en;
        if (fest) break;
    }
    t = treffen(d, e + w);
    return t > 0.0;
}

// Der Fuß am Bildpunkt: Ort P (relativ zum Auge), Richtung d, Abstand t, Spiegel η und Tiefe (umgekehrt; z ist
// gl_FragCoord.z); b47: der Fuß wich ab, der Bildpunkt suchte selbst (seit S3c-7 für den Zähler). false: der Bildpunkt
// fällt weg.
bool netzFuss(vec3 vP, float vEta, float vWelle, float vNah, float z, out vec3 P, out vec3 d, out float t, out float eta, out float tiefe, out bool b47) {
    b47 = false;
    P = vP;
    t = length(P);
    d = P / t;
    tiefe = z;
#ifdef SWASH_BINDUNG
    // seit S6c-2b (Seegang Werkbuch 83): im Rechteck des Auflaufs allein die stetige Fläche des Lösers (swNetz). Sie ist
    // überall bestimmt; weicht der Fuß ab, sucht der Bildpunkt auf seinem Strahl wie B47, aber ohne Nachbarn. Gezeichnet
    // wird, wo der Löser am Treffpunkt Wasser zeigt; sonst zeichnet das Gelände (Film, nasser Grund).
    bool zeigen;
    float fs = swNetz(P.xz, zeigen);
    if (fs != KEIN) {
        eta = fs;
        if (abs(fs - vEta) > FUSS_TOL) {
            b47 = true;
            float e = fs, tt = -1.0;
            for (int k = 0; k < 8; k++) {
                tt = treffen(d, e + vWelle);
                if (tt <= 0.0) return false;
                float en = swNetz(d.xz * tt, zeigen);
                if (en == KEIN) return false;
                bool fest = abs(en - e) < 1e-4;
                e = en;
                if (fest) break;
            }
            tt = treffen(d, e + vWelle);
            if (tt <= 0.0) return false;
            eta = e;
            t = tt;
            P = d * t;
            tiefe = vorn.w / (t * dot(d, vorn.xyz));
            swNetz(P.xz, zeigen);
        }
        return zeigen;
    }
#endif
    int gi;
    eta = etaBild(P.xz, gi);
    tiefe = z;
    if (eta == KEIN && vNah <= 0.0) return false;                       // trocken, weit vom Wasser
    if (eta == KEIN && messAus(MESS_B47)) return false;               // seit S9a: Messschalter (ohne die Suche)
    if (eta != KEIN && abs(eta - vEta) > FUSS_TOL && messAus(MESS_B47)) return true;
    if (eta == KEIN || abs(eta - vEta) > FUSS_TOL) {
        // B47: Der Fuß des Netzes passt hier nicht zu η_Bild (Kante zwischen zwei Spiegeln, trockener Scheitel). Der
        // Bildpunkt sucht die Fläche auf seinem Strahl selbst: ab η_Bild am Netz, sonst ab den Spiegeln der vier Nachbarn
        // im Abstand einer Zelle, der höchste (also nächste) zuerst.
        float tt = -1.0, e = KEIN;
        bool ok = eta != KEIN && festeStelle(d, eta, vWelle, tt, e);
        if (!ok && gi >= 0) {
            float s = netz[gi].f.z;
            int h;
            float c[4] = float[4](etaBild(P.xz + vec2(s, 0.0), h), etaBild(P.xz - vec2(s, 0.0), h), etaBild(P.xz + vec2(0.0, s), h), etaBild(P.xz - vec2(0.0, s), h));
            for (int a = 1; a < 4; a++)
                for (int b = a; b > 0 && c[b] > c[b - 1]; b--) { float q = c[b]; c[b] = c[b - 1]; c[b - 1] = q; }
            for (int a = 0; a < 4 && !ok; a++)
                if (c[a] != KEIN && c[a] != eta) ok = festeStelle(d, c[a], vWelle, tt, e);
        }
        b47 = true;
        if (!ok) return false;
        eta = e;
        t = tt;
        P = d * t;
        tiefe = vorn.w / (t * dot(d, vorn.xyz));
    }
    return true;
}

#include "/com/dan/render/water/netz_zaehler.glsl"
