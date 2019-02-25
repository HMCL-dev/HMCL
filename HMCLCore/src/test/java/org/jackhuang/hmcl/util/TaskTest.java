package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.task.Task;
import org.junit.Assert;
import org.junit.Test;

public class TaskTest {
    @Test
    public void testWhenComplete() {
        Task.composeAsync(() -> Task.allOf(
                Task.allOf(Task.runAsync(() -> {
                    throw new Exception();
                }))
        )).whenComplete((exception -> {
            Assert.assertFalse(exception == null);
        })).test();
    }
}
