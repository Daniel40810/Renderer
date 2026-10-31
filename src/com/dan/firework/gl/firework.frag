#version 430 core
// firework.frag — Stern: Gauß, additiv; Rauch: weiche Scheibe (1 − q)², vormultipliziert (seit Timmendorf Phase 11).
in vec2 vQ;
in vec4 vColor;
uniform int uMode;
uniform float uGain;        // Vorbelichtung × (1 oder Spiegelung)
out vec4 frag;
uniform int uMask;             // seit Timmendorf Phase 12: nur auf dem Wasser (Spiegelung), aus der Tiefe der Szene
uniform sampler2D uDepth;
uniform mat4 uInvViewProj;
uniform vec2 uViewport;
uniform int uReversed;
uniform float uWaterLevel, uWaterTol;
bool offWater() {
    if (uMask == 0) return false;
    vec2 uv = gl_FragCoord.xy / uViewport;
    float dz = textureLod(uDepth, uv, 0.0).r;
    if (uReversed == 1 ? dz <= 0.0 : dz >= 1.0) return true;          // Himmel
    vec4 p = uInvViewProj * vec4(uv * 2.0 - 1.0, uReversed == 1 ? dz : dz * 2.0 - 1.0, 1.0);
    return abs(p.y / p.w - uWaterLevel) > uWaterTol;
}
void main() {
    if (offWater()) discard;
    float q = dot(vQ, vQ);
    if (uMode == 0) {
        if (q > 9.0) discard;
        frag = vec4(vColor.rgb * exp(-0.5 * q) * uGain, 0.0);
    } else {
        if (q >= 1.0) discard;
        float k = (1.0 - q) * (1.0 - q);
        frag = vec4(vColor.rgb * k * uGain, vColor.a * k);
    }
}
