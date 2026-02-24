package orhestra.coordinator.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages recently used INI file paths.
 * Backed by java.util.prefs.Preferences — persists across app restarts.
 *
 * Storage:
 * node: orhestra/ui
 * keys: lastIniPath — last successfully loaded INI path
 * recentIniPaths — pipe-delimited MRU list (max 5)
 */
public final class RecentIniManager {

    private static final int MAX_RECENT = 5;
    private static final String KEY_LAST = "lastIniPath";
    private static final String KEY_RECENT = "recentIniPaths";
    private static final String KEY_OAUTH = "oauthToken";
    private static final String SEPARATOR = "|";

    private final Preferences prefs;

    public RecentIniManager() {
        this.prefs = Preferences.userNodeForPackage(RecentIniManager.class);
    }

    /** Get the last successfully loaded INI path (or null). */
    public String getLast() {
        return prefs.get(KEY_LAST, null);
    }

    /** Set the last loaded path. */
    public void setLast(String path) {
        if (path == null) {
            prefs.remove(KEY_LAST);
        } else {
            prefs.put(KEY_LAST, path);
        }
    }

    /**
     * Add a path to the recent list (MRU order).
     * Moves to front if already present. Trims to MAX_RECENT.
     */
    public void addPath(String path) {
        if (path == null || path.isBlank())
            return;
        List<String> list = getRecentMutable();
        list.remove(path); // deduplicate
        list.add(0, path); // MRU first
        if (list.size() > MAX_RECENT) {
            list = list.subList(0, MAX_RECENT);
        }
        saveRecent(list);
        setLast(path);
    }

    /** Remove a path from the recent list. */
    public void removePath(String path) {
        if (path == null)
            return;
        List<String> list = getRecentMutable();
        list.remove(path);
        saveRecent(list);
        // If removed path was the last, clear or update last
        if (path.equals(getLast())) {
            setLast(list.isEmpty() ? null : list.get(0));
        }
    }

    /** Get saved OAuth token (or null). */
    public String getOauthToken() {
        return prefs.get(KEY_OAUTH, null);
    }

    /** Save OAuth token. */
    public void setOauthToken(String token) {
        if (token == null || token.isBlank()) {
            prefs.remove(KEY_OAUTH);
        } else {
            prefs.put(KEY_OAUTH, token);
        }
    }

    /** Clear all recent paths, last path, and OAuth token. */
    public void clearAll() {
        prefs.remove(KEY_RECENT);
        prefs.remove(KEY_LAST);
    }

    /**
     * Get the list of recent paths (MRU order).
     * Filters out paths whose files no longer exist.
     */
    public List<String> getRecent() {
        List<String> raw = getRecentMutable();
        // Filter out non-existent files
        List<String> valid = new ArrayList<>();
        boolean changed = false;
        for (String p : raw) {
            if (new File(p).isFile()) {
                valid.add(p);
            } else {
                changed = true;
            }
        }
        if (changed) {
            saveRecent(valid);
        }
        return List.copyOf(valid);
    }

    // ---- internal ----

    private List<String> getRecentMutable() {
        String stored = prefs.get(KEY_RECENT, "");
        if (stored.isEmpty())
            return new ArrayList<>();
        List<String> list = new ArrayList<>();
        for (String s : stored.split("\\|")) {
            if (!s.isBlank())
                list.add(s);
        }
        return list;
    }

    private void saveRecent(List<String> paths) {
        prefs.put(KEY_RECENT, String.join(SEPARATOR, paths));
    }
}
