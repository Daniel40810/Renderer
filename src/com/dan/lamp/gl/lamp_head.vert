// lamp_head.vert — die leuchtenden Köpfe als Lichtpunkte, Gauß mit erhaltener Beleuchtungsstärke (seit Timmendorf Phase 11).
// LampGl setzt davor: #version 430 core und lamp.glsl.
uniform mat4 uViewProj;
uniform vec3 uEye;
uniform float uPxPerRad;
uniform float uMinPx;
out vec2 vQ;
out vec3 vColor;
void main() {
    const vec2 C[6] = vec2[](vec2(-1,-1), vec2(1,-1), vec2(1,1), vec2(-1,-1), vec2(1,1), vec2(-1,1));
    vec2 c = C[gl_VertexID % 6];
    LampData L = lamps[gl_InstanceID];
    vec3 v = uEye - L.pos.xyz;
    float d = max(length(v), 0.1);
    vec3 toEye = v / d;
    vec3 col = L.col.rgb * lampShape(int(L.pos.w), toEye, L.dir.xy);     // cd je Kanal zum Auge
    float sigma = max(sqrt(max(L.dir.z, 1e-4) / 3.14159265) / d, uMinPx / uPxPerRad);
    vColor = col / (d * d) / (6.2831853 * sigma * sigma);
    vQ = c * 3.0;
    vec3 f = -toEye;
    vec3 r = normalize(cross(abs(f.y) > 0.99 ? vec3(1,0,0) : vec3(0,1,0), f));
    vec3 u = cross(f, r);
    gl_Position = uViewProj * vec4(L.pos.xyz + (r * c.x + u * c.y) * 3.0 * sigma * d, 1.0);
}
