package org.jackhuang.hmcl.task;

import java.util.concurrent.CompletableFuture;

public abstract class CompletableFutureTask<T> extends Task<T> {

    public abstract CompletableFuture<T> getCompletableFuture();

    @Override
    public void execute() throws Exception {
        throw new AssertionError("Cannot reach here");
    }
}
