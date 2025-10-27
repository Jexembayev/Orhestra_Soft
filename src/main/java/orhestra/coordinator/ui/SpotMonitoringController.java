package orhestra.coordinator.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import orhestra.coordinator.core.AppBus;
import orhestra.coordinator.server.CoordinatorNettyServer;
import orhestra.coordinator.store.model.SpotNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SpotMonitoringController {

    @FXML private TableView<SpotNode> table;
    @FXML private TableColumn<SpotNode, String>  colId;
    @FXML private TableColumn<SpotNode, String>  colAddr;
    @FXML private TableColumn<SpotNode, Double>  colCPU;      // ВАЖНО: Double, не Number
    @FXML private TableColumn<SpotNode, Number>  colTasks;
    @FXML private TableColumn<SpotNode, String>  colBeat;
    @FXML private TableColumn<SpotNode, String>  colStatus;
    @FXML private Label lblTotal;

    private final ObservableList<SpotNode> data = FXCollections.observableArrayList();
    private static final PseudoClass DOWN = PseudoClass.getPseudoClass("down");

    @FXML
    private void initialize() {
        table.setItems(data);

        // ID
        colId.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().spotId() == null ? "—" : d.getValue().spotId()
        ));

        // Address
        colAddr.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().lastIp() == null ? "—" : d.getValue().lastIp()
        ));
        colAddr.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String ip, boolean empty) {
                super.updateItem(ip, empty);
                setText(empty ? null : ip);
                setStyle(empty ? null : "-fx-font-family: 'JetBrains Mono', monospace;");
            }
        });

        // Tasks
        colTasks.setCellValueFactory(d -> new SimpleIntegerProperty(
                d.getValue().runningTasks() == null ? 0 : d.getValue().runningTasks()
        ));

        // CPU: 0..100 → 0..1 (ProgressBar) + подпись
        colCPU.setCellValueFactory(d -> {
            double percent = d.getValue().cpuLoad() == null ? 0.0 : d.getValue().cpuLoad();
            double v01 = Math.max(0, Math.min(1, percent / 100.0));
            return new ReadOnlyObjectWrapper<>(v01);
        });
        colCPU.setCellFactory(tc -> new ProgressBarTableCell<>() {
            @Override public void updateItem(Double v01, boolean empty) {
                super.updateItem(v01, empty);
                if (empty || v01 == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f%%", v01 * 100.0));
                }
            }
        });

        // LastBeat: "Xs ago"
        colBeat.setCellValueFactory(d -> {
            Instant ls = d.getValue().lastSeen();
            if (ls == null) return new SimpleStringProperty("—");
            long age = Math.max(0, Duration.between(ls, Instant.now()).getSeconds());
            return new SimpleStringProperty(age + "s ago");
        });

        // Status: бейдж
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().status() == null ? "DOWN" : d.getValue().status()
        ));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String st, boolean empty) {
                super.updateItem(st, empty);
                if (empty || st == null) { setText(null); setGraphic(null); return; }
                Label badge = new Label(st);
                badge.getStyleClass().add("status-badge");
                if ("UP".equalsIgnoreCase(st)) badge.getStyleClass().add("status-up");
                else badge.getStyleClass().add("status-down");
                setGraphic(badge);
                setText(null);
            }
        });

        // Подсветка строк: DOWN если lastSeen > 5s или статус != UP
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(SpotNode n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { pseudoClassStateChanged(DOWN, false); setTooltip(null); return; }
                long age = n.lastSeen() == null ? Long.MAX_VALUE
                        : Math.max(0, Duration.between(n.lastSeen(), Instant.now()).getSeconds());
                boolean isDown = age > 5 || !"UP".equalsIgnoreCase(String.valueOf(n.status()));
                getStyleClass().removeAll("row-down");
                if (isDown) getStyleClass().add("row-down");

                setTooltip(new Tooltip(
                        "IP: " + (n.lastIp()==null? "—" : n.lastIp()) +
                                "\nCores: " + (n.totalCores()==null? 0 : n.totalCores()) +
                                "\nLast beat: " + (age==Long.MAX_VALUE? "—" : age + "s ago")));
            }
        });

        // Авто-обновление (дергается из /heartbeat)
        AppBus.onSpotsChanged(() -> Platform.runLater(this::refreshSpots));

        // Счётчик всего — биндим один раз
        lblTotal.textProperty().bind(Bindings.size(data).asString());

        // Первичное заполнение
        refreshSpots();
    }

    private void refreshSpots() {
        List<SpotNode> snap = CoordinatorNettyServer.REGISTRY.snapshotNodes();
        data.setAll(new ArrayList<>(snap));
    }

}
