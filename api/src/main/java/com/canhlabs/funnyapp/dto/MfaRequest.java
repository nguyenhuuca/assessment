package com.canhlabs.funnyapp.dto;

public record MfaRequest(String username, String otp , String sessionToken) {
}