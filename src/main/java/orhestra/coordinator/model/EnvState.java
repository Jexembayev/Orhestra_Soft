package orhestra.coordinator.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class EnvState {
    private final BooleanProperty vpn   = new SimpleBooleanProperty(false);
    private final BooleanProperty cloud = new SimpleBooleanProperty(false);
    private final BooleanProperty ovpn  = new SimpleBooleanProperty(false);
    private final BooleanProperty coord = new SimpleBooleanProperty(false);

    // агрегат — удобно подписываться один раз
    private final BooleanProperty any = new SimpleBooleanProperty(false);

    public EnvState() {
        vpn.addListener((o,a,b)->recalc());
        cloud.addListener((o,a,b)->recalc());
        ovpn.addListener((o,a,b)->recalc());
        coord.addListener((o,a,b)->recalc());
        recalc();
    }
    private void recalc() { any.set(vpn.get() || cloud.get() || ovpn.get() || coord.get()); }

    public boolean isVpn() { return vpn.get(); }
    public void setVpn(boolean v) { vpn.set(v); }
    public BooleanProperty vpnProperty() { return vpn; }

    public boolean isCloud() { return cloud.get(); }
    public void setCloud(boolean v) { cloud.set(v); }
    public BooleanProperty cloudProperty() { return cloud; }

    public boolean isOvpn() { return ovpn.get(); }
    public void setOvpn(boolean v) { ovpn.set(v); }
    public BooleanProperty ovpnProperty() { return ovpn; }

    public boolean isCoord() { return coord.get(); }
    public void setCoord(boolean v) { coord.set(v); }
    public BooleanProperty coordProperty() { return coord; }

    public BooleanProperty anyProperty() { return any; }
}

