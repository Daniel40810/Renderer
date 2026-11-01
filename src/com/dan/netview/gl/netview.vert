// netview.vert — eine Strecke, Pfeilspitze oder ein Punkt je Instanz, als Viereck in Bildpunkten um die Weltlage
// (Breite bleibt gleich, egal wie weit weg). Isolinien erscheinen, sobald die Zeit ihren Wert erreicht (die vorderste
// leuchtet), Strahlen wachsen von der Quelle her, Bänder nehmen die Farbe der Rampe aus dem Wert.
layout(location = 0) in vec4 iP0;   // x, y, z, Art (0 Strecke, 1 Pfeilspitze, 2 Punkt, 3 Isolinie, 4 Strahl, 5 Band)
layout(location = 1) in vec4 iP1;   // x, y, z, Breite (px)
layout(location = 2) in vec4 iCol;  // r, g, b, a
layout(location = 3) in vec4 iVal;  // Wert am Anfang, am Ende, Rampe an (1), frei
uniform mat4 uVP;
uniform vec2 uViewport;
uniform float uTime, uFront, uDepthBias;
uniform vec3 uRamp[8];
uniform int uRampN;
uniform vec2 uRampRange;
out vec4 vCol;
out vec2 vLocal;
flat out int vKind;

vec3 ramp(float v) {
    float t = clamp((v - uRampRange.x) / max(1e-6, uRampRange.y - uRampRange.x), 0.0, 1.0) * float(uRampN - 1);
    int i = min(uRampN - 2, int(floor(t)));
    return mix(uRamp[i], uRamp[i + 1], t - float(i));
}

vec4 clipNear(vec4 a, vec4 b) {
    // a liegt hinter dem Auge: auf die Ebene w = 0,01 ziehen
    if (a.w >= 0.01) return a;
    float t = (0.01 - a.w) / (b.w - a.w);
    return mix(a, b, t);
}

void main() {
    int kind = int(iP0.w + 0.5);
    vKind = kind;
    int c = gl_VertexID & 3;
    vec2 corner = vec2(float(c & 1), (c & 2) != 0 ? 1.0 : -1.0);
    vec3 p0 = iP0.xyz, p1 = iP1.xyz;
    float width = iP1.w;
    vCol = iCol;
    float hidden = 0.0;
    if (kind == 3) {
        if (iVal.x > uTime) hidden = 1.0;
        float age = uTime - iVal.x;
        if (age < uFront) { width *= 1.8; vCol.rgb = mix(vCol.rgb, vec3(1.0), 0.6 * (1.0 - age / uFront)); }
    }
    if (kind == 4) {
        if (iVal.x > uTime) hidden = 1.0;
        float f = clamp((uTime - iVal.x) / max(1e-6, iVal.y - iVal.x), 0.0, 1.0);
        p1 = mix(p0, p1, f);
    }
    float v = mix(iVal.x, iVal.y, corner.x);
    if (iVal.z > 0.5) vCol.rgb = ramp(v);
    vec4 a = uVP * vec4(p0, 1.0), b = uVP * vec4(p1, 1.0);
    if ((a.w < 0.01 && b.w < 0.01) || hidden > 0.5) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vLocal = vec2(0.0); return; }
    a = clipNear(a, b);
    b = clipNear(b, a);
    vec2 na = a.xy / a.w * 0.5 * uViewport, nb = b.xy / b.w * 0.5 * uViewport;
    vec2 d = nb - na;
    float L = length(d);
    vec2 dir = L > 1e-4 ? d / L : vec2(1.0, 0.0), nrm = vec2(-dir.y, dir.x);
    vec2 px;
    vec4 base;
    if (kind == 2) {
        base = a;
        px = vec2((corner.x * 2.0 - 1.0), corner.y) * width * 0.5;
        vLocal = vec2(corner.x * 2.0 - 1.0, corner.y);
    } else if (kind == 1) {
        base = b;
        float s = width * 3.5;
        // Spitze vorn, zwei Ecken hinten; die vierte Ecke fällt auf die Spitze
        px = c == 0 ? -dir * s + nrm * s * 0.55 : (c == 1 ? -dir * s - nrm * s * 0.55 : vec2(0.0));
        vLocal = vec2(0.0);
    } else {
        base = corner.x < 0.5 ? a : b;
        px = nrm * corner.y * width * 0.5 + dir * (corner.x * 2.0 - 1.0) * width * 0.5;
        vLocal = vec2(corner.x, corner.y);
    }
    gl_Position = base + vec4(px / (0.5 * uViewport) * base.w, 0.0, 0.0);
    gl_Position.z -= uDepthBias * gl_Position.w;
}
