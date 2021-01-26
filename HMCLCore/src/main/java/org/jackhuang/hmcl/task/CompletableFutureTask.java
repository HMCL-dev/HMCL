package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.function.ExceptionalBiConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.function.ExceptionalRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class CompletableFutureTask<T> extends Task<T> {

    @Override
    public void execute() throws Exception {
    }

    public abstract CompletableFuture<T> getFuture(TaskCompletableFuture executor);

    protected static Runnable wrap(ExceptionalRunnable<?> runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                rethrow(e);
            }
        };
    }

    protected static <T, R> Function<T, R> wrap(ExceptionalFunction<T, R, ?> fn) {
        return t -> {
            try {
                return fn.apply(t);
            } catch (Exception e) {
                rethrow(e);
                throw new InternalError("Unreachable code");
            }
        };
    }

    protected static <T> Consumer<T> wrap(ExceptionalConsumer<T, ?> fn) {
        return t -> {
            try {
                fn.accept(t);
            } catch (Exception e) {
                rethrow(e);
            }
        };
    }

    protected static <T, E> BiConsumer<T, E> wrap(ExceptionalBiConsumer<T, E, ?> fn) {
        return (t, e) -> {
            try {
                fn.accept(t, e);
            } catch (Exception ex) {
                rethrow(ex);
            }
        };
    }

    protected static void rethrow(Throwable e) {
        if (e == null)
            return;
        if (e instanceof ExecutionException || e instanceof CompletionException) { // including UncheckedException and UncheckedThrowable
            rethrow(e.getCause());
        } else if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            throw new CompletionException(e);
        }
    }

    protected static Throwable resolveException(Throwable e) {
        if (e instanceof ExecutionException || e instanceof CompletionException)
            return resolveException(e.getCause());
        else
            return e;
    }

    public static class CustomException extends RuntimeException {}

    protected static CompletableFuture<Void> breakable(CompletableFuture<?> future) {
        return future.thenApplyAsync(unused1 -> (Void) null).exceptionally(throwable -> {
            if (resolveException(throwable) instanceof CustomException) return null;
            else throw new CompletionException(throwable);
        });
    }

}
