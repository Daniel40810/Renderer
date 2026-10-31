// composite.frag — den Strahl aus der verkleinerten Auflösung auf das volle Bild legen (additiv). Von den vier
// nächsten Texeln zählt das, dessen Weg am besten zur Tiefe dieses Bildpunkts passt, damit kein Strahl über den
// Rand des Turms läuft.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uBeam;
uniform sampler2D uDepth;
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform int uReversed;
uniform float uMaxDist;
uniform float uStrength;
uniform vec4 uCeil;             // Höhe der Wolkenuntergrenze, —, an (1)/aus, —
void main() {
    float dz = textureLod(uDepth, vUV, 0.0).r;
    bool sky = uReversed == 1 ? dz <= 0.0 : dz >= 1.0;
    float t = uMaxDist;
    if (!sky) {
        vec4 p = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? dz : dz * 2.0 - 1.0, 1.0);
        t = min(uMaxDist, length(p.xyz / p.w - uCam));
    }
    // wie beam.frag: der Blick endet an der Wolkenuntergrenze. Ohne das passte der Weg der Himmelstexel nie zur
    // Tiefe, und die Gewichtung wählte je Bildpunkt einen einzelnen Texel (treppige Kanten).
    if (uCeil.z > 0.5 && uCam.y < uCeil.x) {
        vec4 c = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? 0.5 : 0.0, 1.0);
        vec3 d = normalize(c.xyz / c.w - uCam);
        if (d.y > 1e-4) t = min(t, (uCeil.x - uCam.y) / d.y);
    }
    vec2 ts = vec2(textureSize(uBeam, 0));
    vec2 f = vUV * ts - 0.5;
    vec2 b = floor(f);
    vec2 fr = f - b;
    vec4 s00 = texelFetch(uBeam, clamp(ivec2(b), ivec2(0), ivec2(ts) - 1), 0);
    vec4 s10 = texelFetch(uBeam, clamp(ivec2(b) + ivec2(1, 0), ivec2(0), ivec2(ts) - 1), 0);
    vec4 s01 = texelFetch(uBeam, clamp(ivec2(b) + ivec2(0, 1), ivec2(0), ivec2(ts) - 1), 0);
    vec4 s11 = texelFetch(uBeam, clamp(ivec2(b) + ivec2(1, 1), ivec2(0), ivec2(ts) - 1), 0);
    vec4 w = vec4((1.0 - fr.x) * (1.0 - fr.y), fr.x * (1.0 - fr.y), (1.0 - fr.x) * fr.y, fr.x * fr.y);
    // Gewicht nach passender Tiefe: gleiche Wege zählen voll, abweichende kaum
    vec4 dd = abs(vec4(s00.a, s10.a, s01.a, s11.a) - t) / max(t, 1.0);
    w *= 1.0 / (1e-3 + dd);
    vec3 c = (s00.rgb * w.x + s10.rgb * w.y + s01.rgb * w.z + s11.rgb * w.w) / max(1e-6, w.x + w.y + w.z + w.w);
    frag = vec4(c * uStrength, 0.0);
}
