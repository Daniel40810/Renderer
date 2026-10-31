package com.dan.tree;

/**
 * Wind von außen, etwa aus {@code com.dan.wind}: {@code tree.wind.external = feld::windAt} (über eine
 * Lambda, die die Zeit festhält). Liefert {Ost, oben, Süd} in m/s am Ort (x Ost, y oben, z Süd).
 */
@FunctionalInterface
public interface Wind {
    double[] at(double x, double y, double z);
}
