// stich_zeigen.frag — zeigt den Druck im Rechteck des Ziels: ganz (verkleinert über Mipmaps, wie aus der Entfernung) oder
// mit der Lupe (Bildpunkte des Drucks vergrößert, ohne Glätten). Außerhalb des Drucks der Grund.
uniform sampler2D uDruckTex;
uniform vec4 uRechteck;    // x, y, Breite, Höhe des Ziels (px)
uniform vec4 uAusschnitt;  // u0, v0, du, dv: welcher Teil des Drucks (Anteile, v nach oben) ins Rechteck kommt
uniform int uLupe;         // 1: Bildpunkte zeigen
uniform vec3 uGrund;
layout(location = 0) out vec4 oFarbe;

void main() {
    vec2 s = (gl_FragCoord.xy - uRechteck.xy) / uRechteck.zw;
    vec2 uv = uAusschnitt.xy + s * uAusschnitt.zw;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) { oFarbe = vec4(uGrund, 1.0); return; }
    if (uLupe != 0) {
        ivec2 gr = textureSize(uDruckTex, 0);
        oFarbe = texelFetch(uDruckTex, min(ivec2(uv * vec2(gr)), gr - 1), 0);
    } else oFarbe = texture(uDruckTex, uv);
}
