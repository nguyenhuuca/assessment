package com.canhlabs.funnyapp.dto.auth;

public record MfaRequest(String username, String otp , String sessionToken) {
}