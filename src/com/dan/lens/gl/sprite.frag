// sprite.frag — der Unschärfekreis eines Lichts: Form der Blende mit Katzenauge, Farbsäume aus dem Längsfarbfehler,
// Helligkeit nach der sphärischen Aberration; die Energie des Überschusses verteilt auf die Fläche des Kreises
// (π·(c/2)², halbe Bildpunkte). Ein schärferer Vordergrund verdeckt ein Licht dahinter (com.dan.lens). Addiert.
in vec2 vOff;
flat in vec4 vSpot;
flat in vec3 vRgb;
out vec4 frag;
uniform sampler2D uHalf;
void main() {
    vec2 posFull = vSpot.xy * 2.0;
    vec2 d = posFull - 0.5 * uFull;
    float th = atan(length(d) * uPitch / uEfl * uField);
    vec2 er = length(d) > 0.5 ? normalize(d) : vec2(1.0, 0.0);
    vec3 dc = vec3(-uDz.x, 0.0, -uDz.y) / uFNumber / (2.0 * uPitch);
    vec3 o = vec3(0.0);
    for (int ch = 0; ch < 3; ch++) {
        float cc = vSpot.z + dc[ch];
        float ac = max(abs(cc), 0.75);
        vec2 q = -2.0 * vOff / (sign(cc + 1e-6) * ac);        // p − s = vOff
        float cov = pupil(q, 1.5 / ac, th, er);
        o[ch] = cov * saWeight(min(length(q), 1.0), cc * 2.0 * uPitch) / (PI * 0.25 * ac * ac);
    }
    // Verdeckung: der Empfänger liegt deutlich davor und ist schärfer
    float cr = texelFetch(uHalf, ivec2(gl_FragCoord.xy), 0).a;
    float occ = vSpot.z > 0.0 ? smoothstep(0.0, 2.0, cr - (vSpot.z - max(2.0, 0.25 * vSpot.z))) : 1.0;
    occ = max(occ, smoothstep(0.35, 0.8, abs(cr) / max(abs(vSpot.z), 1.0)));
    frag = vec4(vRgb * o * occ, 0.0);
}
