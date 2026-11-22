package com.nhom6.motofix.enums;

public enum Gender {
    MALE,
    FEMALE,
    OTHER;

    public static Gender fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toUpperCase();
        if (v.equals("NAM") || v.equals("MALE")) {
            return MALE;
        }
        if (v.equals("NỮ") || v.equals("NU") || v.equals("FEMALE")) {
            return FEMALE;
        }
        if (v.equals("KHÁC") || v.equals("KHAC") || v.equals("OTHER")) {
            return OTHER;
        }
        try {
            return Gender.valueOf(v);
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
