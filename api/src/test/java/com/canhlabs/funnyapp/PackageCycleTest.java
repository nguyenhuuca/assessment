package com.canhlabs.funnyapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against package dependency cycles (what IntelliJ's DSM shows as red cells).
 * Builds the package graph from the {@code import} lines under src/main/java and fails
 * on any strongly connected component of two or more packages, listing the edges inside it.
 */
class PackageCycleTest {

    private static final String BASE = "com.canhlabs.funnyapp";
    private static final Path SRC = Path.of("src", "main", "java");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+?)(?:\\.\\*)?;", Pattern.MULTILINE);

    @Test
    void mainCodeHasNoPackageCycles() throws IOException {
        Map<String, String> sources = readSources();
        Set<String> packages = new TreeSet<>();
        sources.values().forEach(s -> packages.add(packageOf(s)));

        Map<String, Map<String, Set<String>>> edges = new TreeMap<>();
        sources.forEach((file, source) -> {
            String from = packageOf(source);
            Matcher m = IMPORT.matcher(source);
            while (m.find()) {
                String to = owningPackage(m.group(1), packages);
                if (to != null && !to.equals(from)) {
                    edges.computeIfAbsent(from, k -> new TreeMap<>())
                            .computeIfAbsent(to, k -> new TreeSet<>())
                            .add(file + " -> " + m.group(1));
                }
            }
        });

        List<String> report = new ArrayList<>();
        for (Set<String> scc : stronglyConnected(packages, edges)) {
            report.add("cycle " + scc);
            scc.forEach(a -> edges.getOrDefault(a, Map.of()).forEach((b, why) -> {
                if (scc.contains(b)) {
                    report.add("  " + a + " -> " + b + ": " + why);
                }
            }));
        }
        assertThat(report).as("package cycles under %s", BASE).isEmpty();
    }

    private static Map<String, String> readSources() throws IOException {
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                sources.put(p.getFileName().toString(), Files.readString(p));
            }
        }
        assertThat(sources).as("no sources found under %s", SRC.toAbsolutePath()).isNotEmpty();
        return sources;
    }

    private static String packageOf(String source) {
        Matcher m = PACKAGE.matcher(source);
        return m.find() ? m.group(1) : "";
    }

    /** The longest known package that prefixes the import (class, nested class or static member). */
    private static String owningPackage(String imported, Set<String> packages) {
        if (!imported.startsWith(BASE)) {
            return null;
        }
        String candidate = imported;
        while (!packages.contains(candidate)) {
            int dot = candidate.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }
            candidate = candidate.substring(0, dot);
        }
        return candidate;
    }

    /** Tarjan's algorithm; returns only components with two or more packages. */
    private static List<Set<String>> stronglyConnected(Set<String> nodes, Map<String, Map<String, Set<String>>> edges) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new TreeSet<>();
        List<Set<String>> result = new ArrayList<>();
        int[] counter = {0};
        for (String n : nodes) {
            if (!index.containsKey(n)) {
                visit(n, edges, index, low, stack, onStack, result, counter);
            }
        }
        return result;
    }

    private static void visit(String v, Map<String, Map<String, Set<String>>> edges, Map<String, Integer> index,
                              Map<String, Integer> low, Deque<String> stack, Set<String> onStack,
                              List<Set<String>> result, int[] counter) {
        index.put(v, counter[0]);
        low.put(v, counter[0]++);
        stack.push(v);
        onStack.add(v);
        for (String w : edges.getOrDefault(v, Map.of()).keySet()) {
            if (!index.containsKey(w)) {
                visit(w, edges, index, low, stack, onStack, result, counter);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }
        if (low.get(v).equals(index.get(v))) {
            Set<String> component = new TreeSet<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                component.add(w);
            } while (!w.equals(v));
            if (component.size() > 1) {
                result.add(component);
            }
        }
    }
}
