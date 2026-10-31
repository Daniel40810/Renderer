package com.dan.longship;

/** Was ein Schiff ohne Lage von außen vom Wasser wissen muss: die Höhe der Oberfläche an (x, z). */
@FunctionalInterface
public interface Water {
    double height(double x, double z);

    /** Ruhiges Wasser auf der Höhe 0. */
    Water CALM = (x, z) -> 0;
}
