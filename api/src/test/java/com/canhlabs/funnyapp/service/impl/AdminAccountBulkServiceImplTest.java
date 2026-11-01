package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.admin.AdminAccountBulkRequest;
import com.canhlabs.funnyapp.dto.admin.AdminAccountBulkRequest.Action;
import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.ResourceNotFoundException;
import com.canhlabs.funnyapp.service.AdminAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AdminAccountBulkServiceImplTest {
    private static final long ACTOR = 1L;

    @Mock AdminAccountService accounts;
    AdminAccountBulkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountBulkServiceImpl();
        service.injectAdminAccountService(accounts);
    }

    private static AdminAccountDto acct(long id, UserRole role, int permissions) {
        return AdminAccountDto.builder().id(id).role(role).permissions(permissions).build();
    }

    private static AdminAccountBulkRequest req(Action action, UserRole role, Permission perm, Long... ids) {
        return AdminAccountBulkRequest.builder().ids(List.of(ids)).action(action).role(role).permission(perm).build();
    }

    @Test
    void role_mixedOutcomes_bucketsEveryId() {
        when(accounts.getAccount(10L)).thenReturn(acct(10, UserRole.USER, 1));
        when(accounts.getAccount(11L)).thenReturn(acct(11, UserRole.CONTRIBUTOR, 3));
        when(accounts.getAccount(12L)).thenThrow(new ResourceNotFoundException("User not found: 12"));
        when(accounts.getAccount(13L)).thenReturn(acct(13, UserRole.ADMIN, 31));
        doThrow(CustomException.of(HttpStatus.FORBIDDEN, 0, "error.account.outranked"))
                .when(accounts).updateRole(13L, ACTOR, UserRole.CONTRIBUTOR);
        doThrow(CustomException.of(HttpStatus.BAD_REQUEST, 0, "error.account.self"))
                .when(accounts).updateRole(ACTOR, ACTOR, UserRole.CONTRIBUTOR);
        when(accounts.getAccount(ACTOR)).thenReturn(acct(ACTOR, UserRole.CONTRIBUTOR, 3));

        BulkResultDto r = service.bulk(req(Action.ROLE, UserRole.CONTRIBUTOR, null, 10L, 11L, 12L, 13L, ACTOR), ACTOR);

        assertThat(r.getRequested()).isEqualTo(5);
        assertThat(r.getUpdated()).isEqualTo(1);
        assertThat(r.getUnchanged()).isEqualTo(1);
        assertThat(r.getNotFound()).containsExactly("12");
        assertThat(r.getSkipped()).extracting("id", "code")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("13", "error.account.outranked"),
                        org.assertj.core.groups.Tuple.tuple("1", "error.account.self"));
        verify(accounts).updateRole(10L, ACTOR, UserRole.CONTRIBUTOR);
        verify(accounts, never()).updateRole(11L, ACTOR, UserRole.CONTRIBUTOR);
    }

    @Test
    void grant_setsBitOnCurrentMask_andAlreadySetIsUnchanged() {
        when(accounts.getAccount(10L)).thenReturn(acct(10, UserRole.CONTRIBUTOR, 0b00011));
        when(accounts.getAccount(11L)).thenReturn(acct(11, UserRole.CONTRIBUTOR, 0b00110));

        BulkResultDto r = service.bulk(req(Action.GRANT_PERMISSION, null, Permission.EXEC, 10L, 11L), ACTOR);

        assertThat(r.getUpdated()).isEqualTo(1);
        assertThat(r.getUnchanged()).isEqualTo(1);
        verify(accounts).updatePermissions(10L, ACTOR, 0b00111);
        verify(accounts, never()).updatePermissions(org.mockito.ArgumentMatchers.eq(11L), anyLong(), anyInt());
    }

    @Test
    void revoke_clearsBitOnCurrentMask_andAlreadyClearedIsUnchanged() {
        when(accounts.getAccount(10L)).thenReturn(acct(10, UserRole.ADMIN, 0b11111));
        when(accounts.getAccount(11L)).thenReturn(acct(11, UserRole.ADMIN, 0b00111));

        BulkResultDto r = service.bulk(req(Action.REVOKE_PERMISSION, null, Permission.DELETE, 10L, 11L), ACTOR);

        assertThat(r.getUpdated()).isEqualTo(1);
        assertThat(r.getUnchanged()).isEqualTo(1);
        verify(accounts).updatePermissions(10L, ACTOR, 0b10111);
    }

    @Test
    void grantAutopilotBit_byNonSuperAdmin_isSkippedWithStableCode() {
        when(accounts.getAccount(10L)).thenReturn(acct(10, UserRole.ADMIN, 0b11111));
        when(accounts.updatePermissions(10L, ACTOR, 0b11111 | 0b0100000))
                .thenThrow(CustomException.of(HttpStatus.FORBIDDEN, 0, "error.account.autopilotBits"));

        BulkResultDto r = service.bulk(req(Action.GRANT_PERMISSION, null, Permission.AUTOPILOT_AUTO_MERGE, 10L), ACTOR);

        assertThat(r.getUpdated()).isZero();
        assertThat(r.getSkipped()).hasSize(1);
        assertThat(r.getSkipped().get(0).getCode()).isEqualTo("error.account.autopilotBits");
    }

    @Test
    void delete_rowFailureDoesNotStopNextRow_andNoRawTextLeaks() {
        doThrow(new RuntimeException("jdbc secret")).when(accounts).deleteAccount(10L, ACTOR);
        doThrow(new ResourceNotFoundException("User not found: 11")).when(accounts).deleteAccount(11L, ACTOR);

        BulkResultDto r = service.bulk(req(Action.DELETE, null, null, 10L, 11L, 12L, 12L), ACTOR);

        assertThat(r.getRequested()).isEqualTo(3);
        assertThat(r.getUpdated()).isEqualTo(1);
        assertThat(r.getNotFound()).containsExactly("11");
        assertThat(r.getSkipped()).hasSize(1);
        assertThat(r.getSkipped().get(0).getCode()).isEqualTo("error.unexpected");
        assertThat(r.getSkipped().get(0).getMessage()).doesNotContain("jdbc secret");
        verify(accounts).deleteAccount(12L, ACTOR);
    }

    @Test
    void selfRow_withNoChange_stillReachesServiceSoSelfRuleDecides() {
        when(accounts.getAccount(ACTOR)).thenReturn(acct(ACTOR, UserRole.ADMIN, 0b11111));
        when(accounts.updatePermissions(any(), any(), anyInt()))
                .thenThrow(CustomException.of(HttpStatus.BAD_REQUEST, 0, "error.account.self"));

        BulkResultDto r = service.bulk(req(Action.GRANT_PERMISSION, null, Permission.READ, ACTOR), ACTOR);

        assertThat(r.getUnchanged()).isZero();
        assertThat(r.getSkipped().get(0).getCode()).isEqualTo("error.account.self");
    }
}
