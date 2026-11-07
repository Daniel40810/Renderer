// accumulate.frag — Kino-Stufe (com.dan.shutter): ein Teilbild in die Summe. Gewicht je Bildpunkt:
//  1 Zeitfenster: belichtet, wenn das Teilbild (Zeit uTau) zwischen Öffnen und Schließen dieses Bildpunkts liegt; das Öffnen
//    hängt vom Ort ab (Öffnungsgang der Umlaufblende: t_auf = atan2(x, y + R) / (2π · Bildrate) auf dem Film, der kopfüber
//    steht), Flanken so weich wie die Kante durch den Lichtkegel braucht (uRamp)
//  2 Katzenauge: der Ort in der Pupille muss in beiden Kreisen der Fassungen liegen, die mit dem Feldwinkel auswandern
// Ergebnis (additiv gemischt): rgb · w, a = w. resolve.frag teilt.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
uniform float uTau;             // Zeit des Teilbilds (s)
uniform float uOpen;            // Belichtungszeit T (s)
uniform int uRolling;
uniform vec2 uGate;             // Bildfenster (mm)
uniform float uGateR;           // Abstand der Fenstermitte von der Achse (mm)
uniform float uSweep;           // Drehung des Laufs (rad)
uniform float uFps;
uniform float uRamp;            // Flanke (s)
uniform vec2 uPupil;            // Ort in der Pupille (Fläche π; y mit der Stauchung)
uniform float uSqueeze;
uniform vec4 uCat;              // (ρ1, σ1, ρ2, σ2) im Maß der Pupille ohne Stauchung; ρ ≥ 50: aus
uniform float uTanPerPx;        // tan Feldwinkel je Bildpunkt von der Mitte (mit der Stauchung des Felds)
uniform vec2 uSize;

float openAt(vec2 n) {
    vec2 f = -n * uGate;
    float c = cos(uSweep), s = sin(uSweep);
    vec2 r = vec2(c * f.x - s * f.y, s * f.x + c * f.y);
    return atan(r.x, r.y + uGateR) / (6.2831853 * uFps);
}

void main() {
    vec2 n = vUV - 0.5;
    float a = uTau - (uRolling == 1 ? openAt(n) : 0.0);
    float rp = max(uRamp, 1e-7);
    float w = clamp(a / rp + 0.5, 0.0, 1.0) * clamp((uOpen - a) / rp + 0.5, 0.0, 1.0);
    if (w <= 0.0) discard;
    if (uCat.x < 50.0 || uCat.z < 50.0) {
        vec2 d = n * uSize;
        float r = length(d);
        vec2 er = r > 0.5 ? d / r : vec2(1.0, 0.0);
        float th = atan(r * uTanPerPx);
        vec2 q = vec2(uPupil.x, uPupil.y / uSqueeze);
        if (uCat.x < 50.0 && length(q - uCat.y * th * er) > uCat.x) discard;
        if (uCat.z < 50.0 && length(q - uCat.w * th * er) > uCat.z) discard;
    }
    vec3 c = max(texture(uHdr, vUV).rgb, vec3(0.0));
    frag = vec4(c * w, w);
}
