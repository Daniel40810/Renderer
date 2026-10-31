// wind.glsl — Baustein für fremde Shader (wie weathering.glsl): das Windfeld aus com.dan.wind als 3D-Textur.
// Einbinden mit #include "/com/dan/wind/gl/wind.glsl"; WindGl.bind() setzt die Uniforms.
//   uWindTex  RGBA16F, x = Zelle Ost, y = Zelle Süd, z = Schicht; rgb = Wind (m/s, x Ost, y oben, z Süd), a = Böe (m/s)
//   uWindBox  (x0, z0, Breite, Tiefe) des Gitters in der Szene (m)
//   uWindLay  (erste Schicht m, Streckung, Schichten, Höhe der Grenzschicht m)
//   uWindOn   0 = kein Feld (windSample liefert 0)
// Die Schicht zu h m über Grund ist die Umkehrung der Streckung: k = log(1 + h·(r − 1)/d0) / log(r), wie
// WindField.Grid.layerOf(). Den Grund unter dem Punkt kennt der aufrufende Shader (sein Gelände).
uniform sampler3D uWindTex;
uniform vec4 uWindBox, uWindLay;
uniform int uWindOn;

vec4 windSample(vec2 xz, float hAboveGround) {
    if (uWindOn == 0) return vec4(0.0);
    vec2 uv = (xz - uWindBox.xy) / uWindBox.zw;
    float k = log(1.0 + max(hAboveGround, 0.0) * (uWindLay.y - 1.0) / uWindLay.x) / log(uWindLay.y);
    float w = clamp(k, 0.5, uWindLay.z - 0.5) / uWindLay.z;
    return texture(uWindTex, vec3(clamp(uv, 0.0, 1.0), w));
}
