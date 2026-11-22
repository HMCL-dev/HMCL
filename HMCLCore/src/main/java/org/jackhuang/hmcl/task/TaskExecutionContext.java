// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/// Executes children within the current task's cancellation and listener scope.
/// Children whose [Task#getExecutor()] returns null inherit the executor running the current
/// task's body. Each child can in turn supply this executor to its own children.
/// A task must use its context only on the thread executing its body and before the body returns.
/// While waiting, a worker may execute submissions from the awaited child's subtree on its
/// own executor. Use the same executor instance for work requiring the same worker.
/// Tasks using constrained executors must avoid wait cycles between independent
/// branches and must not hold resources that their children need to complete.
@NotNullByDefault
public interface TaskExecutionContext {
    /// Runs a child to completion and returns its published result.
    ///
    /// @throws Exception if the child fails or execution is cancelled or interrupted
    <T extends @Nullable Object> T run(Task<T> task) throws Exception;

    /// Schedules the children on their respective executors and waits for all of them.
    /// Siblings on executors with available workers may run concurrently. A failure in
    /// one child does not cancel the others. An empty collection schedules no work.
    ///
    /// @return a mutable list of successful results in the input's iteration order
    /// @throws Exception if a child fails or execution is cancelled or interrupted
    <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks) throws Exception;

    /// Executes an action on the specified executor and returns its result without creating
    /// a task or sending lifecycle notifications. May run inline on the current executor.
    /// An already running task may call this method after cancellation to perform cleanup.
    ///
    /// @throws Exception if the action fails or the wait is interrupted
    <T extends @Nullable Object> T call(Executor executor, Callable<T> action) throws Exception;
}
