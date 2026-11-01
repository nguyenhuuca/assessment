package com.canhlabs.funnyapp.dto.admin;

import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.List;

/** Enum fields make an unknown role/permission name a 400 at deserialization, before any row is touched. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccountBulkRequest {
    public static final int MAX_IDS = 100;

    public enum Action { ROLE, GRANT_PERMISSION, REVOKE_PERMISSION, DELETE }

    @NotEmpty
    @Size(max = MAX_IDS)
    private List<@NotNull Long> ids;
    @NotNull
    private Action action;
    private UserRole role;
    private Permission permission;

    /** Ids without duplicates, in request order. */
    public List<Long> distinctIds() {
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    /** The parameter the chosen action needs must be present, checked before any row is touched. */
    @JsonIgnore
    @AssertTrue(message = "action parameter is missing")
    public boolean isActionParamPresent() {
        if (action == null) {
            return true;
        }
        return switch (action) {
            case ROLE -> role != null;
            case GRANT_PERMISSION, REVOKE_PERMISSION -> permission != null;
            case DELETE -> true;
        };
    }
}
