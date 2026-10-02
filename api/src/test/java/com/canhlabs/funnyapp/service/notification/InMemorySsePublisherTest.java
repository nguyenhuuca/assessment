package com.canhlabs.funnyapp.service.notification;

import com.canhlabs.funnyapp.repo.NotificationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemorySsePublisherTest {

    @Mock NotificationRepository repository;

    SimpleMeterRegistry meters;
    Deque<SseEmitter> queue;
    InMemorySsePublisher publisher;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        queue = new ArrayDeque<>();
        publisher = new InMemorySsePublisher(repository, meters) {
            @Override
            SseEmitter newEmitter() {
                return queue.isEmpty() ? mock(SseEmitter.class) : queue.poll();
            }
        };
    }

    private SseEmitter next() {
        SseEmitter e = mock(SseEmitter.class);
        queue.add(e);
        return e;
    }

    /** Text of the SSE wire frame produced by the builder passed to emitter.send(...). */
    private static String frame(SseEmitter.SseEventBuilder builder) {
        StringBuilder sb = new StringBuilder();
        for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
            sb.append(part.getData());
        }
        return sb.toString();
    }

    private static List<String> frames(SseEmitter emitter, int times) throws IOException {
        ArgumentCaptor<SseEmitter.SseEventBuilder> cap = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(times)).send(cap.capture());
        return cap.getAllValues().stream().map(InMemorySsePublisherTest::frame).toList();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_sendsCurrentUnreadOnConnect() throws Exception {
        SseEmitter e = next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(3L);

        SseEmitter result = publisher.register(7L);

        assertThat(result).isSameAs(e);
        assertThat(frames(e, 1)).containsExactly("event:unread\ndata:{\"unread\":3}\n\n");
        assertThat(publisher.activeConnections()).isEqualTo(1);
    }

    @Test
    void register_realEmitter_usesThirtyMinuteTimeout() {
        InMemorySsePublisher real = new InMemorySsePublisher(repository, new SimpleMeterRegistry());
        assertThat(real.newEmitter().getTimeout()).isEqualTo(30L * 60 * 1000);
    }

    @Test
    void register_capsAtFivePerUser_completingOldest() {
        List<SseEmitter> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(next());
        }
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L);

        for (int i = 0; i < 6; i++) {
            publisher.register(7L);
        }

        verify(all.get(0)).complete();
        for (int i = 1; i < 6; i++) {
            verify(all.get(i), never()).complete();
        }
        assertThat(publisher.activeConnections()).isEqualTo(5);
    }

    @Test
    void register_capIsPerUser() {
        for (int i = 0; i < 6; i++) {
            next();
        }
        when(repository.countByUserIdAndReadAtIsNull(any(Long.class))).thenReturn(0L);

        for (int i = 0; i < 3; i++) {
            publisher.register(1L);
            publisher.register(2L);
        }

        assertThat(publisher.activeConnections()).isEqualTo(6);
    }

    @Test
    void register_initialSendFails_removesAndCompletes() throws Exception {
        SseEmitter e = next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(1L);
        doThrow(new IOException("broken pipe")).when(e).send(any(SseEmitter.SseEventBuilder.class));

        publisher.register(7L);

        verify(e).complete();
        assertThat(publisher.activeConnections()).isZero();
    }

    @Test
    void register_completionCallbackRemovesEmitter() {
        SseEmitter e = next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L);
        publisher.register(7L);
        ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
        verify(e).onCompletion(cap.capture());

        cap.getValue().run();

        assertThat(publisher.activeConnections()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void register_timeoutAndErrorCallbacksRemoveEmitter() {
        SseEmitter timedOut = next();
        SseEmitter errored = next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L);
        publisher.register(7L);
        publisher.register(7L);
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(timedOut).onTimeout(timeout.capture());
        ArgumentCaptor<Consumer<Throwable>> error = ArgumentCaptor.forClass(Consumer.class);
        verify(errored).onError(error.capture());

        timeout.getValue().run();
        assertThat(publisher.activeConnections()).isEqualTo(1);
        verify(timedOut).complete();

        error.getValue().accept(new IOException("reset"));
        assertThat(publisher.activeConnections()).isZero();
    }

    // ── unreadChanged ─────────────────────────────────────────────────────────

    @Test
    void unreadChanged_sendsToEveryEmitterOfUser_countComputedOnce() throws Exception {
        SseEmitter tab1 = next();
        SseEmitter tab2 = next();
        SseEmitter other = next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L, 0L, 4L); // 2 connects, then the change
        when(repository.countByUserIdAndReadAtIsNull(8L)).thenReturn(0L);
        publisher.register(7L);
        publisher.register(7L);
        publisher.register(8L);

        publisher.unreadChanged(7L);

        assertThat(frames(tab1, 2).get(1)).isEqualTo("event:unread\ndata:{\"unread\":4}\n\n");
        assertThat(frames(tab2, 2).get(1)).isEqualTo("event:unread\ndata:{\"unread\":4}\n\n");
        frames(other, 1); // only its own initial event
        verify(repository, times(4)).countByUserIdAndReadAtIsNull(any(Long.class));
        verify(repository, times(3)).countByUserIdAndReadAtIsNull(7L); // 2 connects + exactly 1 for the change
    }

    @Test
    void unreadChanged_countQueriedOncePerChangeRegardlessOfTabs() {
        next();
        next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L, 0L, 2L);
        publisher.register(7L);
        publisher.register(7L);

        publisher.unreadChanged(7L);

        verify(repository, times(3)).countByUserIdAndReadAtIsNull(7L);
    }

    @Test
    void unreadChanged_noOpenStream_doesNotQuery() {
        publisher.unreadChanged(99L);

        verifyNoInteractions(repository);
    }

    @Test
    void unreadChanged_dropsFailedEmitterAndKeepsOthers() throws Exception {
        SseEmitter dead = next();
        SseEmitter alive = next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L, 0L, 1L);
        publisher.register(7L);
        publisher.register(7L);
        doThrow(new IOException("gone")).when(dead).send(any(SseEmitter.SseEventBuilder.class));

        publisher.unreadChanged(7L);

        assertThat(publisher.activeConnections()).isEqualTo(1);
        verify(dead).complete();
        assertThat(frames(alive, 2).get(1)).isEqualTo("event:unread\ndata:{\"unread\":1}\n\n");
    }

    @Test
    void unreadChanged_repositoryFailure_isSwallowed() throws Exception {
        SseEmitter e = next();
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L).thenThrow(new IllegalStateException("db"));
        publisher.register(7L);

        publisher.unreadChanged(7L);

        frames(e, 1);
        assertThat(publisher.activeConnections()).isEqualTo(1);
    }

    // ── heartbeat ─────────────────────────────────────────────────────────────

    @Test
    void heartbeat_sendsCommentPingToAll() throws Exception {
        SseEmitter a = next();
        SseEmitter b = next();
        when(repository.countByUserIdAndReadAtIsNull(any(Long.class))).thenReturn(0L);
        publisher.register(1L);
        publisher.register(2L);

        publisher.heartbeat();

        assertThat(frames(a, 2).get(1)).isEqualTo(":ping\n\n");
        assertThat(frames(b, 2).get(1)).isEqualTo(":ping\n\n");
    }

    @Test
    void heartbeat_dropsFailedEmitter() throws Exception {
        SseEmitter dead = next();
        when(repository.countByUserIdAndReadAtIsNull(1L)).thenReturn(0L);
        publisher.register(1L);
        doThrow(new IllegalStateException("completed")).when(dead).send(any(SseEmitter.SseEventBuilder.class));

        publisher.heartbeat();

        assertThat(publisher.activeConnections()).isZero();
    }

    @Test
    void heartbeat_everyTwentyFiveSeconds() throws Exception {
        var scheduled = InMemorySsePublisher.class.getMethod("heartbeat")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(scheduled.fixedRate()).isEqualTo(25_000L);
    }

    // ── gauge ─────────────────────────────────────────────────────────────────

    @Test
    void gauge_reportsActiveConnections() {
        next();
        next();
        when(repository.countByUserIdAndReadAtIsNull(any(Long.class))).thenReturn(0L);

        assertThat(meters.get("notifications_sse_active_connections").gauge().value()).isZero();
        publisher.register(1L);
        publisher.register(2L);
        assertThat(meters.get("notifications_sse_active_connections").gauge().value()).isEqualTo(2.0);
    }
}
