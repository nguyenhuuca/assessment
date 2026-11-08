package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety net for splitting the /admin controllers: the set of routes served under the exact
 * {@code /admin} base path, with their HTTP method and security/audit/rate-limit annotations,
 * must stay identical no matter which controller class declares them.
 */
class AdminRouteContractTest {

    private static final String ADMIN_BASE = AppConstant.API.BASE_URL + "/admin";
    private static final String HLS_PRE = "hasRole('SUPER_ADMIN') and @permissionServiceImpl"
            + ".hasPermission(authentication.details.permissions, ADMIN)";
    private static final String ADMIN_ROLE = "hasRole('ADMIN')";

    private static final List<String> EXPECTED = List.of(
            // videos (10, incl. /stats)
            row("GET", "/videos", ADMIN_ROLE, "-", "-", "-"),
            row("PATCH", "/videos/{id}/status", ADMIN_ROLE, "-", "-", "-"),
            row("PATCH", "/videos/{id}/priority", ADMIN_ROLE, "-", "updateVideoPriority", "-"),
            row("PATCH", "/videos/{id}/details", ADMIN_ROLE, "-", "updateVideoDetails", "-"),
            row("PATCH", "/videos/{id}/visibility", ADMIN_ROLE, "-", "updateVideoVisibility", "-"),
            row("DELETE", "/videos/{id}", ADMIN_ROLE, "-", "deleteVideo", "-"),
            row("PUT", "/videos/{id}/hls", HLS_PRE, "-", "enableVideoHls", "-"),
            row("DELETE", "/videos/{id}/hls", HLS_PRE, "-", "disableVideoHls", "-"),
            row("POST", "/videos/bulk", ADMIN_ROLE, "ADMIN", "bulkVideos", "permit=10,window=60"),
            row("GET", "/stats", ADMIN_ROLE, "ADMIN", "-", "-"),
            // accounts (5)
            row("GET", "/accounts", ADMIN_ROLE, "-", "-", "-"),
            row("PATCH", "/accounts/{id}/role", ADMIN_ROLE, "-", "-", "-"),
            row("PATCH", "/accounts/{id}/permissions", ADMIN_ROLE, "-", "-", "-"),
            row("POST", "/accounts/bulk", ADMIN_ROLE, "ADMIN", "bulkAccounts", "permit=10,window=60"),
            row("DELETE", "/accounts/{id}", ADMIN_ROLE, "-", "-", "-"),
            // comments (4)
            row("GET", "/comments", ADMIN_ROLE, "-", "-", "-"),
            row("PATCH", "/comments/moderation", ADMIN_ROLE, "ADMIN", "bulkModerateComments", "-"),
            row("POST", "/comments/hard-delete", ADMIN_ROLE, "ADMIN", "hardDeleteComments", "-"),
            row("PATCH", "/comments/{id}/moderation", ADMIN_ROLE, "ADMIN", "moderateComment", "-"));

    private static String row(String http, String path, String pre, String perm, String audit, String rate) {
        return http + " " + ADMIN_BASE + path + " | pre=" + pre + " | perm=" + perm
                + " | audit=" + audit + " | rate=" + rate;
    }

    @Test
    void adminRoutes_matchTheCheckedInContract() {
        List<String> actual = new ArrayList<>(collectRows());
        List<String> expected = new ArrayList<>(EXPECTED);
        actual.sort(null);
        expected.sort(null);

        assertEquals(19, EXPECTED.size());
        assertEquals(expected, actual);
    }

    @Test
    void adminRoutes_haveNoDuplicateMethodAndPath() {
        Set<String> seen = new HashSet<>();
        for (String r : collectRows()) {
            String key = r.substring(0, r.indexOf(" | "));
            assertTrue(seen.add(key), "duplicate route: " + key);
        }
    }

    private static List<String> collectRows() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> rows = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.canhlabs.funnyapp.web")) {
            Class<?> controller = load(bd.getBeanClassName());
            RequestMapping base = controller.getAnnotation(RequestMapping.class);
            if (base == null || base.value().length != 1 || !ADMIN_BASE.equals(base.value()[0])) {
                continue;
            }
            PreAuthorize classPre = controller.getAnnotation(PreAuthorize.class);
            for (Method m : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String path = ADMIN_BASE + (mapping.path().length == 0 ? "" : mapping.path()[0]);
                rows.add(mapping.method()[0].name() + " " + path
                        + " | pre=" + preAuthorize(m, classPre)
                        + " | perm=" + perm(m)
                        + " | audit=" + audit(m)
                        + " | rate=" + rate(m));
            }
        }
        return rows;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Direct (non-merged) lookup: a method with only {@code @HasPermission} falls back to the class rule. */
    private static String preAuthorize(Method m, PreAuthorize classPre) {
        PreAuthorize own = m.getAnnotation(PreAuthorize.class);
        PreAuthorize effective = own != null ? own : classPre;
        return effective == null ? "-" : effective.value();
    }

    private static String perm(Method m) {
        HasPermission p = m.getAnnotation(HasPermission.class);
        return p == null ? "-" : p.perm().name();
    }

    private static String audit(Method m) {
        AuditLog a = m.getAnnotation(AuditLog.class);
        return a == null ? "-" : a.value();
    }

    private static String rate(Method m) {
        RateLimited r = m.getAnnotation(RateLimited.class);
        return r == null ? "-" : "permit=" + r.permit() + ",window=" + r.windowInSeconds();
    }
}
