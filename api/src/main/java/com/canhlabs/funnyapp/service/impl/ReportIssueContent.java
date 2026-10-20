package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.AppReport;
import com.canhlabs.funnyapp.enums.ReportAutoLabel;
import com.canhlabs.funnyapp.enums.ReportType;
import com.canhlabs.funnyapp.enums.UserRole;

import java.util.ArrayList;
import java.util.List;

/** Builds the public GitHub issue body and labels for a report (BR-2, BR-4, BR-7). */
final class ReportIssueContent {

    static final String AUTOPILOT = "autopilot";
    private static final String UNKNOWN = "unknown";

    private ReportIssueContent() {
    }

    static String typeLabel(ReportType type) {
        return switch (type) {
            case BUG -> "bug";
            case ENHANCEMENT -> "enhancement";
            case ISSUE -> "report";
        };
    }

    /** The auto label only rides along with autopilot (BR-4). */
    static List<String> labels(ReportType type, ReportAutoLabel autoLabel, boolean autopilot) {
        List<String> labels = new ArrayList<>();
        if (autopilot) {
            labels.add(AUTOPILOT);
            if (autoLabel == ReportAutoLabel.AUTO) {
                labels.add("autopilot:auto");
            } else if (autoLabel == ReportAutoLabel.AUTO_MERGE) {
                labels.add("autopilot:auto-merge");
            }
        }
        labels.add(typeLabel(type));
        return labels;
    }

    /** Never includes the reporter's email, user name or id. */
    static String body(AppReport report) {
        String role = report.getReporterRole() == UserRole.SUPER_ADMIN ? "the SUPER_ADMIN" : "an ADMIN";
        return report.getDescription() + "\n\n"
                + "### Context\n"
                + "- Page: " + orUnknown(report.getPage()) + "\n"
                + "- Path: " + orUnknown(report.getPagePath()) + "\n"
                + "- App: " + orUnknown(report.getAppVersion()) + "\n"
                + "- Browser: " + orUnknown(report.getUserAgent()) + "\n"
                + "- Screen: " + (report.getScreenWidth() == null ? UNKNOWN : report.getScreenWidth() + "px") + "\n\n"
                + "Reported in-app by " + role + " · report " + report.getId().toString().substring(0, 8) + "\n\n"
                + "<!-- app-report:" + report.getId() + " -->";
    }

    /** Drops the query string and fragment, starting at the first '?' or '#'. */
    static String stripPath(String path) {
        if (path == null) {
            return null;
        }
        int cut = path.length();
        for (char c : new char[]{'?', '#'}) {
            int i = path.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        return path.substring(0, cut);
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
