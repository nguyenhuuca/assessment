package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.totp.TaskUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskUtilsTest {

    @Test
    public void testRunAll_AllTasksSuccessful() throws Exception {
        // Prepare tasks
        Callable<String> task1 = () -> "Task 1 Result";
        Callable<String> task2 = () -> "Task 2 Result";
        Callable<String> task3 = () -> "Task 3 Result";

        List<String> results = TaskUtils.runAll(List.of(task1, task2, task3));

        // Verify results
        assertEquals(3, results.size());
        assertEquals("Task 1 Result", results.get(0));
        assertEquals("Task 2 Result", results.get(1));
        assertEquals("Task 3 Result", results.get(2));
    }

    @Test
    public void testRunAll_OneTaskFails() {
        // Prepare tasks
        Callable<String> task1 = () -> "Task 1 Result";
        Callable<String> task2 = () -> {
            throw new RuntimeException("Task 2 Failed");
        };
        Callable<String> task3 = () -> "Task 3 Result";

        // Verify that an exception is thrown when one task fails
        Exception exception = assertThrows(Exception.class, () -> {
            TaskUtils.runAll(List.of(task1, task2, task3));
        });

        assertTrue(exception.getMessage().contains("Task 2 Failed"));
    }

    @Test
    public void testRunAllIgnoreError_AllTasksSuccessful() throws Exception {
        // Prepare tasks
        Callable<String> task1 = () -> "Task 1 Result";
        Callable<String> task2 = () -> "Task 2 Result";
        Callable<String> task3 = () -> "Task 3 Result";

        List<TaskUtils.Result<String>> results = TaskUtils.runAllIgnoreError(List.of(task1, task2, task3));

        // Verify results
        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("Task 1 Result", results.get(0).getValue());
        assertTrue(results.get(1).isSuccess());
        assertEquals("Task 2 Result", results.get(1).getValue());
        assertTrue(results.get(2).isSuccess());
        assertEquals("Task 3 Result", results.get(2).getValue());
    }

    @Test
    public void testRunAllIgnoreError_OneTaskFails() throws Exception {
        // Prepare tasks
        Callable<String> task1 = () -> "Task 1 Result";
        Callable<String> task2 = () -> {
            throw new RuntimeException("Task 2 Failed");
        };
        Callable<String> task3 = () -> "Task 3 Result";

        List<TaskUtils.Result<String>> results = TaskUtils.runAllIgnoreError(List.of(task1, task2, task3));

        // Verify results
        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("Task 1 Result", results.get(0).getValue());
        assertFalse(results.get(1).isSuccess());
        assertNotNull(results.get(1).getError());
        assertTrue(results.get(1).getError() instanceof RuntimeException);
        assertEquals("Task 2 Failed", results.get(1).getError().getMessage());
        assertTrue(results.get(2).isSuccess());
        assertEquals("Task 3 Result", results.get(2).getValue());
    }

    @Test
    public void testRunAllIgnoreError_AllTasksFail() throws Exception {
        // Prepare tasks
        Callable<String> task1 = () -> {
            throw new RuntimeException("Task 1 Failed");
        };
        Callable<String> task2 = () -> {
            throw new RuntimeException("Task 2 Failed");
        };
        Callable<String> task3 = () -> {
            throw new RuntimeException("Task 3 Failed");
        };

        List<TaskUtils.Result<String>> results = TaskUtils.runAllIgnoreError(List.of(task1, task2, task3));

        // Verify results
        assertEquals(3, results.size());
        assertFalse(results.get(0).isSuccess());
        assertNotNull(results.get(0).getError());
        assertEquals("Task 1 Failed", results.get(0).getError().getMessage());

        assertFalse(results.get(1).isSuccess());
        assertNotNull(results.get(1).getError());
        assertEquals("Task 2 Failed", results.get(1).getError().getMessage());

        assertFalse(results.get(2).isSuccess());
        assertNotNull(results.get(2).getError());
        assertEquals("Task 3 Failed", results.get(2).getError().getMessage());
    }

    @Test
    void testRunAllAsyncIgnoreError_AllSuccess() throws Exception {
        List<Callable<String>> tasks = List.of(
                () -> "A",
                () -> "B",
                () -> "C"
        );

        CompletableFuture<List<TaskUtils.Result<String>>> future = TaskUtils.runAllAsyncIgnoreError(tasks);

        List<TaskUtils.Result<String>> results = future.get();

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(TaskUtils.Result::isSuccess));
        assertEquals("A", results.get(0).getValue());
        assertEquals("B", results.get(1).getValue());
        assertEquals("C", results.get(2).getValue());
    }

    @Test
    void testRunAllAsyncIgnoreError_WithFailure() throws Exception {
        List<Callable<String>> tasks = List.of(
                () -> "OK",
                () -> {
                    throw new RuntimeException("Boom");
                },
                () -> "Still OK"
        );

        CompletableFuture<List<TaskUtils.Result<String>>> future = TaskUtils.runAllAsyncIgnoreError(tasks);
        List<TaskUtils.Result<String>> results = future.get();

        assertEquals(3, results.size());

        // Task 0: success
        assertTrue(results.get(0).isSuccess());
        assertEquals("OK", results.get(0).getValue());

        // Task 1: failure
        assertFalse(results.get(1).isSuccess());
        assertNotNull(results.get(1).getError());
        assertEquals("Boom", results.get(1).getError().getMessage());

        // Task 2: success
        assertTrue(results.get(2).isSuccess());
        assertEquals("Still OK", results.get(2).getValue());
    }

//    @Test
//    void testRunAllAsyncIgnoreError_AllFail() throws Exception {
//        List<Callable<String>> tasks = List.of(
//                () -> { throw new RuntimeException("Fail A"); },
//                () -> { throw new RuntimeException("Fail B"); }
//        );
//
//        CompletableFuture<List<TaskUtils.Result<String>>> future = TaskUtils.runAllAsyncIgnoreError(tasks);
//        List<TaskUtils.Result<String>> results = future.get();
//
//        assertEquals(2, results.size());
//
//        for (TaskUtils.Result<String> result : results) {
//            assertFalse(result.isSuccess());
//            assertNotNull(result.getError());
//        }
//
//        assertEquals("Fail A", results.get(0).getError().getMessage());
//        assertEquals("Fail B", results.get(1).getError().getMessage());
//    }
//
//    @Test
//    void testRunAllAsync_AllSuccess() throws Exception {
//        List<Callable<String>> tasks = List.of(
//                () -> "A",
//                () -> "B",
//                () -> "C"
//        );
//
//        CompletableFuture<List<String>> future = TaskUtils.runAllAsync(tasks);
//
//        List<String> results = future.get();
//
//        assertEquals(3, results.size());
//        assertEquals("A", results.get(0));
//        assertEquals("B", results.get(1));
//        assertEquals("C", results.get(2));
//    }
//
//    @Test
//    void testRunAllAsync_OneFails() {
//        List<Callable<String>> tasks = List.of(
//                () -> "Good",
//                () -> { throw new RuntimeException("Failing task"); },
//                () -> "Should not reach here"
//        );
//
//        CompletableFuture<List<String>> future = TaskUtils.runAllAsync(tasks);
//
//        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
//        assertTrue(exception.getCause() instanceof RuntimeException);
//        assertEquals("Failing task", exception.getCause().getMessage());
//    }
//
//    @Test
//    void testRunAllAsync_AllFail() {
//        List<Callable<String>> tasks = List.of(
//                () -> { throw new IllegalStateException("Error A"); },
//                () -> { throw new IllegalArgumentException("Error B"); }
//        );
//
//        CompletableFuture<List<String>> future = TaskUtils.runAllAsync(tasks);
//
//        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
//        assertInstanceOf(RuntimeException.class, exception.getCause());
//        assertTrue(exception.getCause().getMessage().contains("Error")); // Could be either A or B
//    }
}