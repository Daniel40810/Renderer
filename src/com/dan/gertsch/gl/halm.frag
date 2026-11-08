// halm.frag — Licht je Bildpunkt, die Normale zum Blick und etwas nach oben, durchscheinend; dann Band und Stufe.
in vec3 vP;
flat in vec3 vAlbedo;
layout(location = 0) out vec4 oFarbe;
layout(location = 1) out float oY;

void main() {
    vec3 n = normalize(normalize(uAuge - vP) + vec3(0.0, 0.3, 0.0));
    vec3 rgb = licht(vP, n, vAlbedo, true, -1.0);
    float y = lum(rgb);
    int band = bandRoh(log2(max(y, 1e-12)));
    oFarbe = vec4(farbeZu(stufeZu(rgb, band, true), band), 1.0);
    oY = y;
}
