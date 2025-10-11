// src/main/java/orhestra/ui/model/SpotRow.java
package orhestra.ui.model;

import javafx.beans.property.*;

public class SpotRow {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty ip = new SimpleStringProperty();
    private final IntegerProperty cpu = new SimpleIntegerProperty();
    private final IntegerProperty activeTasks = new SimpleIntegerProperty();
    private final IntegerProperty cores = new SimpleIntegerProperty();
    private final StringProperty lastSeen = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();

    public static SpotRow of(String id, String ip, int cpu, int active, int cores, String lastSeen, String status) {
        var r = new SpotRow();
        r.setId(id); r.setIp(ip); r.setCpu(cpu); r.setActiveTasks(active);
        r.setCores(cores); r.setLastSeen(lastSeen); r.setStatus(status);
        return r;
    }

    // getters/properties
    public StringProperty idProperty() { return id; }
    public void setId(String v) { id.set(v); }
    public StringProperty ipProperty() { return ip; }
    public void setIp(String v) { ip.set(v); }
    public IntegerProperty cpuProperty() { return cpu; }
    public void setCpu(int v) { cpu.set(v); }
    public IntegerProperty activeTasksProperty() { return activeTasks; }
    public void setActiveTasks(int v) { activeTasks.set(v); }
    public IntegerProperty coresProperty() { return cores; }
    public void setCores(int v) { cores.set(v); }
    public StringProperty lastSeenProperty() { return lastSeen; }
    public void setLastSeen(String v) { lastSeen.set(v); }
    public StringProperty statusProperty() { return status; }
    public void setStatus(String v) { status.set(v); }
}
