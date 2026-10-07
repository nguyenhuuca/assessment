package com.canhlabs.funnyapp.filter;

import com.canhlabs.funnyapp.cache.CredentialsVersionCache;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialsVersionCacheTest {

    @Mock
    private UserRepo userRepo;

    private CredentialsVersionCache cache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cache = new CredentialsVersionCache(userRepo);
    }

    @Test
    void currentVersion_loadsOnceThenServesFromCache() {
        when(userRepo.findAllById(1L)).thenReturn(User.builder().id(1L).credentialsVersion(3).build());

        assertThat(cache.currentVersion(1L)).isEqualTo(3);
        assertThat(cache.currentVersion(1L)).isEqualTo(3);

        verify(userRepo, times(1)).findAllById(1L);
    }

    @Test
    void evict_forcesReloadSoNewVersionIsSeenImmediately() {
        when(userRepo.findAllById(1L))
                .thenReturn(User.builder().id(1L).credentialsVersion(0).build())
                .thenReturn(User.builder().id(1L).credentialsVersion(1).build());

        assertThat(cache.currentVersion(1L)).isZero();
        cache.evict(1L);
        assertThat(cache.currentVersion(1L)).isEqualTo(1);
    }

    @Test
    void currentVersion_unknownUserOrNullId_isZero() {
        when(userRepo.findAllById(9L)).thenReturn(null);

        assertThat(cache.currentVersion(9L)).isZero();
        assertThat(cache.currentVersion(null)).isZero();
        cache.evict(null); // no-op, must not throw
    }
}
