// netz_daten.glsl – render.water, seit S3c: die Clipmap des Seegangs (render.seegang.SeegangKarte), je Stufe L und
// Stelle (i, j) am Index (L n + j) n + i: Versatz (ξ_Ost, ξ_Nord, η, |Z'|), Normale (n_Ost, n_Nord, n_oben, J),
// aufgelöst (Σ k² E g², Σ E g²). Dazu die Scheitel des Netzes (netz.comp): je Stelle zwei vec4,
// (x, y, z relativ zum Auge, η_Bild) und (x₀, z₀ der ruhenden Stelle, η der Welle, Art: 1 nass, 0,5 nah am Wasser, 0 weit;
// seit S3c-5).
layout(set = 0, binding = 15, std430) readonly buffer SeeVersatz { vec4 snVersatz[]; };
layout(set = 0, binding = 16, std430) readonly buffer SeeNormale { vec4 snNormale[]; };
layout(set = 0, binding = 17, std430) readonly buffer SeeAufg { vec2 snAufg[]; };
