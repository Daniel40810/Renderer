// bg.frag — Hintergrund der Demo von com.dan.wetglass: Himmel mit Wolken, See bis zur Kimm, Sonne und ein ferner
// rot-weiß geringelter Turm, damit man ihn in jedem Tropfen kopfüber sieht. Linear, HDR.
in vec2 vUv;
out vec4 frag;
uniform float uAspect, uFovy, uTime, uSunAlt, uSunAz, uDark;
float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.55); }
float vn(vec2 p) {
    vec2 i = floor(p), f = fract(p), u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), u.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), u.x), u.y);
}
void main() {
    vec2 ndc = vUv * 2.0 - 1.0;
    float t = tan(radians(uFovy) * 0.5);
    vec3 d = normalize(vec3(ndc.x * t * uAspect, ndc.y * t + 0.02, -1.0));
    vec3 sun = vec3(cos(radians(uSunAlt)) * sin(radians(uSunAz)), sin(radians(uSunAlt)), -cos(radians(uSunAlt)) * cos(radians(uSunAz)));
    vec3 sky = mix(vec3(0.70, 0.74, 0.78), vec3(0.28, 0.38, 0.52), clamp(d.y * 2.5, 0.0, 1.0)) * (1.0 - 0.55 * uDark);
    float cl = vn(d.xz / max(d.y, 0.02) * 1.5 + vec2(uTime * 0.02, 0.0)) * 0.6 + vn(d.xz / max(d.y, 0.02) * 4.0) * 0.4;
    sky = mix(sky, vec3(0.8) * (1.0 - 0.6 * uDark), smoothstep(0.45, 0.8, cl) * smoothstep(0.0, 0.1, d.y));
    float sd = dot(d, sun);
    sky += vec3(6.0, 5.4, 4.6) * smoothstep(0.9995, 0.9998, sd) * (1.0 - uDark) + vec3(0.6, 0.5, 0.4) * pow(max(sd, 0.0), 40.0) * (1.0 - uDark);
    vec3 col = sky;
    if (d.y < 0.0) {
        float dist = 12.0 / max(-d.y, 0.002);
        vec2 p = d.xz * dist;
        float w = vn(p * 0.15 + uTime * 0.3) * 0.5 + vn(p * 0.6 - uTime * 0.5) * 0.5;
        vec3 sea = vec3(0.03, 0.08, 0.09) * (1.0 - 0.4 * uDark) + vec3(0.6, 0.6, 0.55) * pow(max(dot(reflect(d, normalize(vec3(w - 0.5, 4.0, 0.3))), sun), 0.0), 200.0) * (1.0 - uDark);
        col = mix(sea, sky * 0.9, exp(-dist / 3000.0) * 0.0 + smoothstep(-0.03, 0.0, d.y) * 0.6);
    }
    // ferner Turm: geringelt rot und weiß, schwarzer Fuß, auf der Kimm bei Azimut +8°
    float az = atan(d.x, -d.z), el = asin(d.y);
    float tx = (az - radians(8.0)) / radians(1.1), ty = el / radians(6.0);
    if (abs(tx) < 1.0 - ty * 0.25 && ty > -0.02 && ty < 1.0) {
        float band = floor(ty * 6.0);
        vec3 tc = ty < 0.22 ? vec3(0.03) : mod(band, 2.0) < 1.0 ? vec3(0.75, 0.74, 0.72) : vec3(0.55, 0.05, 0.03);
        col = tc * (0.55 + 0.45 * (1.0 - abs(tx))) * (1.0 - 0.5 * uDark);
    }
    frag = vec4(col, 1.0);
}
