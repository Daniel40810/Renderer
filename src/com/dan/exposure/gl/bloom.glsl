// bloom.glsl — com.dan.exposure (aus LituyaBay1958, Werkbuch Welle W1): Glühen um Helles aus den Mip-Stufen 2–6 des
// HDR-Bilds (der Wirt baut die Stufen, etwa nach der Kantenglättung). Ergebnis zum belichteten Bild addieren, mal Stärke:
//   hdr = textureLod(uHdr, uv, 0.0).rgb * uExposure;
//   hdr += exposureBloom(uHdr, uv, uExposure, 0.9) * uBloom;
// Gewichte 0,30 / 0,25 / 0,20 / 0,15 / 0,10 der Stufen 2 bis 6; was über der Schwelle liegt, glüht [G].
vec3 exposureBloom(sampler2D hdr, vec2 uv, float exposure, float threshold) {
    vec3 bl = (textureLod(hdr, uv, 2.0).rgb * 0.30 + textureLod(hdr, uv, 3.0).rgb * 0.25 + textureLod(hdr, uv, 4.0).rgb * 0.20
             + textureLod(hdr, uv, 5.0).rgb * 0.15 + textureLod(hdr, uv, 6.0).rgb * 0.10) * exposure;
    return max(bl - vec3(threshold), vec3(0.0));
}
