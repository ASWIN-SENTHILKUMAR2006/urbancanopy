package com.urbancanopy.species.entity;

/** Preferred soil, used to filter the catalogue to a site's ground conditions. */
public enum SoilType {

    LOAMY("Loamy"),
    SANDY("Sandy"),
    CLAY("Clay"),
    ALLUVIAL("Alluvial"),
    RED_LATERITE("Red / laterite"),
    BLACK_COTTON("Black cotton"),
    ROCKY("Rocky / gravelly"),
    WELL_DRAINED_ANY("Any well-drained soil");

    private final String label;

    SoilType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
