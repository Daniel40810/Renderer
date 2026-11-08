// glied.frag — Licht je Bildpunkt auf dem Zylinder (Normale aus der Lage quer über das Band), mit der Schattenkarte; dann
// Band und Stufe. So zerfällt das Sonnenlicht auf dem Stamm in Flecken mit der Form der Lücken im Laub.
in vec3 vAchsePunkt;
in float vU;
flat in vec3 vAchse, vQuer, vAlbedo;
in float vR;
uniform int uModus;
layout(location = 0) out vec4 oFarbe;
layout(location = 1) out float oY;

void main() {
    if (uModus == 1) return;
    vec3 vorn = uAuge - vAchsePunkt;
    vec3 n0 = normalize(vorn - dot(vorn, vAchse) * vAchse);
    float u = clamp(vU, -1.0, 1.0);
    vec3 n = normalize(sqrt(max(0.0, 1.0 - u * u)) * n0 + u * vQuer);
    vec3 p = vAchsePunkt + n * vR;
    vec3 rgb = licht(p, n, vAlbedo, false, -1.0);
    float y = lum(rgb);
    int band = bandRoh(log2(max(y, 1e-12)));
    oFarbe = vec4(farbeZu(stufeZu(rgb, band, true), band), 1.0);
    oY = y;
}
