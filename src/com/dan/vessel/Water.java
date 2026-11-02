package com.dan.vessel;

/**
 * Das Wasser, in dem ein Boot schwimmt und eine Kette hängt (eigene kleine Schnittstelle; die App reicht ihr Meer, ihre
 * Welle oder eine gerechnete Bucht herein). Welt: x Ost, y oben (m), z Süd.
 */
public interface Water {
    /** Höhe der Wasseroberfläche am Ort (m); liegt dort Land trocken, höchstens die Höhe des Grunds. */
    double height(double x, double z);

    /** Strömung über die Tiefe gemittelt {u nach Ost, w nach Süd} (m/s). */
    double[] velocity(double x, double z);

    /** Höhe des Grunds am Ort (m); ohne Grund sehr tief. */
    default double bed(double x, double z) { return -1e4; }

    /** Wind über dem Wasser {nach Ost, nach Süd} (m/s). */
    default double[] wind(double x, double z) { return new double[2]; }

    /** Ruhiges, tiefes Wasser auf Höhe level. */
    static Water still(double level) {
        return new Water() {
            public double height(double x, double z) { return level; }
            public double[] velocity(double x, double z) { return new double[2]; }
        };
    }

    /** Ruhiges Wasser auf Höhe level über einem flachen Grund in der Tiefe depth. */
    static Water still(double level, double depth) {
        return new Water() {
            public double height(double x, double z) { return level; }
            public double[] velocity(double x, double z) { return new double[2]; }
            public double bed(double x, double z) { return level - depth; }
        };
    }
}
