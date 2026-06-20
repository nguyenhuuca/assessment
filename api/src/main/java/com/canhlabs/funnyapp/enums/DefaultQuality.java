package com.canhlabs.funnyapp.enums;

/**
 * Allowed values for the defaultQuality preference field.
 * Wire strings: "AUTO", "1080P", "4K" — mapped to enum constants AUTO, Q1080P, Q4K.
 */
public enum DefaultQuality {
    AUTO("AUTO"),
    Q1080P("1080P"),
    Q4K("4K");

    private final String wireValue;

    DefaultQuality(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    /**
     * Parse the wire string ("AUTO", "1080P", "4K") to the corresponding enum constant.
     *
     * @param value the wire string
     * @return the matching enum constant, or null if value is null
     * @throws IllegalArgumentException if value is non-null but not a valid quality string
     */
    public static DefaultQuality fromWireValue(String value) {
        if (value == null) {
            return null;
        }
        for (DefaultQuality q : values()) {
            if (q.wireValue.equals(value)) {
                return q;
            }
        }
        throw new IllegalArgumentException("Invalid defaultQuality value: " + value);
    }
}
