// feldtafel.glsl — die Feldtafel von com.dan.core.gpu (Satz 1): alle eingetragenen Speicherpuffer unter ihrer Nummer.
// Einbinden mit  #include "/com/dan/core/gpu/feldtafel.glsl"  (braucht GL_GOOGLE_include_directive).
// Gebrauch: feld[nummer].w[i] liest oder schreibt den i-ten float des Puffers mit dieser Nummer; die Nummer kommt aus
// den Push-Konstanten (im ganzen Aufruf gleich). feldU ist derselbe Speicher als uint.
#extension GL_EXT_nonuniform_qualifier : require
layout(set = 1, binding = 0, std430) buffer FeldtafelF { float w[]; } feld[];
layout(set = 1, binding = 0, std430) buffer FeldtafelU { uint w[]; } feldU[];
