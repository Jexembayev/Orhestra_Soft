package orhestra.coordinator.service;

import orhestra.coordinator.model.SpotRow;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Память о спотах + снапшоты для UI. */
public class SpotRegistry {

    /** Внутреннее хранение фактов от агентa. */
    private static final class SpotInfo {
        String id;
        String addr;
        double cpu;
        int    tasks;
        int    totalCores;
        long   lastBeatEpoch;  // seconds
        String status = "READY";
    }

    private final ConcurrentHashMap<String, SpotInfo> map = new ConcurrentHashMap<>();

    /** Heartbeat от HTTP-агента. */
    public void heartbeat(String nodeId, double cpu, int tasks, int totalCores, String ip) {
        if (nodeId == null || nodeId.isBlank()) nodeId = "unknown";
        final String key = nodeId;

        SpotInfo s = map.computeIfAbsent(key, k -> {
            SpotInfo x = new SpotInfo();
            x.id = k;
            return x;
        });
        s.addr          = (ip == null ? "" : ip);
        s.cpu           = cpu;
        s.tasks         = tasks;
        s.totalCores    = Math.max(totalCores, 0);
        s.lastBeatEpoch = Instant.now().getEpochSecond();
        s.status        = "READY";
    }

    /** Снимок для UI (готовые строки таблицы). */
    public List<SpotRow> snapshot() {
        long now = Instant.now().getEpochSecond();

        List<SpotRow> out = new ArrayList<>(map.size());
        for (SpotInfo s : map.values()) {
            SpotRow r = new SpotRow();
            // id в UI числовой – возьмём хэш, но строковый id покажем в addr
            try { r.setId(Math.abs(s.id.hashCode())); } catch (Throwable ignored) {}
            r.setAddr(s.addr == null || s.addr.isBlank() ? s.id : (s.addr + " (" + s.id + ")"));
            r.setCpu(s.cpu);
            r.setTasks(s.tasks);
            r.setLastBeatEpoch(s.lastBeatEpoch);
            r.setStatus((now - s.lastBeatEpoch > 10) ? "OFFLINE" : s.status);
            try { r.setTotalCores(s.totalCores); } catch (Throwable ignored) {}
            out.add(r);
        }

        out.sort(Comparator.comparingInt(SpotRow::getId));
        return out;
    }

    /** Для отладки. */
    public int size() { return map.size(); }

    /** Удаление «старых» записей (опционально). */
    public void reapOlderThanSeconds(long seconds) {
        long lim = Instant.now().getEpochSecond() - seconds;
        map.values().removeIf(s -> s.lastBeatEpoch < lim);
    }
}







