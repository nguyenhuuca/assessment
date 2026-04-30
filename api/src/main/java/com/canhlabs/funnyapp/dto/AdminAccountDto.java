package com.canhlabs.funnyapp.dto;

import com.canhlabs.funnyapp.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccountDto {
    private Long id;
    private String email;
    private UserRole role;
    private boolean mfaEnabled;
    private Instant createdAt;
}
