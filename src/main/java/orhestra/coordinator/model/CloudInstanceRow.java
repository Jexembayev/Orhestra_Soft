package orhestra.coordinator.model;

import javafx.beans.property.*;

public class CloudInstanceRow {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty zone = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty ip = new SimpleStringProperty();
    private final StringProperty createdAt = new SimpleStringProperty();
    private final StringProperty preemptible = new SimpleStringProperty();

    public static CloudInstanceRow of(String id, String name, String zone, String status, String ip, String createdAt, boolean spot) {
        var r = new CloudInstanceRow();
        r.setId(id); r.setName(name); r.setZone(zone); r.setStatus(status);
        r.setIp(ip); r.setCreatedAt(createdAt); r.setPreemptible(spot ? "Yes" : "No");
        return r;
    }

    public String getId(){ return id.get(); }
    public StringProperty idProperty(){ return id; }
    public void setId(String v){ id.set(v); }

    public StringProperty nameProperty(){ return name; }
    public void setName(String v){ name.set(v); }

    public StringProperty zoneProperty(){ return zone; }
    public void setZone(String v){ zone.set(v); }

    public StringProperty statusProperty(){ return status; }
    public void setStatus(String v){ status.set(v); }

    public StringProperty ipProperty(){ return ip; }
    public void setIp(String v){ ip.set(v); }

    public StringProperty createdAtProperty(){ return createdAt; }
    public void setCreatedAt(String v){ createdAt.set(v); }

    public StringProperty preemptibleProperty(){ return preemptible; }
    public void setPreemptible(String v){ preemptible.set(v); }
}
