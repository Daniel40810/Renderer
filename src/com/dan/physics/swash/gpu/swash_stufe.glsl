// swash_stufe.glsl — der Körper von swash_stufe.comp (Kachel 16 × 16, seit S6e) und swash_stufe8.comp (16 × 8, bis S6d).
// Vor dem Einbinden: KACHEL_Y, VERSCHMOLZEN, die Gruppe und swash_gemein.glsl. Beschreibung in swash_stufe.comp.

layout(push_constant) uniform Schritt { float dt; float t; float w; int stufe; int slotA; int slotB; int paritaet; int bild; } P;   // bild seit S6c-2b: 1 = Stufe 2 schreibt das Bild

const int TX = 16, TY = KACHEL_Y, N = TX * TY;
const int W3 = TX + 6, H3 = TY + 6, W2 = TX + 4, H2 = TY + 4, WFX = TX + 3, HFX = TY + 2, WFY = TX + 2, HFY = TY + 3, W1 = TX + 2, H1 = TY + 2;

shared vec4 sU[W3 * H3];                 // (h, hu, hv, Grund) für die Zellen I0 − 3 … I0 + TX + 2
shared vec4 sS[W2 * H2], sT[W2 * H2];    // Kanten in x und y für I0 − 2 … I0 + TX + 1 (seit Swash 0.3; die Steigungen der Geschwindigkeit rechnen die Kanten aus sU)
shared vec4 sFX[WFX * HFX], sFY[WFY * HFY];
shared int sMarke;
// seit S6e (Seegang Werkbuch 85): θ liegt in sT[n].x (Rand 1, W1 × H1 ≤ W2 × H2), sobald die Kanten in y gelesen sind –
// so bleibt die Kachel 16 × 16 unter 32 kB geteilten Speichers (zwei Gruppen je CU)

int I0, J0;
int r3(int i, int j) { return (j - J0 + 3) * W3 + (i - I0 + 3); }
int r2(int i, int j) { return (j - J0 + 2) * W2 + (i - I0 + 2); }
int ifx(int a, int j) { return (j - J0 + 1) * WFX + (a - I0 + 1); }
int ify(int i, int b) { return (b - J0 + 1) * WFY + (i - I0 + 1); }
int ith(int i, int j) { return (j - J0 + 1) * W1 + (i - I0 + 1); }

float thX(int a, int j, float m) { int geber = m > 0.0 ? a - 1 : a; return geber < 0 || geber >= nx() ? 1.0 : sT[ith(geber, j)].x; }
float thY(int i, int b, float m) { int geber = m > 0.0 ? b - 1 : b; return geber < 0 || geber >= ny() ? 1.0 : sT[ith(i, geber)].x; }

void main() {
    int tx = int(gl_WorkGroupID.x), ty = int(gl_WorkGroupID.y), l = int(gl_LocalInvocationIndex);
    int ntx = int(gl_NumWorkGroups.x), nty = int(gl_NumWorkGroups.y), nt = ntx * nty;
    I0 = tx * TX;
    J0 = ty * TY;
    int alt = P.paritaet * nt, neu = (1 - P.paritaet) * nt, kach = ty * ntx + tx;
    int li = l % TX, lj = l / TX, i = I0 + li, j = J0 + lj;
    bool innen = i < nx() && j < ny();
    bildSchreiben = P.bild;                                               // seit S6c-2b: 1 die Zellen schreiben (letzter Schritt eines Bilds)

    // aktiv? (gleich für die ganze Gruppe)
    bool aktiv = (tx == 0 && W.I[I_RAND + SW] != WAND) || (tx == ntx - 1 && W.I[I_RAND + SE] != WAND)
            || (ty == 0 && W.I[I_RAND + SS] != WAND) || (ty == nty - 1 && W.I[I_RAND + SN] != WAND);
    for (int dy = -1; dy <= 1; dy++)
        for (int dx = -1; dx <= 1; dx++) {
            int ax = tx + dx, ay = ty + dy;
            if (ax >= 0 && ax < ntx && ay >= 0 && ay < nty && (kachel[alt + ay * ntx + ax] & 1) != 0) aktiv = true;
        }
    if (l == 0) sMarke = 0;
    barrier();

    if (!aktiv) {
        // trocken: der Stand bleibt; einmal in den Zielpuffer kopieren, wenn die beiden Puffer hier noch nicht gleich sind
        // (Bit 4); das Sickerwasser zieht ab (Stufe 2), wie Swash.versickern für trockene Zellen
        int marke = kachel[alt + kach];
        bool kopie = innen && (marke & 4) == 0;
        if (kopie) { int c = id(i, j); uAus[c] = u[c]; }
        // seit S6c-2b: im letzten Schritt des Bilds folgt das Bild der Kopie (Stufe 1: der Stand des vorigen Schritts bleibt;
        // Stufe 2: der Stand der Stufe 1 wird ohne Rechnung der neue)
        if (kopie && bildSchreiben != 0 && !(P.stufe == 2 && (marke & 2) != 0)) {
            int c = id(i, j);
            vec4 z = zelle[c];
            if (!(z.x >= FELS)) { vec4 sp4 = spur[c]; bildZelle(i, j, u[c].x, z.x, sp4.z, sp4.x); }
        }
        if (P.stufe == 2 && (marke & 2) != 0 && innen) {
            int c = id(i, j);
            vec4 z = zelle[c];
            vec4 sp4 = spur[c];
            if (!(z.x >= FELS) && z.z > 0.0 && z.x - z.w > 0.0 && sp4.x > 0.0) {
                sp4.x = max(0.0, sp4.x - z.z * P.dt);
                spur[c] = sp4;
            }
            if (sp4.x > 0.0) atomicOr(sMarke, 2);
            if (bildSchreiben != 0 && !(z.x >= FELS)) bildZelle(i, j, u[c].x, z.x, sp4.z, sp4.x);   // seit S6c-2b
        } else if (P.stufe == 1 && (marke & 2) != 0) {
            if (l == 0) atomicOr(sMarke, 2);
        }
        barrier();
        if (l == 0) kachel[neu + kach] = sMarke | 4;
        return;
    }

    // 1 Zustand und Grund mit Rand 3
    for (int n = l; n < W3 * H3; n += N) {
        int ci = I0 - 3 + n % W3, cj = J0 - 3 + n / W3;
        if (ci >= -G && ci < nx() + G && cj >= -G && cj < ny() + G) { int c = id(ci, cj); sU[n] = vec4(u[c].xyz, zelle[c].x); }
        else sU[n] = vec4(0.0, 0.0, 0.0, 2.0 * FELS);
    }
    barrier();
    // 2 Kanten und Steigungen mit Rand 2 (seit Swash 0.3: kantenZelle)
    for (int n = l; n < W2 * H2; n += N) {
        int ci = I0 - 2 + n % W2, cj = J0 - 2 + n / W2, c = r3(ci, cj);
        vec4 kx, ky;
        kantenZelle(sU[c], sU[c - 1], sU[c + 1], sU[c - W3], sU[c + W3], kx, ky);
        sS[n] = kx;
        sT[n] = ky;
    }
    barrier();
    // 3 Kanten mit Rand 1 (nur Kanten des Gebiets; die übrigen brauchen nur θ von Randzellen, und das ist 1)
    for (int n = l; n < WFX * HFX; n += N) {
        int a = I0 - 1 + n % WFX, cj = J0 - 1 + n / WFX;
        if (a >= 0 && a <= nx() && cj >= 0 && cj < ny()) {
            int ka = r3(a - 1, cj), kc = ka + 1;
            sFX[n] = kante(sU[ka], sS[r2(a - 1, cj)].yw, geschwSteigung(sU[ka], sU[ka - 1], sU[kc]), sU[kc], sS[r2(a, cj)].xz, geschwSteigung(sU[kc], sU[ka], sU[kc + 1]));
        }
        else sFX[n] = vec4(0.0);
    }
    for (int n = l; n < WFY * HFY; n += N) {
        int ci = I0 - 1 + n % WFY, b = J0 - 1 + n / WFY;
        if (ci >= 0 && ci < nx() && b >= 0 && b <= ny()) {
            vec4 ua = sU[r3(ci, b - 1)], uc = sU[r3(ci, b)];
            int ka = r3(ci, b - 1), kc = ka + W3;
            sFY[n] = kante(ua.xzyw, sT[r2(ci, b - 1)].yw, geschwSteigung(ua, sU[ka - W3], uc).yx, uc.xzyw, sT[r2(ci, b)].xz, geschwSteigung(uc, ua, sU[kc + W3]).yx);
        } else sFY[n] = vec4(0.0);
    }
    barrier();
    // seit S6e: die eigenen Kanten in Register, dann ist sT frei für θ
    vec4 kxE = sS[r2(i, j)], kyE = sT[r2(i, j)];
    barrier();
    // 4 θ mit Rand 1 (Swash.abfluss)
    float k = P.dt / W.F[F_DX];
    for (int n = l; n < W1 * H1; n += N) {
        int ci = I0 - 1 + n % W1, cj = J0 - 1 + n / W1;
        float th = 1.0;
        if (ci >= 0 && ci < nx() && cj >= 0 && cj < ny()) {
            vec4 uc = sU[r3(ci, cj)];
            if (!(uc.w >= FELS)) {
                float aus = k * (max(0.0, sFX[ifx(ci + 1, cj)].x) + max(0.0, -sFX[ifx(ci, cj)].x) + max(0.0, sFY[ify(ci, cj + 1)].x) + max(0.0, -sFY[ify(ci, cj)].x));
                th = aus > uc.x ? uc.x / aus : 1.0;
            }
        }
        sT[n].x = th;
    }
    barrier();
    // 5 die Stufe der eigenen Zelle
    int marke = 0;
    if (innen) {
        vec4 uc = sU[r3(i, j)];
        int c = id(i, j);
        if (!(uc.w >= FELS)) {
            vec4 xl = sFX[ifx(i, j)], xr = sFX[ifx(i + 1, j)], yd = sFY[ify(i, j)], yu = sFY[ify(i, j + 1)];
            xl.xy *= thX(i, j, xl.x);
            xr.xy *= thX(i + 1, j, xr.x);
            yd.xy *= thY(i, j, yd.x);
            yu.xy *= thY(i, j + 1, yu.x);
            vec2 e = stufeZelle(i, j, c, xl, xr, yd, yu, kxE, kyE, vec4(uc.xyz, 0.0), P.dt, P.t, P.stufe);
            if (e.x > W.F[F_HTR]) marke |= 1;
            if (P.stufe == 2 ? e.y > 0.0 : (kachel[alt + kach] & 2) != 0) marke |= 2;
        } else uAus[c] = vec4(0.0);
    }
    if (marke != 0) atomicOr(sMarke, marke);
    barrier();
    if (l == 0) kachel[neu + kach] = sMarke;
}
