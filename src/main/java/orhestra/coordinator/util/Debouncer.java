package orhestra.coordinator.util;

import javafx.application.Platform;

import java.util.concurrent.*;

public final class Debouncer implements AutoCloseable {
    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ui-debouncer"); t.setDaemon(true); return t;
    });
    private final long delayMs;
    private ScheduledFuture<?> future;

    public Debouncer(long delayMs) { this.delayMs = delayMs; }

    public synchronized void submit(Runnable fxTask) {
        if (future != null) future.cancel(false);
        future = ses.schedule(() -> Platform.runLater(fxTask), delayMs, TimeUnit.MILLISECONDS);
    }

    @Override public void close() { ses.shutdownNow(); }
}
