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
import orhestra.coordinator.config.Dependencies;
import orhestra.coordinator.core.AppBus;
import orhestra.coordinator.model.Spot;
import orhestra.coordinator.server.CoordinatorNettyServer;
import orhestra.coordinator.simulation.SimulationService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class SpotMonitoringController {

    @FXML
    private TableView<SpotInfo> table;
    @FXML
    private TableColumn<SpotInfo, String> colId;
    @FXML
    private TableColumn<SpotInfo, String> colAddr;
    @FXML
    private TableColumn<SpotInfo, Double> colCPU; // ВАЖНО: Double, не Number
    @FXML
    private TableColumn<SpotInfo, Number> colTasks;
    @FXML
    private TableColumn<SpotInfo, String> colBeat;
    @FXML
    private TableColumn<SpotInfo, String> colStatus;
    @FXML
    private Label lblTotal;

    // Simulation controls
    @FXML
    private Spinner<Integer> simWorkers;
    @FXML
    private TextField simDelayMin, simDelayMax, simFailRate;
    @FXML
    private Button btnStartSim, btnStopSim;

    private final ObservableList<SpotInfo> data = FXCollections.observableArrayList();
    private static final PseudoClass DOWN = PseudoClass.getPseudoClass("down");

    private Timer retryTimer;
    private SimulationService simulationService;

    @FXML
    private void initialize() {
        table.setItems(data);

        // ID
        colId.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().spotId() == null ? "—" : d.getValue().spotId()));

        // Address
        colAddr.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().lastIp() == null ? "—" : d.getValue().lastIp()));
        colAddr.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String ip, boolean empty) {
                super.updateItem(ip, empty);
                setText(empty ? null : ip);
                setStyle(empty ? null : "-fx-font-family: 'JetBrains Mono', monospace;");
            }
        });

        // Tasks
        colTasks.setCellValueFactory(d -> new SimpleIntegerProperty(
                d.getValue().runningTasks() == null ? 0 : d.getValue().runningTasks()));

        // CPU: 0..100 → 0..1 (ProgressBar) + подпись
        colCPU.setCellValueFactory(d -> {
            double percent = d.getValue().cpuLoad() == null ? 0.0 : d.getValue().cpuLoad();
            double v01 = Math.max(0, Math.min(1, percent / 100.0));
            return new ReadOnlyObjectWrapper<>(v01);
        });
        colCPU.setCellFactory(tc -> new ProgressBarTableCell<>() {
            @Override
            public void updateItem(Double v01, boolean empty) {
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
            if (ls == null)
                return new SimpleStringProperty("—");
            long age = Math.max(0, Duration.between(ls, Instant.now()).getSeconds());
            return new SimpleStringProperty(age + "s ago");
        });

        // Status: бейдж
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().status() == null ? "DOWN" : d.getValue().status()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String st, boolean empty) {
                super.updateItem(st, empty);
                if (empty || st == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(st);
                badge.getStyleClass().add("status-badge");
                if ("UP".equalsIgnoreCase(st))
                    badge.getStyleClass().add("status-up");
                else
                    badge.getStyleClass().add("status-down");
                setGraphic(badge);
                setText(null);
            }
        });

        // Подсветка строк: DOWN если lastSeen > 5s или статус != UP
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(SpotInfo n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) {
                    pseudoClassStateChanged(DOWN, false);
                    setTooltip(null);
                    return;
                }
                long age = n.lastSeen() == null ? Long.MAX_VALUE
                        : Math.max(0, Duration.between(n.lastSeen(), Instant.now()).getSeconds());
                boolean isDown = age > 5 || !"UP".equalsIgnoreCase(String.valueOf(n.status()));
                getStyleClass().removeAll("row-down");
                if (isDown)
                    getStyleClass().add("row-down");

                setTooltip(new Tooltip(
                        "IP: " + (n.lastIp() == null ? "—" : n.lastIp()) +
                                "\nCores: " + (n.totalCores() == null ? 0 : n.totalCores()) +
                                "\nLast beat: " + (age == Long.MAX_VALUE ? "—" : age + "s ago")));
            }
        });

        // Авто-обновление (дергается из /heartbeat)
        AppBus.onSpotsChanged(() -> Platform.runLater(this::refreshSpots));

        // Счётчик всего — биндим один раз
        lblTotal.textProperty().bind(Bindings.size(data).asString());

        // Simulation Spinner init
        if (simWorkers != null) {
            simWorkers.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 200, 20));
        }

        // Первичное заполнение - resilient to server not started
        refreshSpots();
    }

    private void refreshSpots() {
        Dependencies deps = CoordinatorNettyServer.tryDependencies();
        if (deps == null) {
            // Server not started yet - show placeholder and schedule retry
            data.clear();
            data.add(new SpotInfo("—", null, null, "Server not started", null, null, "Waiting..."));
            scheduleRetry();
            return;
        }

        // Cancel any pending retry
        cancelRetryTimer();

        List<SpotInfo> snap = deps
                .spotService()
                .findAll()
                .stream()
                .map(this::toSpotInfo)
                .collect(Collectors.toList());

        data.setAll(new ArrayList<>(snap));
    }

    private void scheduleRetry() {
        if (retryTimer != null)
            return; // Already scheduled

        retryTimer = new Timer("spot-monitor-retry", true);
        retryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    retryTimer = null;
                    refreshSpots();
                });
            }
        }, 2000); // Retry after 2 seconds
    }

    private void cancelRetryTimer() {
        if (retryTimer != null) {
            retryTimer.cancel();
            retryTimer = null;
        }
    }

    /**
     * Convert Spot model to SpotInfo for UI display.
     */
    private SpotInfo toSpotInfo(Spot spot) {
        return new SpotInfo(
                spot.id(),
                spot.cpuLoad(),
                spot.runningTasks(),
                spot.status() != null ? spot.status().name() : "DOWN",
                spot.lastHeartbeat(),
                spot.totalCores(),
                spot.ipAddress());
    }

    // ---- Simulation handlers ----

    @FXML
    private void handleStartSim() {
        Dependencies deps = CoordinatorNettyServer.tryDependencies();
        if (deps == null) {
            new Alert(Alert.AlertType.WARNING, "Server not started", ButtonType.OK).showAndWait();
            return;
        }

        int workers = simWorkers != null ? simWorkers.getValue() : 20;
        int delayMin = parseIntOr(simDelayMin, 200);
        int delayMax = parseIntOr(simDelayMax, 1200);
        double failRate = parseDoubleOr(simFailRate, 0.0);

        simulationService = new SimulationService(deps.spotService(), deps.taskService());
        simulationService.start(workers, delayMin, delayMax, failRate);

        if (btnStartSim != null)
            btnStartSim.setDisable(true);
        if (btnStopSim != null)
            btnStopSim.setDisable(false);
    }

    @FXML
    private void handleStopSim() {
        if (simulationService != null) {
            simulationService.stop();
            simulationService = null;
        }
        if (btnStartSim != null)
            btnStartSim.setDisable(false);
        if (btnStopSim != null)
            btnStopSim.setDisable(true);
    }

    private static int parseIntOr(TextField tf, int fallback) {
        if (tf == null || tf.getText() == null || tf.getText().isBlank())
            return fallback;
        try {
            return Integer.parseInt(tf.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDoubleOr(TextField tf, double fallback) {
        if (tf == null || tf.getText() == null || tf.getText().isBlank())
            return fallback;
        try {
            return Double.parseDouble(tf.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
