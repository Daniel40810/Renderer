// netz_zaehler.glsl – render.water, seit S3c-6 (N6, Seegang Werkbuch 22), seit S3c-7 eigene Datei (Werkbuch 24): Schub
// und Zähler je Bildpunkt für den Befund der Kosten. Eingebunden von netz_fuss.glsl (Netz, Vorlauf) und wasser.frag
// (alter Weg, mit ZAEHL_BINDUNG 15).
#ifndef ZAEHL_BINDUNG
#define ZAEHL_BINDUNG 20
#endif
// Schub – x 1: mit Vorlauf der Tiefe (netz.frag schattiert nur den Sieger), y 1: Zähler an, z: Breite des Bilds, w 1: die
// Maske des Netzes gilt (seit S3c-8, nur der alte Weg)
layout(push_constant) uniform NetzSchub { ivec4 nsch; };
// Zähler je Bildpunkt (8 uint): 0 Aufrufe im Vorlauf, 1 Aufrufe in netz.frag, 2 davon mit Fuß, 3 davon bis zur Optik,
// 4 in netz.frag mit B47 (seit S3c-7), 5 alter Weg: verworfen im Netz nach der Suche, 6 alter Weg: Aufrufe, 7 alter
// Weg: bis zur Optik
layout(set = 0, binding = ZAEHL_BINDUNG, std430) buffer NetzZaehler { uint zaehl[]; };
void zaehle(ivec2 px, uint k) { if (nsch.y == 1) atomicAdd(zaehl[8u * uint(px.y * nsch.z + px.x) + k], 1u); }
