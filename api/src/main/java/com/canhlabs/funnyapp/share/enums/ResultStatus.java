package com.canhlabs.funnyapp.share.enums;

public enum ResultStatus {
    SUCCESS(0), FAILED(1);

    private final int statusValue;

    ResultStatus(int statusValue) {
        this.statusValue = statusValue;
    }

    public int getStatusValue() {
        return this.statusValue;
    }

}