// pane.frag — die nasse Scheibe der Demo vor dem Hintergrund, mit dem Baustein wetglass.glsl: Tropfen zeigen die Welt
// kopfüber, ihr Rand wirft das Licht zurück, die Sonne glänzt auf ihnen, der Film der Spuren macht das Bild weicher;
// außen Salz, innen Beschlag, nach einer Überspülung eine ablaufende Wasserhaut.
#include "wetglass.glsl"
in vec2 vUv;
out vec4 frag;
uniform sampler2D uBg;
uniform vec2 uViewport;
uniform vec2 uCenter;        // Mitte des Ausschnitts auf der Scheibe in m
uniform float uHalf;         // halbe Höhe des Ausschnitts in m
uniform float uFocusLod;     // Unschärfe des Hintergrunds (Auge auf die Scheibe scharfgestellt)
uniform float uLens;
uniform vec3 uSunDir;
void main() {
    vec2 ndc = vUv * 2.0 - 1.0;
    vec2 p = uCenter + vec2(ndc.x * uViewport.x / uViewport.y, ndc.y) * uHalf;
    vec4 s = wgSample(p);
    vec2 ax, ay;
    wgScreenAxes(p, uViewport, ax, ay);
    vec3 behind = textureLod(uBg, vUv, uFocusLod).rgb;
    float cov = wgCover(s), rim = wgRim(s);
    vec2 suv = clamp(vUv + wgOffset(s, ax, ay, uLens), 0.0, 1.0);
    vec3 img = textureLod(uBg, suv, 0.0).rgb;
    // Rand: Totalreflexion, dunkel mit etwas Himmel
    vec3 skyAvg = textureLod(uBg, vec2(0.5, 0.9), 6.0).rgb;
    vec3 drop = mix(img * 0.95, skyAvg * 0.18, smoothstep(0.55, 0.95, rim) * (0.25 + 0.75 * wgRimDetail(p)));
    // Glanz der Sonne (und eines hellen Himmels) auf der Wasseroberfläche
    vec3 N = wgNormal(s, vec3(1, 0, 0), vec3(0, 1, 0), vec3(0, 0, 1));
    vec3 V = vec3(0.0, 0.0, 1.0);
    float spec = pow(max(dot(reflect(-uSunDir, N), V), 0.0), 120.0);
    drop += vec3(1.0, 0.95, 0.85) * spec * 3.0 + skyAvg * 0.35 * pow(1.0 - N.z, 2.0);
    vec3 col = mix(behind, drop, cov);
    // nasser Film: weicher und etwas heller
    col = mix(col, textureLod(uBg, vUv, uFocusLod + 2.0).rgb * 1.05, s.y * 0.35 * (1.0 - cov));
    // Feintropfen, zu klein für einen Bildpunkt: die Scheibe wird milchig
    col = mix(col, textureLod(uBg, vUv, uFocusLod + 3.0).rgb * 1.1 + 0.02, wgHaze(p) * 0.45);
    // Salz außen: eine weiße, matte Kruste, vom Himmel beleuchtet
    col = mix(col, skyAvg * 0.85 + vec3(0.07), wgSalt(p));
    // Beschlag innen: davor, das Bild verschwimmt und wird hell
    col = mix(col, textureLod(uBg, vUv, uFocusLod + 5.0).rgb * 0.8 + skyAvg * 0.28 + 0.03, wgFog(p) * 0.88);
    frag = vec4(col, 1.0);
}
