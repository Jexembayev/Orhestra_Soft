package orhestra.coordinator.model;

import javafx.beans.property.*;

public class SpotRow {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty  addr = new SimpleStringProperty("");
    private final DoubleProperty  cpu = new SimpleDoubleProperty(0.0);      // 0..100
    private final IntegerProperty tasks = new SimpleIntegerProperty(0);
    private final LongProperty    lastBeatEpoch = new SimpleLongProperty(0); // seconds
    private final StringProperty  status = new SimpleStringProperty("INIT");
    private final IntegerProperty totalCores = new SimpleIntegerProperty(0); // 👈 новое поле

    public static SpotRow of(int id, String addr) {
        SpotRow r = new SpotRow();
        r.setId(id);
        r.setAddr(addr);
        return r;
    }

    public int getId() { return id.get(); }
    public void setId(int v) { id.set(v); }
    public IntegerProperty idProperty() { return id; }

    public String getAddr() { return addr.get(); }
    public void setAddr(String v) { addr.set(v); }
    public StringProperty addrProperty() { return addr; }

    public double getCpu() { return cpu.get(); }
    public void setCpu(double v) { cpu.set(v); }
    public DoubleProperty cpuProperty() { return cpu; }

    public int getTasks() { return tasks.get(); }
    public void setTasks(int v) { tasks.set(v); }
    public IntegerProperty tasksProperty() { return tasks; }

    public long getLastBeatEpoch() { return lastBeatEpoch.get(); }
    public void setLastBeatEpoch(long v) { lastBeatEpoch.set(v); }
    public LongProperty lastBeatEpochProperty() { return lastBeatEpoch; }

    public String getStatus() { return status.get(); }
    public void setStatus(String v) { status.set(v); }
    public StringProperty statusProperty() { return status; }

    // 👇 новое свойство totalCores
    public int getTotalCores() { return totalCores.get(); }
    public void setTotalCores(int v) { totalCores.set(v); }
    public IntegerProperty totalCoresProperty() { return totalCores; }
}

