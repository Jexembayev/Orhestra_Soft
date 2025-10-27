// src/main/java/orhestra/coordinator/service/SpotRegistry.java
package orhestra.coordinator.service;

import orhestra.coordinator.store.model.SpotNode;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpotRegistry {

    private static final long DOWN_SEC = 5;

    private static final class Entry {
        String spotId;
        String lastIp;
        double cpuLoad;
        int runningTasks;
        int totalCores;
        long lastBeatEpoch; // seconds
    }

    // по nodeId
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    public void heartbeat(String nodeId, double cpu, int tasks, int totalCores, String ip) {
        Entry e = map.computeIfAbsent(nodeId, k -> {
            Entry x = new Entry();
            x.spotId = k;
            return x;
        });
        e.lastIp = ip;
        e.cpuLoad = cpu;
        e.runningTasks = tasks;
        e.totalCores = totalCores;
        e.lastBeatEpoch = System.currentTimeMillis() / 1000L;
    }

    // SpotRegistry.java
    public List<String> pruneAndGetRemovedIds(long ttlSec) {
        long now = System.currentTimeMillis()/1000L;
        var removed = new java.util.ArrayList<String>();
        for (var it = map.entrySet().iterator(); it.hasNext();) {
            var e = it.next().getValue();
            if (now - e.lastBeatEpoch > ttlSec) {
                removed.add(e.spotId);
                it.remove();
            }
        }
        return removed;
    }

    public int size() { return map.size(); }

    /** Снэпшот для UI в виде SpotNode */
    public List<SpotNode> snapshotNodes() {
        long now = System.currentTimeMillis() / 1000L;
        List<SpotNode> list = new ArrayList<>(map.size());
        for (Entry e : map.values()) {
            long age = Math.max(0, now - e.lastBeatEpoch);
            String status = (age > DOWN_SEC) ? "DOWN" : "UP";
            list.add(new SpotNode(
                    e.spotId,
                    e.cpuLoad,
                    e.runningTasks,
                    status,
                    Instant.ofEpochSecond(e.lastBeatEpoch),
                    e.totalCores,
                    e.lastIp
            ));
        }
        // опционально — сортировка: свежие сверху
        list.sort(Comparator.comparing(SpotNode::lastSeen, Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    /** Очистка (если нужна кнопка сброса) */
    public void clear() { map.clear(); }
}






