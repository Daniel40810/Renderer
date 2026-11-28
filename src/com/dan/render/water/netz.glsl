// netz.glsl – render.water, seit S3c (Seegang Werkbuch): der Kopf der Clipmap des Seegangs, die render.seegang als
// Puffer „Seegang/Netz“ ablegt (Seegangbild). snN: Stufen, n, 1 = gesetzt (0: kein Netz, alles wie bisher), Breite des
// Übergangs ins Fernfeld; snRand: der innere Bereich relativ zum Auge (x von, x bis, z von, z bis; x Ost, z Süd), in
// dem das Netz zeichnet und der Weg ohne Netz schweigt; snLage[L]: x, z der Stelle (0, 0) relativ zum Auge und d_L. Die
// Stelle (i, j) der Stufe L liegt bei (x + i d_L, z − j d_L).
layout(set = 0, binding = 14, std430) readonly buffer SeeNetz { ivec4 snN; vec4 snRand; vec4 snLage[8]; };

bool imNetz(vec2 q) { return snN.z == 1 && q.x > snRand.x && q.x < snRand.y && q.y > snRand.z && q.y < snRand.w; }
