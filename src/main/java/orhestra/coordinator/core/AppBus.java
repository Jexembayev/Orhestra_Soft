package orhestra.coordinator.core;

import java.util.concurrent.CopyOnWriteArrayList;

public final class AppBus {
    private AppBus() {}

    private static final CopyOnWriteArrayList<Runnable> TASKS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> SPOTS = new CopyOnWriteArrayList<>();

    public static void onTasksChanged(Runnable r) { TASKS.add(r); }
    public static void onSpotsChanged(Runnable r) { SPOTS.add(r); }

    public static void fireTasksChanged() { for (var r : TASKS) try { r.run(); } catch (Throwable ignore) {} }
    public static void fireSpotsChanged() { for (var r : SPOTS) try { r.run(); } catch (Throwable ignore) {} }
}
