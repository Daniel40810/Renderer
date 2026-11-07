// treefall_light.glsl — com.dan.forest: Lichtvertrag der fallenden Bäume (TreeFallGl). Ein Wirt mit eigenem Licht ersetzt
// die Datei (TreeFallGl.hostLight) und setzt seine Uniforms (TreeFallGl.hostUniforms). Bereitzustellen:
//   uniform mat4 uVP; uniform vec3 uCam, uSun;
//   vec3 lightSun(vec3 w), vec3 lightSky(vec3 n), vec3 lightAerial(vec3 c, vec3 d, float dist)
// Vorgabe: aus ForestGl.Light (Sonne, Himmel, Dunst; vorbelichtet).
uniform mat4 uVP;
uniform vec3 uCam, uSun;
uniform vec3 uLightSun, uLightSky, uLightFog;
uniform float uLightFogDist;
vec3 lightSun(vec3 w) { return uLightSun; }
vec3 lightSky(vec3 n) { return uLightSky * (0.55 + 0.45 * n.y); }
vec3 lightAerial(vec3 c, vec3 d, float dist) { return mix(c, uLightFog, 1.0 - exp(-dist / uLightFogDist)); }
