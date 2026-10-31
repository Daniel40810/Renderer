// terrain_fog.frag — der Blick durch den Nebel über Gelände bis zur Szene (Tiefe als Kopie) oder zur Kimm. Abgetastet
// wird nur das Stück zwischen der tiefsten und der höchsten Stelle, an der Nebel sein kann (uTfBounds): erst grob bis
// zum ersten Nebel und dessen Rand halbiert, dann vom Rand an mit Schritten, die von 1,5 m an wachsen. Erdkrümmung wie das Gelände: die Szene liegt in der Ferne
// tiefer, der Nebel wird an der wahren Höhe gefragt. Licht: Sonne und Mond mit Vorwärtsstreuung (Henyey-Greenstein,
// g = 0,75) und vielfach gestreutem Anteil, gedämpft durch den Nebel über dem Punkt; dazu der Himmel. Ausgabe
// (Streulicht, Durchlässigkeit) für die Mischung ONE, SRC_ALPHA. Seit Brocken Phase 6.
// Seit Brocken Phase 7:
// - Phasenfunktion von außen (uPhase, u = Streuwinkel / 180°, auf 4π normiert, etwa die Tröpfchen nach Mie aus
//   com.dan.sky): dann zeigt der Nebel Glorie, Nebelbogen und Aureole.
// - Schattenwerfer (tfSunShadow): das direkte, einfach gestreute Sonnenlicht fehlt in ihrer Schattensäule – mit der
//   Glorie um den Kopf das Brockengespenst.
// - Höhe, ab der die Sonne scheint (uSunH, Kanal G, über uSunHRect): der Schatten des Geländes im Nebel.
// - Halbe (oder drittel) Auflösung (uScale > 1): je Texel ein fester Bildpunkt aus dem Block ((uScale − 1) / 2 vom
//   Eck), seine Entfernung geht als zweite Ausgabe an terrain_fog_up.frag.
#include "terrain_fog_common.glsl"
#include "terrain_fog_march.glsl"
uniform int uScale;                // 1 = volle Auflösung, 2 = halbe, 3 = drittel
layout(location = 0) out vec4 frag;
layout(location = 1) out float fragDist;

void main() {
    // Bildpunkt: voll oder der feste aus dem Block
    ivec2 lo = ivec2(gl_FragCoord.xy);
    ivec2 px = uScale > 1 ? min(lo * uScale + ivec2((uScale - 1) / 2), ivec2(uViewport) - 1) : lo;
    float d;
    frag = fogMarch(px, d);
    fragDist = d;
}
