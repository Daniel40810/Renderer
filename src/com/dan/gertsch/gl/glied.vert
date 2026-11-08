// glied.vert — Stamm oder Ast als Band quer zum Blick (Ansicht) oder quer zur Sonne (Schattenkarte), je Glied eine
// Instanz mit vier Ecken. In der Ansicht mindestens 0,2 Bildpunkte breit (die Mehrfachabtastung macht dünnere blass).
layout(std430, binding = 4) readonly buffer Glieder { vec4 gd[]; };   // je Glied: p0 r0, p1 r1, Albedo

uniform int uModus;
uniform mat4 uVP;
uniform vec3 uVorn;
uniform float uFokus;

out vec3 vAchsePunkt;
out float vU;
flat out vec3 vAchse, vQuer, vAlbedo;
out float vR;

void main() {
    int gi = gl_InstanceID;
    vec4 a = gd[3 * gi], b = gd[3 * gi + 1], f = gd[3 * gi + 2];
    vec3 ax = b.xyz - a.xyz;
    float len = length(ax);
    ax = len > 1e-6 ? ax / len : vec3(0.0, 1.0, 0.0);
    vec3 mitte = 0.5 * (a.xyz + b.xyz);
    vec3 blick = uModus == 0 ? normalize(uAuge - mitte) : uZurSonne;
    vec3 quer = cross(ax, blick);
    float ql = length(quer);
    quer = ql < 1e-5 ? normalize(cross(ax, vec3(0.3, 0.1, 0.9))) : quer / ql;
    bool oben = (gl_VertexID & 2) != 0;
    float u = (gl_VertexID & 1) == 0 ? -1.0 : 1.0;
    vec3 p = oben ? b.xyz : a.xyz;
    float r = oben ? b.w : a.w;
    if (uModus == 0) r = max(r, 0.2 * max(0.3, dot(p - uAuge, uVorn)) / uFokus);
    vAchsePunkt = p;
    vU = u;
    vR = r;
    vAchse = ax;
    vQuer = quer;
    vAlbedo = f.rgb;
    gl_Position = uVP * vec4(p + quer * r * u, 1.0);
}
