// light.glsl — com.dan.surge: der Lichtvertrag. Die Shader dieses Packages rufen nur diese Namen; ein Wirt mit eigenem
// Licht ersetzt die ganze Datei (SurgeGl.hostLight), etwa durch seine Sonne am Punkt mit Bergschatten (com.dan.horizon),
// sein Rundumbild und seine Luftperspektive. Was ein Lichtblock bereitstellen muss:
//   uniform mat4 uVP; uniform vec3 uCam, uSun; uniform float uTime, uLevel, uFrame;
//   vec3 lightSun(vec3 w)          Sonnenlicht am Punkt w (vorbelichtet, mit Schatten)
//   vec3 lightSky(vec3 n)          Himmelslicht auf eine Fläche mit Normale n
//   vec3 lightSkyDir(vec3 d)       Himmel in Richtung d (Spiegelungen, Durchlicht)
//   vec3 lightHaze(vec3 d)         Farbe des Dunsts in Richtung d (flach gespiegelte Hänge, Kimm)
//   vec3 lightAerial(vec3 c, vec3 d, float dist)   Luftperspektive zum Auge
//   vec3 lightIrradiance()         Bestrahlung von oben durch den Himmel (ohne Sonne)
// Diese Vorgabe: Sonne, Himmel und Dunst als feste Farben (SurgeGl.Light).
uniform mat4 uVP;
uniform vec3 uCam, uSun;
uniform float uTime, uLevel, uFrame;
uniform vec3 uLightSun, uLightZenith, uLightHorizon, uLightHaze;
uniform float uLightFog;

vec3 lightSun(vec3 w) { return uLightSun * smoothstep(-0.015, 0.01, uSun.y); }
vec3 lightSkyDir(vec3 d) { return mix(uLightZenith, uLightHorizon, pow(1.0 - clamp(d.y, 0.0, 1.0), 4.0)); }
vec3 lightSky(vec3 n) { return mix(uLightHorizon, uLightZenith, 0.6) * (0.55 + 0.45 * n.y); }
vec3 lightHaze(vec3 d) { return uLightHaze; }
vec3 lightAerial(vec3 c, vec3 d, float dist) { return mix(c, uLightHaze, 1.0 - exp(-dist / uLightFog)); }
vec3 lightIrradiance() { return uLightZenith * 3.14159; }
