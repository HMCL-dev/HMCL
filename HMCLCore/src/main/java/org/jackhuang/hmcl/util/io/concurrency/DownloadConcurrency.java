package org.jackhuang.hmcl.util.io.concurrency;

import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.CircularArrayList;
import org.jackhuang.hmcl.util.FXThread;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class DownloadConcurrency {
    private DownloadConcurrency() {
    }

    public static int DEFAULT_CONCURRENCY = Math.min(Runtime.getRuntime().availableProcessors() * 4, 64);

    private static volatile int downloadConcurrency = DEFAULT_CONCURRENCY;
    private static final List<WeakReference<Semaphore>> instances = new CopyOnWriteArrayList<>();

    public static ConcurrencyGuard of() {
        return of(false);
    }

    public static ConcurrencyGuard of(boolean fair) {
        Semaphore instance = new Semaphore(downloadConcurrency, fair);
        instances.add(new WeakReference<>(instance));
        return new ConcurrencyGuard(instance);
    }

    @FXThread
    public static void set(int concurrency) {
        concurrency = Math.max(concurrency, 1);
        int delta = concurrency - downloadConcurrency;
        downloadConcurrency = concurrency;
        if (delta == 0) {
            return;
        }

        FetchTask.setDownloadExecutorConcurrency(concurrency);
        if (delta > 0) {
            growConcurrency(delta);
        } else {
            if (Schedulers.isVirtualThreadAvailable()) {
                shrinkConcurrencyVT(-delta);
            } else {
                shrinkConcurrencySlow(-delta);
            }
        }
    }

    private static void growConcurrency(int delta) {
        for (int i = instances.size() - 1; i >= 0; i--) {
            Semaphore semaphore = instances.get(i).get();
            if (semaphore == null) {
                instances.remove(i);
            } else {
                semaphore.release(delta);
            }
        }
    }

    private static void shrinkConcurrencyVT(int delta) {
        for (int i = instances.size() - 1; i >= 0; i--) {
            Semaphore semaphore = instances.get(i).get();
            if (semaphore == null) {
                instances.remove(i);
            } else {
                Schedulers.io().execute(() -> {
                    if (semaphore.tryAcquire(delta)) {
                        return;
                    }

                    for (int j = 0; j < delta; j++) {
                        semaphore.acquireUninterruptibly();
                    }
                });
            }
        }
    }

    private static final class SemaphorePair {
        private final Semaphore semaphore;
        private int count;

        public SemaphorePair(Semaphore semaphore, int count) {
            this.semaphore = semaphore;
            this.count = count;
        }
    }

    private static void shrinkConcurrencySlow(int delta) {
        List<SemaphorePair> semaphores = new CircularArrayList<>();
        for (int i = instances.size() - 1; i >= 0; i--) {
            Semaphore semaphore = instances.get(i).get();
            if (semaphore == null) {
                instances.remove(i);
            } else {
                semaphores.add(new SemaphorePair(semaphore, delta));
            }
        }

        Schedulers.defaultScheduler().execute(() -> {
            while (!semaphores.isEmpty()) {
                SemaphorePair pair = semaphores.get(0);
                boolean acquired;
                try {
                    acquired = pair.semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    continue;
                }

                if (acquired) {
                    pair.count--;
                    if (pair.count == 0) {
                        semaphores.remove(0);
                    }
                } else {
                    semaphores.remove(0);
                    semaphores.add(pair);
                }
            }
        });
    }
}
