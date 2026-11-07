// sunmap_host.glsl — com.dan.horizon: Vorgabe des Wirts für sunmap.comp. Ein Wirt ersetzt diese Datei mit
// HorizonGl.sunMapHost (GLSL-Text) und setzt seine Uniforms in HorizonGl.sunMapUniforms; etwa Wolkenschatten.
// w: Punkt der Szene (Krone), sunDir: Richtung zur Sonne. Ergebnis 0..1.
float hzHostShadow(vec3 w, vec3 sunDir) { return 1.0; }
