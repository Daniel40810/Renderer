// surge_flood.glsl — com.dan.surge: die Flutkarte als Textur (FloodMap, SurgeGl.bindFlood). RG32F: r = Ankunft der Front
// (s Ereigniszeit; 1e6 = nie), g = höchster Stand (m; −1000 = trocken). Rechteck x0, z der Südkante, Breite, Tiefe;
// Zeilen laufen nach Norden (−z), wie die Gitter eines Lösers in UTM.
uniform int uSgFloodOn;
uniform sampler2D uSgFlood;
uniform vec4 uSgFloodRect;
uniform float uSgEvent;
vec2 sgFloodUV(vec2 xz) { return vec2((xz.x - uSgFloodRect.x) / uSgFloodRect.z, (uSgFloodRect.y - xz.y) / uSgFloodRect.w); }
