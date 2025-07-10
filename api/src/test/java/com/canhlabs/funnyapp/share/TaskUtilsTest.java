package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.share.totp.TaskUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;

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
        Callable<String> task2 = () -> { throw new RuntimeException("Task 2 Failed"); };
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
        Callable<String> task2 = () -> { throw new RuntimeException("Task 2 Failed"); };
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
        Callable<String> task1 = () -> { throw new RuntimeException("Task 1 Failed"); };
        Callable<String> task2 = () -> { throw new RuntimeException("Task 2 Failed"); };
        Callable<String> task3 = () -> { throw new RuntimeException("Task 3 Failed"); };

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
}