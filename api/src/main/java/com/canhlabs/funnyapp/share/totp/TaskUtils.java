package com.canhlabs.funnyapp.share.totp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;

/**
 * Utility class to run multiple tasks in parallel using Virtual Threads and StructuredTaskScope.
 */
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
    /**
     * Run all tasks asynchronously and return a CompletableFuture that completes
     * with all results if successful, or exceptionally if any task fails.
     */
    public static <T> CompletableFuture<List<T>> runAllAsync(List<Callable<T>> tasks) {
        // Use virtual thread per task (Java 21+)
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<T>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e); // wrap checked exception
                    }
                }, executor))
                .toList();

        // Combine all futures
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(voidd -> futures.stream()
                        .map(CompletableFuture::join) // join is safe here because allOf already ensured completion
                        .toList()
                )
                .whenComplete((res, ex) -> executor.close()); // auto-shutdown virtual thread executor
    }

    public static <T> CompletableFuture<List<Result<T>>> runAllAsyncIgnoreError(List<Callable<T>> tasks) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Result<T>>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            CompletableFuture<Result<T>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    T result = task.call();
                    return Result.success(result);
                } catch (Throwable e) {
                    return Result.failure(e);
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return allDone
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList()
                )
                .whenComplete((res, ex) -> {
                    // ✅ Always shutdown executor
                    executor.close();
                });
    }
}
