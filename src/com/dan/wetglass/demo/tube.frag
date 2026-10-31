// tube.frag — derselbe Baustein auf einem Zylinder (F10): ein lackiertes Rohr dicht vor dem Auge, liegend (Handlauf) oder
// stehend (Pfosten), vor dem Hintergrund. Der Strahl trifft den Zylinder analytisch; der Ort wird in die Flächenkoordinaten
// der Simulation abgewickelt (x um den Zylinder, y entlang der Achse). Auf undurchsichtigem Lack bricht ein Tropfen nicht
// die Welt, er spiegelt sie und glänzt; nasser Lack wird dunkler und glatter, Salz liegt weiß darauf.
#include "wetglass.glsl"
in vec2 vUv;
out vec4 frag;
uniform sampler2D uBg;
uniform vec2 uViewport;
uniform float uFovy;          // Grad
uniform float uRadius;        // m
uniform float uDist;          // Abstand der Achse vom Auge in m
uniform float uLift;          // Achse so viel über (liegend) bzw. neben (stehend) der Blickachse
uniform int uHorizontal;
uniform float uAxial;         // Verschiebung entlang der Achse (m)
uniform vec3 uSunDir;
uniform vec3 uPaint;

vec3 env(vec3 d) {
    vec3 sky = textureLod(uBg, vec2(0.5, 0.92), 7.0).rgb, sea = textureLod(uBg, vec2(0.5, 0.08), 7.0).rgb;
    if (d.z < -0.15) {
        float t = tan(radians(uFovy) * 0.5);
        vec2 uv = vec2(d.x / -d.z / t / (uViewport.x / uViewport.y), d.y / -d.z / t) * 0.5 + 0.5;
        if (all(greaterThan(uv, vec2(0.0))) && all(lessThan(uv, vec2(1.0)))) return textureLod(uBg, uv, 3.0).rgb;
    }
    return mix(sea, sky, smoothstep(-0.2, 0.3, d.y));
}

void main() {
    float t = tan(radians(uFovy) * 0.5);
    vec2 ndc = vUv * 2.0 - 1.0;
    vec3 rd = normalize(vec3(ndc.x * t * uViewport.x / uViewport.y, ndc.y * t, -1.0));
    vec3 bg = textureLod(uBg, vUv, 1.5).rgb;
    // Zylinder: liegend Achse entlang x durch (0, uLift, −uDist), stehend entlang y durch (uLift, 0, −uDist)
    vec2 o = vec2(-uLift, uDist);                         // Auge relativ zur Achse (quer, Tiefe)
    vec2 d = uHorizontal == 1 ? vec2(rd.y, rd.z) : vec2(rd.x, rd.z);
    float a = dot(d, d), b = 2.0 * dot(o, d), c = dot(o, o) - uRadius * uRadius;
    float disc = b * b - 4.0 * a * c;
    if (disc < 0.0) { frag = vec4(bg, 1.0); return; }
    float s = (-b - sqrt(disc)) / (2.0 * a);
    vec3 P = rd * s;
    vec2 q = o + d * s;                                   // Ort im Querschnitt relativ zur Achse (quer, Tiefe zum Auge)
    float th;
    vec3 T, B, N;
    vec2 p;
    if (uHorizontal == 1) {
        // θ von oben, wachsend zur Seite, wohin der Wind weht (vom Auge weg)
        th = atan(-q.y, q.x);
        T = vec3(0.0, -sin(th), -cos(th));
        B = vec3(1.0, 0.0, 0.0);
        N = vec3(0.0, cos(th), -sin(th));
        p = vec2(mod(th, 6.2831853) * uRadius, P.x + uAxial);
    } else {
        // θ von der Windseite (zum Auge), wachsend nach rechts
        th = atan(q.x, q.y);
        T = vec3(cos(th), 0.0, -sin(th));
        B = vec3(0.0, 1.0, 0.0);
        N = vec3(sin(th), 0.0, cos(th));
        p = vec2(mod(th, 6.2831853) * uRadius, P.y + uAxial);
    }
    vec3 V = -rd, L = normalize(uSunDir);
    vec4 w = wgSample(p);
    float cov = wgCover(w), rim = wgRim(w);
    vec3 n = wgNormal(w, T, B, N);
    vec3 skyA = textureLod(uBg, vec2(0.5, 0.92), 7.0).rgb;
    // Lack: matt, nass dunkler
    float film = w.y;
    vec3 paint = uPaint * (1.0 - 0.3 * film);
    vec3 dry = paint * (max(dot(N, L), 0.0) * 1.6 + 0.35 + 0.25 * max(N.y, 0.0)) * (0.6 + 0.4 * skyA);
    float fr0 = 0.04 + 0.08 * film;
    vec3 col = dry + env(reflect(rd, N)) * (fr0 + (1.0 - fr0) * pow(1.0 - max(dot(N, V), 0.0), 5.0)) * (0.3 + 0.7 * film);
    // Tropfen: Wasser über dem Lack, spiegelt Himmel und See, Sonnenglanz, dunkler Rand
    float fw = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
    vec3 under = paint * (max(dot(n, L), 0.0) * 1.4 + 0.4) * (0.6 + 0.4 * skyA);
    vec3 drop = mix(under, env(reflect(rd, n)), clamp(fw * 1.6, 0.0, 1.0));
    drop += vec3(1.0, 0.95, 0.85) * pow(max(dot(reflect(-L, n), V), 0.0), 240.0) * 6.0;
    drop *= 1.0 - 0.45 * smoothstep(0.6, 1.0, rim) * wgRimDetail(p);
    col = mix(col, drop, cov);
    // Salz: weiße, matte Kruste
    col = mix(col, vec3(0.78, 0.79, 0.77) * (max(dot(N, L), 0.0) * 0.9 + 0.45) * (0.6 + 0.4 * skyA), wgSalt(p) * (1.0 - cov));
    frag = vec4(col, 1.0);
}
