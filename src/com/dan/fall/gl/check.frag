// check.frag — com.dan.fall: Ort und Geschwindigkeit eines Pakets wie Fall.parcel.
#include "fall.glsl"
flat in vec4 vQ;
flat in float vF;
out vec4 frag;
void main() { frag = fallParcel(int(vQ.x + 0.5), vQ.y, vQ.z, vQ.w, vF); }
