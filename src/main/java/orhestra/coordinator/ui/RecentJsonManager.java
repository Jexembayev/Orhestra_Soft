package orhestra.coordinator.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages recently used JSON task files.
 * Backed by java.util.prefs.Preferences — persists across app restarts.
 *
 * Storage keys: lastJsonPath, recentJsonPaths (pipe-delimited, max 5).
 */
public final class RecentJsonManager {

    private static final int MAX_RECENT = 5;
    private static final String KEY_LAST = "lastJsonPath";
    private static final String KEY_RECENT = "recentJsonPaths";
    private static final String SEPARATOR = "|";

    private final Preferences prefs;

    public RecentJsonManager() {
        this.prefs = Preferences.userNodeForPackage(RecentJsonManager.class);
    }

    public String getLast() {
        return prefs.get(KEY_LAST, null);
    }

    public void setLast(String path) {
        if (path == null)
            prefs.remove(KEY_LAST);
        else
            prefs.put(KEY_LAST, path);
    }

    /** Add path to MRU list. Deduplicates and trims to MAX_RECENT. */
    public void addPath(String path) {
        if (path == null || path.isBlank())
            return;
        List<String> list = getRawList();
        list.remove(path);
        list.add(0, path);
        if (list.size() > MAX_RECENT)
            list = list.subList(0, MAX_RECENT);
        saveList(list);
        setLast(path);
    }

    public void removePath(String path) {
        if (path == null)
            return;
        List<String> list = getRawList();
        list.remove(path);
        saveList(list);
        if (path.equals(getLast())) {
            setLast(list.isEmpty() ? null : list.get(0));
        }
    }

    public void clearAll() {
        prefs.remove(KEY_RECENT);
        prefs.remove(KEY_LAST);
    }

    /** Get recent paths, filtering out non-existent files. */
    public List<String> getRecent() {
        List<String> raw = getRawList();
        List<String> valid = new ArrayList<>();
        boolean changed = false;
        for (String p : raw) {
            if (new File(p).isFile()) {
                valid.add(p);
            } else {
                changed = true;
            }
        }
        if (changed)
            saveList(valid);
        return List.copyOf(valid);
    }

    /** Get display name (filename only) for a full path. */
    public static String displayName(String fullPath) {
        if (fullPath == null)
            return "";
        return new File(fullPath).getName();
    }

    // ---- internal ----

    private List<String> getRawList() {
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

    private void saveList(List<String> paths) {
        prefs.put(KEY_RECENT, String.join(SEPARATOR, paths));
    }
}
