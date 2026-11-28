// kugel.glsl – render.schwimmer (S7b-3, wie render.bau): reelle Kugelflächenfunktionen bis Grad 2 (Achsen des Raums: x Ost, y oben, z Süd)
// und die Bestrahlung aus ihren Gliedern (Ramamoorthi und Hanrahan 2001). Gleich in Java: Hausbild.kugelY.
void kugelY(vec3 d, out float y[9]) {
    y[0] = 0.282095;
    y[1] = 0.488603 * d.y;
    y[2] = 0.488603 * d.z;
    y[3] = 0.488603 * d.x;
    y[4] = 1.092548 * d.x * d.y;
    y[5] = 1.092548 * d.y * d.z;
    y[6] = 0.315392 * (3.0 * d.z * d.z - 1.0);
    y[7] = 1.092548 * d.x * d.z;
    y[8] = 0.546274 * (d.x * d.x - d.y * d.y);
}
