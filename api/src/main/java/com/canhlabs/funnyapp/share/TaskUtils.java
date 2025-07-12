package com.canhlabs.funnyapp.share;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;

/**
 * Utility class to run multiple tasks in parallel using Virtual Threads and StructuredTaskScope.
 */
@Slf4j
public class TaskUtils {

    /**
     * Run all tasks in parallel and return their results if all succeed.
     * If any task fails, throws the first encountered exception and cancels the rest.
     * Equivalent to JavaScript's Promise.all().
     *
     * @param tasks List of Callable tasks
     * @return List of results from the tasks
     * @throws Exception if any task fails
     */
    public static <T> List<T> runAll(List<Callable<T>> tasks) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();

            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(task::call));
            }

            scope.join();            // Wait for all subtasks to complete
            scope.throwIfFailed();   // Throw if any subtask failed

            List<T> results = new ArrayList<>();
            for (var subtask : subtasks) {
                results.add(subtask.get());
            }

            return results;
        }
    }

    /**
     * Run all tasks in parallel, ignore failures, and return success/failure for each.
     * Equivalent to JavaScript's Promise.allSettled().
     *
     * @param tasks List of Callable tasks
     * @return List of Result objects representing success or failure
     */
    public static <T> List<Result<T>> runAllIgnoreError(List<Callable<T>> tasks) {
        try (var scope = new StructuredTaskScope()) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();

            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(task::call));
            }

            scope.join(); // Wait for all subtasks, don't throw on failure

            List<Result<T>> results = new ArrayList<>();
            for (var subtask : subtasks) {
                if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    results.add(Result.success(subtask.get()));
                } else {
                    results.add(Result.failure(subtask.exception()));
                }
            }

            return results;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Wrapper class representing the result of a task, which may succeed or fail.
     */
    public static class Result<T> {
        private final T value;
        private final Throwable error;

        private Result(T value, Throwable error) {
            this.value = value;
            this.error = error;
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        public static <T> Result<T> failure(Throwable error) {
            return new Result<>(null, error);
        }

        public boolean isSuccess() {
            return error == null;
        }

        public T getValue() {
            return value;
        }

        public Throwable getError() {
            return error;
        }
    }

    public static <T> T runSingle(Callable<T> task) throws Exception {
        ThreadFactory factory = Thread.ofVirtual().factory();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure("runSingle", factory)) {
            log.info("thread name: " + Thread.currentThread().getName() +
                    ", is virtual: " + Thread.currentThread().isVirtual());
            var subtask = scope.fork(task);
            scope.join();           // wait for task to complete
            scope.throwIfFailed();  // throw if it failed
            return subtask.get();
        }
    }

}