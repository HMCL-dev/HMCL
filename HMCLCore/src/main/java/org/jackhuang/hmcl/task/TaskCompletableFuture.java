// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface TaskCompletableFuture {

    <T> CompletableFuture<T> one(Task<T> task);

    CompletableFuture<?> all(Collection<Task<?>> tasks);
}
