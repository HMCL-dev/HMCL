// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.Lang;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public abstract class CompletableFutureTask<T> extends Task<T> {

    @Override
    public void execute() throws Exception {
    }

    public abstract CompletableFuture<T> getFuture(TaskCompletableFuture executor);

    public static class CustomException extends RuntimeException {}

    protected static CompletableFuture<Void> breakable(CompletableFuture<?> future) {
        return future.thenApplyAsync(unused1 -> (Void) null).exceptionally(throwable -> {
            if (Lang.resolveException(throwable) instanceof CustomException) return null;
            else throw new CompletionException(throwable);
        });
    }

}
