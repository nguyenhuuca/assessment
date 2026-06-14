package com.canhlabs.funnyapp.dto.auth;

public record EnableRequest(String username, String otp, String secret) {
}
