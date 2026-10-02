package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.repo.NotificationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single-JVM registry of open SSE streams (userId -> emitters). Only "unread" hints are pushed;
 * clients fetch the data over REST. Correct while exactly one instance runs (ADR-0017).
 */
@Slf4j
@Component
public class InMemorySsePublisher implements NotificationPublisher, NotificationStream {

    static final long TIMEOUT_MS = 30L * 60 * 1000;
    static final int MAX_PER_USER = 5;
    static final long HEARTBEAT_MS = 25_000;
    static final String GAUGE_NAME = "notifications_sse_active_connections";

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final NotificationRepository repository;

    public InMemorySsePublisher(NotificationRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        Gauge.builder(GAUGE_NAME, this, InMemorySsePublisher::activeConnections)
                .description("Open notification SSE streams")
                .register(meterRegistry);
    }

    @Override
    public SseEmitter register(long userId) {
        SseEmitter emitter = newEmitter();
        List<SseEmitter> evicted = new ArrayList<>();
        emitters.compute(userId, (k, list) -> {
            CopyOnWriteArrayList<SseEmitter> current = list == null ? new CopyOnWriteArrayList<>() : list;
            current.add(emitter);
            while (current.size() > MAX_PER_USER) {
                evicted.add(current.remove(0));
            }
            return current;
        });
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(t -> cleanup.run());

        evicted.forEach(SseEmitter::complete);

        try {
            send(emitter, unreadEvent(repository.countByUserIdAndReadAtIsNull(userId)));
        } catch (Exception e) {
            log.debug("Initial unread send failed for user {}", userId, e);
            remove(userId, emitter);
            emitter.complete();
        }
        return emitter;
    }

    @Override
    public void unreadChanged(long userId) {
        List<SseEmitter> targets = emitters.get(userId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        try {
            long unread = repository.countByUserIdAndReadAtIsNull(userId); // once for all of the user's tabs
            for (SseEmitter emitter : targets) {
                sendOrDrop(userId, emitter, unreadEvent(unread)); // builders are single-use
            }
        } catch (Exception e) {
            log.warn("Failed to push unread change for user {}", userId, e);
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                sendOrDrop(userId, emitter, SseEmitter.event().comment("ping")); // builders are single-use
            }
        });
    }

    public int activeConnections() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    private void sendOrDrop(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            send(emitter, event);
        } catch (IOException | RuntimeException e) {
            // Client went away or the emitter is already completed
            remove(userId, emitter);
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // already completed
            }
        }
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) throws IOException {
        emitter.send(event);
    }

    private static SseEmitter.SseEventBuilder unreadEvent(long unread) {
        return SseEmitter.event().name("unread").data("{\"unread\":" + unread + "}");
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
