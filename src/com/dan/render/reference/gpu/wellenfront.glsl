// Referenz Stufe 2 als Wellenfront (P11a, Probe zu F112 A; Laine, Karras und Aila 2013): Derselbe Pfadverfolger wie
// pfad.glsl, aber jeder Teil der Schleife von verfolge() ist ein eigener kleiner Kernel. Der Zustand eines Pfads liegt
// zwischen den Aufrufen im Puffer Z (Felder hintereinander, je Feld N Werte: Z[k N + i]); jeder Faden hält einen Platz
// und rechnet dort nacheinander pc.pfade Pfade. Ein Kernel arbeitet nur an Plätzen in seiner Stufe:
//   K_NEU       Ende eines Pfads (Beitrag in die Ausgabe) und Beginn des nächsten, neue Wellenlängen
//   K_SCHNITT   Schnitt mit der Szene, Beginn des Flugs durch die Luft (oder Flug durch das Wasser)
//   K_FLUG      ein Stück Flug durch die Luft bis zum nächsten Stoß mit Auftrag oder Ereignis (flugSchritt)
//   K_EREIGNIS  ein Ereignis (Streuung, Boden, Fläche, Austritt) mit Russischem Roulette
//   K_AUFTRAG   die Aufträge zur Lichtquelle (erledige)
// Der Wirt ruft sie im Wechsel, bis alle Plätze fertig sind (Zaehler[0]). Was ein Kernel nicht ändert, liest er aus
// eigenen Puffern ohne Schreibzugriff (Wellenlängen ZW, Treffer ZT); so darf der Übersetzer die Werte erst bei Bedarf
// laden. Erwartet kopf.glsl, eine Szene und pfad.glsl mit WELLENFRONT (ohne dessen main).

#ifdef K_NEU
#define WQ
#else
#define WQ readonly
#endif
#ifdef K_SCHNITT
#define TQ
#else
#define TQ readonly
#endif
layout(std430, set = 0, binding = 4) restrict buffer ZustandB { float Z[]; };
layout(std430, set = 0, binding = 5) restrict buffer ZaehlerB { uint Zaehler[]; };
layout(std430, set = 0, binding = 6) restrict WQ buffer WelleB { float ZW[]; };
layout(std430, set = 0, binding = 7) restrict TQ buffer TrefferB { float ZT[]; };

#define ST_START 0
#define ST_SCHNITT 1
#define ST_FLUG 2
#define ST_EREIGNIS 3
#define ST_AUFTRAG 4
#define ST_ENDE 5
#define ST_FERTIG 6

// Felder des Zustands
#define Z_STUFE 0
#define Z_EV 1
#define Z_FLAG 2
#define Z_RS 3
#define Z_TIEFE 4
#define Z_SCHRITTE 5
#define Z_NR 6
#define Z_NAUF 7
#define Z_P 8
#define Z_D 11
#define Z_W 14
#define Z_L 18
#define Z_FA2 22
#define Z_TEREIGNIS 23
#define Z_ANTEILR 24
#define Z_FLUGF 28
#define Z_TRUEB 34
#define Z_AORT 35
#define Z_BORT 39
#define Z_A0 40
#define Z_A1 56
#define Z_N 72
// ZW: lam, ray, ozo, aW, son, mon, nac (28 Felder); ZT: t, n, art, albedo, sigma2 (9 Felder)
#define ZW_N 28
#define ZT_N 9

uint zi;            // Platz dieses Fadens
int zn;             // Plätze

float zl(int k) { return Z[k * zn + int(zi)]; }
void zs(int k, float v) { Z[k * zn + int(zi)] = v; }
int zli(int k) { return floatBitsToInt(zl(k)); }
void zsi(int k, int v) { zs(k, intBitsToFloat(v)); }
vec3 zl3(int k) { return vec3(zl(k), zl(k + 1), zl(k + 2)); }
void zs3(int k, vec3 v) { zs(k, v.x); zs(k + 1, v.y); zs(k + 2, v.z); }
vec4 zl4(int k) { return vec4(zl(k), zl(k + 1), zl(k + 2), zl(k + 3)); }
void zs4(int k, vec4 v) { zs(k, v.x); zs(k + 1, v.y); zs(k + 2, v.z); zs(k + 3, v.w); }

bool weiter;

// Merker: ereignis, imFlug, hit, weiter, getaucht, grund, nanPfad, abbruch, fStoss, imWasser
int merker() {
    int m = (ereignis ? 1 : 0) | (imFlug ? 2 : 0) | (hit ? 4 : 0) | (weiter ? 8 : 0) | (getaucht ? 16 : 0) | (grund ? 32 : 0)
          | (nanPfad ? 64 : 0) | (abbruch ? 128 : 0) | (fStoss ? 256 : 0);
#ifndef OHNE_WASSER
    if (imWasser) m |= 512;
#endif
    return m;
}

void setzeMerker(int m) {
    ereignis = (m & 1) != 0; imFlug = (m & 2) != 0; hit = (m & 4) != 0; weiter = (m & 8) != 0; getaucht = (m & 16) != 0;
    grund = (m & 32) != 0; nanPfad = (m & 64) != 0; abbruch = (m & 128) != 0; fStoss = (m & 256) != 0;
#ifndef OHNE_WASSER
    imWasser = (m & 512) != 0;
#endif
}

Auftrag ladeAuftrag(int k) {
    Auftrag a;
    a.o = zl3(k); a.r = zl3(k + 3); a.ls = zl3(k + 6); a.f = zl4(k + 9); a.pm = zl(k + 13);
    a.art = int(zl(k + 14)); a.quelle = zl(k + 15) > 0.5;
    return a;
}

void sichereAuftrag(int k, Auftrag a) {
    zs3(k, a.o); zs3(k + 3, a.r); zs3(k + 6, a.ls); zs4(k + 9, a.f); zs(k + 13, a.pm);
    zs(k + 14, float(a.art)); zs(k + 15, a.quelle ? 1.0 : 0.0);
}

float wl(int k) { return ZW[k * zn + int(zi)]; }
vec4 wl4(int k) { return vec4(wl(k), wl(k + 1), wl(k + 2), wl(k + 3)); }
float tl(int k) { return ZT[k * zn + int(zi)]; }

void ladeWelle() {
    if (!spektral) return;                                                // Kanäle: wellenKanaele() in lies()
    lam = wl4(0); ray = wl4(4); ozo = wl4(8); aW = wl4(12); son = wl4(16); mon = wl4(20); nac = wl4(24);
}

void ladeTreffer() {
    TR.t = tl(0); TR.n = vec3(tl(1), tl(2), tl(3)); TR.art = int(tl(4)); TR.albedo = vec3(tl(5), tl(6), tl(7)); TR.sigma2 = tl(8);
}

#ifdef K_NEU
void ws4(int k, vec4 v) { ZW[k * zn + int(zi)] = v.x; ZW[(k + 1) * zn + int(zi)] = v.y; ZW[(k + 2) * zn + int(zi)] = v.z; ZW[(k + 3) * zn + int(zi)] = v.w; }
void sichereWelle() { ws4(0, lam); ws4(4, ray); ws4(8, ozo); ws4(12, aW); ws4(16, son); ws4(20, mon); ws4(24, nac); }
#endif

#ifdef K_SCHNITT
void ts(int k, float v) { ZT[k * zn + int(zi)] = v; }
void sichereTreffer() {
    ts(0, TR.t); ts(1, TR.n.x); ts(2, TR.n.y); ts(3, TR.n.z); ts(4, float(TR.art)); ts(5, TR.albedo.x); ts(6, TR.albedo.y); ts(7, TR.albedo.z); ts(8, TR.sigma2);
}
#endif

void ladeFlug() { fT = zl(Z_FLUGF); fE = zl(Z_FLUGF + 1); fEnde = zl(Z_FLUGF + 2); fMj = zl(Z_FLUGF + 3); fC0 = zl(Z_FLUGF + 4); fB = zl(Z_FLUGF + 5); }
void sichereFlug() { zs(Z_FLUGF, fT); zs(Z_FLUGF + 1, fE); zs(Z_FLUGF + 2, fEnde); zs(Z_FLUGF + 3, fMj); zs(Z_FLUGF + 4, fC0); zs(Z_FLUGF + 5, fB); }

/** Die nächste Stufe nach einem Schritt (wie die Bedingungen der Schleife in verfolge). */
int naechsteStufe() {
    if (abbruch) return ST_ENDE;
    if (nAuftrag > 0) return ST_AUFTRAG;
    if (!weiter) return ST_ENDE;
    return imFlug ? ST_FLUG : ST_SCHNITT;
}

/** Plätze und Werte der Szene; false, wenn der Faden keinen Platz hat. */
bool beginne() {
    zi = gl_GlobalInvocationID.x;
    zn = pc.faeden;
    if (zi >= uint(zn)) return false;
    lies();
    return true;
}


/** Wasserzustand (Trübung am Ort). */
void ladeTruebung() {
#ifndef OHNE_WASSER
    truebungAkt = zl(Z_TRUEB); aOrt = zl4(Z_AORT); bOrt = zl(Z_BORT);
#endif
}

void sichereTruebung() {
#ifndef OHNE_WASSER
    zs(Z_TRUEB, truebungAkt); zs4(Z_AORT, aOrt); zs(Z_BORT, bOrt);
#endif
}

// ================================================================== K_NEU
#ifdef K_NEU
/** Beginn eines Pfads wie in main und verfolge von pfad.glsl. */
void neuerPfad() {
    schritte = 0;
    abbruch = false;
    nanPfad = false;
    neueWellen();
    vec3 p0 = vec3(W[W_P0], W[W_P0 + 1], W[W_P0 + 2]), d0 = vec3(W[W_DIR], W[W_DIR + 1], W[W_DIR + 2]);
    if (pc.modus == 0) {
        int px = int(zi) % max(1, pc.breite), py = int(zi) / max(1, pc.breite);
        float nx = (float(px) + zufall()) / float(pc.breite) * 2.0 - 1.0, ny = (float(py) + zufall()) / float(pc.hoehe) * 2.0 - 1.0;
        vec3 b0 = vec3(W[W_CAM], W[W_CAM + 1], W[W_CAM + 2]), b1 = vec3(W[W_CAM + 3], W[W_CAM + 4], W[W_CAM + 5]), b2 = vec3(W[W_CAM + 6], W[W_CAM + 7], W[W_CAM + 8]);
        p0 = vec3(0.0);
        d0 = normalize(b0 * nx + b1 * ny + b2);
    }
    w = aktiv;
    L = vec4(0.0);
    getaucht = false;
    grund = false;
    fa2 = alpha2Basis;
    P = p0;
    D = d0;
#ifndef OHNE_WASSER
    imWasser = false;
#endif
    ereignis = false;
    imFlug = false;
    hit = false;
    fStoss = false;
    nAuftrag = 0;
    tiefe = 0;
    weiter = true;
    truebungAkt = -1.0;
}

/** Ende eines Pfads: Beitrag in die Ausgabe dieses Platzes (wie main in pfad.glsl). */
void schliessePfad(int nr) {
    int base = int(zi) * 24;
    vec4 Lp = L;
    if (abbruch || nanPfad) {
        A[base + 23] += abbruch ? 1.0 : 0.0;
        A[base + 22] += nanPfad ? 1.0 : 0.0;
        Lp = vec4(0.0);
    }
    vec3 v = pc.modus <= 1 ? sammle(Lp) : Lp.xyz;
    if (pc.modus == 0) {
        int o = ((pc.pfadBasis + nr) & 1) == 0 ? 0 : 3;
        A[base + o] += v.x; A[base + o + 1] += v.y; A[base + o + 2] += v.z;
    } else {
        float m = dot(Lp, aktiv) / float(NB);
        A[base + 0] += v.x; A[base + 1] += v.y; A[base + 2] += v.z;
        A[base + 3] += v.x * v.x; A[base + 4] += v.y * v.y; A[base + 5] += v.z * v.z;
        A[base + 6] += m; A[base + 7] += m * m; A[base + 8] += 1.0;
    }
}

void main() {
    if (!beginne()) return;
    int stufe = zli(Z_STUFE);
    if (stufe != ST_START && stufe != ST_ENDE) return;
    int nr = 0;
    if (stufe == ST_START) rs = pcgHash(zi ^ pcgHash(uint(pc.runde) * 2654435769u ^ pcgHash(pc.saat)));
    else {
        nr = zli(Z_NR);
        setzeMerker(zli(Z_FLAG));
        ladeWelle();
        L = zl4(Z_L);
        schliessePfad(nr);
        atomicAdd(Zaehler[1], 1u);                                         // fertige Pfade (Dauerrate)
        nr++;
        zsi(Z_NR, nr);
        if (nr >= pc.pfade) {
            zsi(Z_STUFE, ST_FERTIG);
            atomicAdd(Zaehler[0], 1u);
            return;
        }
        rs = uint(zli(Z_RS));
    }
    neuerPfad();
    sichereWelle();
    zsi(Z_NR, nr);
    zsi(Z_STUFE, ST_SCHNITT);
    zsi(Z_RS, int(rs));
    zsi(Z_SCHRITTE, schritte);
    zsi(Z_FLAG, merker());
    zsi(Z_TIEFE, tiefe);
    zsi(Z_NAUF, 0);
    zs3(Z_P, P); zs3(Z_D, D); zs4(Z_W, w); zs4(Z_L, L);
    zs(Z_FA2, fa2);
    sichereTruebung();
}
#endif

// ================================================================== K_SCHNITT
#ifdef K_SCHNITT
void main() {
    if (!beginne() || zli(Z_STUFE) != ST_SCHNITT) return;
    schritte = zli(Z_SCHRITTE);
    setzeMerker(zli(Z_FLAG));
    tiefe = zli(Z_TIEFE);
    P = zl3(Z_P); D = zl3(Z_D); w = zl4(Z_W);
    nAuftrag = 0;
    int stufe;
    if (wache()) stufe = ST_ENDE;
    else if (tiefe >= maxTiefe) { weiter = false; stufe = ST_ENDE; }
    else if (any(isnan(w)) || any(isinf(w)) || any(isnan(P)) || any(isnan(D))) { nanPfad = true; weiter = false; stufe = ST_ENDE; }
    else {
        hit = schneide(P, D, imWasser ? wasserWeite : INF, TR);        // der einzige Schnitt des Pfads
        sichereTreffer();
        int ev = -1;
#ifndef OHNE_WASSER
        if (imWasser) {
            rs = uint(zli(Z_RS));
            ladeWelle();
            ladeTruebung();
            if (!hit && wasserWeite >= INF) weiter = false;
            else {
                ev = fliegeWasser(hit ? TR.t : wasserWeite);
                if (!hit && ev == ENDE) { weiter = false; ev = -1; }
            }
            zsi(Z_RS, int(rs));
            zs4(Z_W, w);
            zs(Z_TEREIGNIS, tEreignis);
        } else
#endif
        {
            fEnde = min(hit ? TR.t : INF, bisOben(P, D));
            fC0 = cVon(P);
            fB = bVon(P, D);
            fT = 0.0;
            fStoss = (jederStoss && !ofen) || einfachSchaetzer;
            ladeWelle();                                                  // die Majorante braucht ray, ozo
            imFlug = flugAbschnitt();
            sichereFlug();
            if (!imFlug) { tEreignis = fEnde; ev = ENDE; zs(Z_TEREIGNIS, tEreignis); }
        }
        if (ev >= 0) { zsi(Z_EV, ev); stufe = ST_EREIGNIS; }
        else stufe = naechsteStufe();
    }
    zsi(Z_STUFE, stufe);
    zsi(Z_SCHRITTE, schritte);
    zsi(Z_FLAG, merker());
}
#endif

// ================================================================== K_FLUG
#ifdef K_FLUG
void main() {
    if (!beginne() || zli(Z_STUFE) != ST_FLUG) return;
    rs = uint(zli(Z_RS));
    schritte = zli(Z_SCHRITTE);
    setzeMerker(zli(Z_FLAG));
    P = zl3(Z_P); D = zl3(Z_D); w = zl4(Z_W);
    ladeFlug();
    ladeWelle();
    nAuftrag = 0;
    int r = flugSchritt();
    int stufe;
    if (r == STOSS) stufe = ST_AUFTRAG;
    else {
        imFlug = false;
        stufe = ST_EREIGNIS;
        zsi(Z_EV, r);
        zs(Z_TEREIGNIS, tEreignis);
        zs4(Z_ANTEILR, anteilR);
    }
    if (abbruch) stufe = ST_ENDE;
    zsi(Z_STUFE, stufe);
    zsi(Z_RS, int(rs));
    zsi(Z_SCHRITTE, schritte);
    zsi(Z_FLAG, merker());
    zs4(Z_W, w);
    zs(Z_FLUGF, fT); zs(Z_FLUGF + 1, fE); zs(Z_FLUGF + 3, fMj);
    zsi(Z_NAUF, nAuftrag);
    if (nAuftrag > 0) sichereAuftrag(Z_A0, A0);
}
#endif

// ================================================================== K_EREIGNIS
#ifdef K_EREIGNIS
void main() {
    if (!beginne() || zli(Z_STUFE) != ST_EREIGNIS) return;
    rs = uint(zli(Z_RS));
    schritte = zli(Z_SCHRITTE);
    setzeMerker(zli(Z_FLAG));
    tiefe = zli(Z_TIEFE);
    nAuftrag = zli(Z_NAUF);                                                // ein Stoß kann einen Auftrag und ein Ereignis geben
    if (nAuftrag > 0) A0 = ladeAuftrag(Z_A0);
    P = zl3(Z_P); D = zl3(Z_D); w = zl4(Z_W); L = zl4(Z_L);
    fa2 = zl(Z_FA2);
    tEreignis = zl(Z_TEREIGNIS);
    anteilR = zl4(Z_ANTEILR);
    ladeTruebung();
    ladeTreffer();
    ladeWelle();
    weiter = ereignisBehandeln(zli(Z_EV));
    if (weiter) {
        if (tiefe >= 8) {                                                 // Russisches Roulette
            float q = max4(abs(w));
            if (q < 0.25) {
                if (zufall() >= q / 0.25) weiter = false;
                else w /= q / 0.25;
            }
        }
        tiefe++;
    }
    zsi(Z_STUFE, naechsteStufe());
    zsi(Z_RS, int(rs));
    zsi(Z_FLAG, merker());
    zsi(Z_TIEFE, tiefe);
    zsi(Z_NAUF, nAuftrag);
    zs3(Z_P, P); zs3(Z_D, D); zs4(Z_W, w); zs4(Z_L, L);
    zs(Z_FA2, fa2);
    sichereTruebung();
    if (nAuftrag > 0) sichereAuftrag(Z_A0, A0);
    if (nAuftrag > 1) sichereAuftrag(Z_A1, A1);
}
#endif

// ================================================================== K_AUFTRAG
#ifdef K_AUFTRAG
void main() {
    if (!beginne() || zli(Z_STUFE) != ST_AUFTRAG) return;
    schritte = zli(Z_SCHRITTE);
    setzeMerker(zli(Z_FLAG));
    nAuftrag = zli(Z_NAUF);
    A0 = ladeAuftrag(Z_A0);
    if (nAuftrag > 1) A1 = ladeAuftrag(Z_A1);
    L = zl4(Z_L);
    ladeWelle();
    ladeTruebung();
    int k = 0;
    [[dont_unroll]] while (nAuftrag > 0 && k < 4) {                       // höchstens zwei Aufträge, je einmal gewandelt
        if (wache()) break;
        erledige();
        k++;
    }
    zsi(Z_NAUF, nAuftrag);
    if (nAuftrag > 0) sichereAuftrag(Z_A0, A0);
    zs4(Z_L, L);
    zsi(Z_SCHRITTE, schritte);
    zsi(Z_FLAG, merker());
    zsi(Z_STUFE, naechsteStufe());
}
#endif
