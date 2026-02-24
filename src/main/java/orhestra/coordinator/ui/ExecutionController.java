package orhestra.coordinator.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import orhestra.coordinator.config.Dependencies;
import orhestra.coordinator.core.AppBus;
import orhestra.coordinator.model.Spot;
import orhestra.coordinator.model.Task;
import orhestra.coordinator.model.TaskStatus;
import orhestra.coordinator.server.CoordinatorNettyServer;
import orhestra.coordinator.simulation.SimulationService;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified controller for the Execution tab.
 * Top: SPOT worker cards. Bottom: Task table with progress/SpotId.
 */
public class ExecutionController {

    // ---- Task table ----
    @FXML
    private TableView<TaskInfo> taskTable;
    @FXML
    private TableColumn<TaskInfo, String> idColumn, algColumn, funcColumn, runtimeColumn, statusColumn, progressColumn;
    @FXML
    private TableColumn<TaskInfo, String> iterColumn;
    @FXML
    private TableColumn<TaskInfo, String> agentsColumn, dimensionColumn;
    @FXML
    private TableColumn<TaskInfo, String> spotIdColumn;

    // ---- Stats ----
    @FXML
    private Label lblNew, lblRunning, lblDone, lblFailed;

    // ---- Spot cards ----
    @FXML
    private FlowPane spotCards;
    @FXML
    private Label lblSpotCount;

    // ---- Simulation ----
    @FXML
    private Spinner<Integer> simWorkers;
    @FXML
    private TextField simDelayMin, simDelayMax, simFailRate;
    @FXML
    private Button btnStartSim, btnStopSim;

    // ---- JSON files ----
    @FXML
    private ComboBox<String> recentJsonCombo;

    private Timer retryTimer;
    private SimulationService simulationService;
    private final RecentJsonManager recentJson = new RecentJsonManager();

    @FXML
    private void initialize() {
        // ---- Task table columns ----
        idColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id()));
        algColumn.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().algId() != null ? c.getValue().algId() : "—"));
        funcColumn.setCellValueFactory(c -> new SimpleStringProperty(extractFunc(c.getValue().payload())));

        iterColumn.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().inputIterations() == null ? "—" : String.valueOf(c.getValue().inputIterations())));

        if (agentsColumn != null) {
            agentsColumn.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().inputAgents() == null ? "—" : String.valueOf(c.getValue().inputAgents())));
        }
        if (dimensionColumn != null) {
            dimensionColumn.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().inputDimension() == null ? "—" : String.valueOf(c.getValue().inputDimension())));
        }
        if (spotIdColumn != null) {
            spotIdColumn.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().assignedTo() == null ? "—" : c.getValue().assignedTo()));
        }

        runtimeColumn.setCellValueFactory(c -> new SimpleStringProperty(formatRuntime(c.getValue().runtimeMs())));

        // Status badge
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().status())));
        statusColumn.setCellFactory(col -> new TableCell<>() {
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
                switch (st) {
                    case "DONE" -> badge.getStyleClass().add("status-done");
                    case "FAILED" -> badge.getStyleClass().add("status-failed");
                    case "RUNNING" -> badge.getStyleClass().add("status-running");
                    case "NEW" -> badge.getStyleClass().add("status-new");
                    default -> {
                    }
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // Progress (status-aware)
        progressColumn.setCellValueFactory(c -> new SimpleStringProperty(
                calcProgress(c.getValue().status(), c.getValue().payload(), c.getValue().iter())));

        // ---- Simulation spinner ----
        if (simWorkers != null) {
            simWorkers.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 200, 20));
        }

        // ---- Recent JSON ComboBox ----
        if (recentJsonCombo != null) {
            recentJsonCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(String p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(empty || p == null ? null : RecentJsonManager.displayName(p));
                }
            });
            recentJsonCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(String p, boolean empty) {
                    super.updateItem(p, empty);
                    setText(empty || p == null ? null : RecentJsonManager.displayName(p));
                }
            });
            refreshJsonCombo();
            String lastJson = recentJson.getLast();
            if (lastJson != null && new File(lastJson).isFile()) {
                recentJsonCombo.setValue(lastJson);
            }
        }

        // ---- Event listeners ----
        AppBus.onTasksChanged(() -> Platform.runLater(this::refreshTasks));
        AppBus.onSpotsChanged(() -> Platform.runLater(this::refreshSpots));

        // Initial load
        refreshAll();
    }

    // ================== Refresh ==================

    private void refreshAll() {
        refreshTasks();
        refreshSpots();
    }

    @FXML
    public void refreshTasks() {
        var deps = CoordinatorNettyServer.tryDependencies();
        if (deps == null) {
            taskTable.getItems().clear();
            scheduleRetry();
            return;
        }
        cancelRetryTimer();

        List<TaskInfo> items = deps.taskService().findRecent(200)
                .stream().map(this::toTaskInfo).collect(Collectors.toList());

        taskTable.getItems().setAll(items);
        updateStats(items);
        taskTable.refresh();
    }

    private void refreshSpots() {
        Dependencies deps = CoordinatorNettyServer.tryDependencies();
        if (deps == null)
            return;

        List<Spot> spots = deps.spotService().findAll();

        if (spotCards != null) {
            spotCards.getChildren().clear();
            for (Spot spot : spots) {
                spotCards.getChildren().add(buildSpotCard(spot));
            }
        }
        if (lblSpotCount != null) {
            lblSpotCount.setText("Spots: " + spots.size());
        }
    }

    // ================== Spot Cards ==================

    private VBox buildSpotCard(Spot spot) {
        VBox card = new VBox(3);
        card.setPadding(new Insets(8));
        card.setPrefWidth(130);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("spot-card");

        String status = spot.status() != null ? spot.status().name() : "DOWN";
        boolean isUp = "UP".equals(status);

        // Check if stale (>5s since last heartbeat)
        long age = spot.lastHeartbeat() == null ? Long.MAX_VALUE
                : Math.max(0, Duration.between(spot.lastHeartbeat(), Instant.now()).getSeconds());
        if (age > 5)
            isUp = false;

        if (isUp)
            card.getStyleClass().add("spot-card-up");
        else
            card.getStyleClass().add("spot-card-down");

        Label idLabel = new Label(spot.id());
        idLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");

        double cpu = spot.cpuLoad();
        int tasks = spot.runningTasks();

        Label cpuLabel = new Label(String.format("CPU: %.0f%%", cpu));
        cpuLabel.setStyle("-fx-font-size: 11;");

        Label tasksLabel = new Label("Tasks: " + tasks);
        tasksLabel.setStyle("-fx-font-size: 11;");

        Label beatLabel = new Label(age == Long.MAX_VALUE ? "—" : age + "s ago");
        beatLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");

        card.getChildren().addAll(idLabel, cpuLabel, tasksLabel, beatLabel);
        return card;
    }

    // ================== Stats ==================

    private void updateStats(List<TaskInfo> items) {
        long nw = items.stream().filter(t -> "NEW".equals(t.status())).count();
        long ru = items.stream().filter(t -> "RUNNING".equals(t.status())).count();
        long dn = items.stream().filter(t -> "DONE".equals(t.status())).count();
        long fl = items.stream().filter(t -> "FAILED".equals(t.status())).count();
        if (lblNew != null)
            lblNew.setText(String.valueOf(nw));
        if (lblRunning != null)
            lblRunning.setText(String.valueOf(ru));
        if (lblDone != null)
            lblDone.setText(String.valueOf(dn));
        if (lblFailed != null)
            lblFailed.setText(String.valueOf(fl));
    }

    // ================== Task model mapping ==================

    private TaskInfo toTaskInfo(Task task) {
        String algDisplay = task.algorithm();
        if (algDisplay == null || algDisplay.isBlank())
            algDisplay = extractAlg(task.payload());
        return new TaskInfo(
                task.id(), algDisplay,
                task.status() != null ? task.status().name() : "NEW",
                task.iter(), task.runtimeMs(), task.fopt(),
                task.payload(), task.assignedTo(),
                task.startedAt(), task.finishedAt(),
                task.inputIterations(), task.inputAgents(), task.inputDimension());
    }

    // ================== Simulation ==================

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

    // ================== JSON file handling ==================

    @FXML
    private void onAddJsonFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите JSON с задачами");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        String currentVal = recentJsonCombo != null ? recentJsonCombo.getValue() : null;
        if (currentVal != null) {
            File dir = new File(currentVal).getParentFile();
            if (dir != null && dir.isDirectory())
                fc.setInitialDirectory(dir);
        }
        File f = fc.showOpenDialog(taskTable.getScene().getWindow());
        if (f == null)
            return;

        recentJson.addPath(f.getAbsolutePath());
        refreshJsonCombo();
        if (recentJsonCombo != null)
            recentJsonCombo.setValue(f.getAbsolutePath());
        loadJsonFile(f);
    }

    @FXML
    private void handleLoadSelectedJson() {
        if (recentJsonCombo == null)
            return;
        String selected = recentJsonCombo.getValue();
        if (selected == null || selected.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Выберите JSON файл из списка", ButtonType.OK).showAndWait();
            return;
        }
        File f = new File(selected);
        if (!f.isFile()) {
            new Alert(Alert.AlertType.WARNING, "Файл не найден: " + selected, ButtonType.OK).showAndWait();
            recentJson.removePath(selected);
            refreshJsonCombo();
            return;
        }
        loadJsonFile(f);
    }

    @FXML
    private void handleRemoveJson() {
        if (recentJsonCombo == null)
            return;
        String selected = recentJsonCombo.getValue();
        if (selected != null) {
            recentJson.removePath(selected);
            refreshJsonCombo();
        }
    }

    @FXML
    private void handleClearJson() {
        recentJson.clearAll();
        refreshJsonCombo();
    }

    private void refreshJsonCombo() {
        if (recentJsonCombo == null)
            return;
        recentJsonCombo.getItems().setAll(recentJson.getRecent());
    }

    private void loadJsonFile(File f) {
        try {
            String txt = java.nio.file.Files.readString(f.toPath());
            com.fasterxml.jackson.databind.ObjectMapper M = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = M.readTree(txt);

            List<Task> tasksToCreate = new ArrayList<>();
            var deps = CoordinatorNettyServer.dependencies();

            if (root.has("algorithms")) {
                var algs = M.convertValue(root.get("algorithms"),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                        });
                var iters = expandRange(root.path("iterations"));
                var dims = expandRange(root.path("dimension"));
                if (dims.isEmpty())
                    dims = java.util.List.of(2);
                if (iters.isEmpty())
                    iters = java.util.List.of(100);

                for (String alg : algs) {
                    for (Integer iterMax : iters) {
                        for (Integer dim : dims) {
                            var payload = new java.util.LinkedHashMap<String, Object>();
                            payload.put("alg", alg);
                            payload.put("iterations", java.util.Map.of("max", iterMax));
                            payload.put("dimension", dim);
                            String payloadJson = M.writeValueAsString(payload);
                            tasksToCreate.add(Task.builder()
                                    .id(UUID.randomUUID().toString())
                                    .payload(payloadJson).status(TaskStatus.NEW).priority(0)
                                    .algorithm(alg).inputIterations(iterMax).inputDimension(dim)
                                    .build());
                        }
                    }
                }
            } else if (root.isArray()) {
                for (var n : root) {
                    String payloadJson = n.toString();
                    String pAlg = null;
                    Integer pIter = null, pAgents = null, pDim = null;
                    try {
                        if (n.has("alg") && !n.get("alg").isNull())
                            pAlg = n.get("alg").asText();
                        var iterN = n.path("iterations");
                        if (iterN.isObject() && iterN.has("max"))
                            pIter = iterN.get("max").asInt();
                        else if (iterN.isNumber())
                            pIter = iterN.asInt();
                        if (n.has("agents") && n.get("agents").isNumber())
                            pAgents = n.get("agents").asInt();
                        if (n.has("dimension") && n.get("dimension").isNumber())
                            pDim = n.get("dimension").asInt();
                    } catch (Exception ignored) {
                    }

                    tasksToCreate.add(Task.builder()
                            .id(UUID.randomUUID().toString())
                            .payload(payloadJson).status(TaskStatus.NEW).priority(0)
                            .algorithm(pAlg).inputIterations(pIter).inputAgents(pAgents).inputDimension(pDim)
                            .build());
                }
            } else {
                throw new IllegalArgumentException("Неизвестный формат JSON.");
            }

            if (!tasksToCreate.isEmpty())
                deps.taskService().createTasks(tasksToCreate);
            recentJson.addPath(f.getAbsolutePath());
            refreshJsonCombo();
            AppBus.fireTasksChanged();
            refreshTasks();
            new Alert(Alert.AlertType.INFORMATION, "Добавлено задач: " + tasksToCreate.size(), ButtonType.OK)
                    .showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Ошибка загрузки: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    // ================== Retry timer ==================

    private void scheduleRetry() {
        if (retryTimer != null)
            return;
        retryTimer = new Timer("exec-retry", true);
        retryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    retryTimer = null;
                    refreshAll();
                });
            }
        }, 2000);
    }

    private void cancelRetryTimer() {
        if (retryTimer != null) {
            retryTimer.cancel();
            retryTimer = null;
        }
    }

    // ================== Helpers ==================

    private static java.util.List<Integer> expandRange(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull())
            return out;
        if (node.isObject()) {
            int min = node.path("min").asInt(0);
            int max = node.path("max").asInt(min);
            int step = Math.max(1, node.path("step").asInt(1));
            for (int v = min; v <= max; v += step)
                out.add(v);
        } else if (node.isInt()) {
            out.add(node.asInt());
        }
        return out;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String formatRuntime(Long ms) {
        if (ms == null || ms <= 0)
            return "—";
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        return m > 0 ? m + "m " + sec + "s" : sec + "s";
    }

    private static String extractAlg(String j) {
        if (j == null || j.isBlank())
            return "—";
        try {
            int i = j.indexOf("\"alg\"");
            if (i < 0)
                return "—";
            int c = j.indexOf(':', i);
            int q1 = j.indexOf('"', c);
            int q2 = j.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1)
                return j.substring(q1 + 1, q2);
        } catch (Exception e) {
        }
        return "—";
    }

    private static String extractFunc(String j) {
        if (j == null || j.isBlank())
            return "—";
        try {
            int i = j.indexOf("\"func\"");
            if (i < 0)
                return "—";
            int c = j.indexOf(':', i);
            int q1 = j.indexOf('"', c);
            int q2 = j.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1)
                return j.substring(q1 + 1, q2);
        } catch (Exception e) {
        }
        return "—";
    }

    private static String calcProgress(String status, String payload, Integer iter) {
        if ("DONE".equals(status))
            return "100%";
        if ("FAILED".equals(status))
            return "—";
        if (iter == null)
            return "—";
        Integer max = extractIterationsMax(payload);
        if (max == null || max <= 0)
            return iter + " it";
        int pct = (int) Math.max(0, Math.min(100, Math.round(iter * 100.0 / max)));
        return pct + "% (" + iter + "/" + max + ")";
    }

    private static Integer extractIterationsMax(String j) {
        if (j == null)
            return null;
        try {
            int p = j.indexOf("\"iterations\"");
            int bs = (p >= 0) ? p : j.indexOf("\"iterMax\"");
            if (bs < 0)
                return null;
            int mk = j.indexOf("\"max\"", bs);
            if (mk >= 0) {
                int c = j.indexOf(':', mk);
                int e = c < 0 ? -1 : findNumberEnd(j, c + 1);
                if (e > 0) {
                    String n = j.substring(c + 1, e).replaceAll("[^0-9]", "");
                    if (!n.isEmpty())
                        return Integer.parseInt(n);
                }
            }
            int im = j.indexOf("\"iterMax\"");
            if (im >= 0) {
                int c = j.indexOf(':', im);
                int e = c < 0 ? -1 : findNumberEnd(j, c + 1);
                if (e > 0) {
                    String n = j.substring(c + 1, e).replaceAll("[^0-9]", "");
                    if (!n.isEmpty())
                        return Integer.parseInt(n);
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static int findNumberEnd(String s, int from) {
        int i = from;
        while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ':'))
            i++;
        while (i < s.length() && Character.isDigit(s.charAt(i)))
            i++;
        return i;
    }

    private static int parseIntOr(TextField tf, int fb) {
        if (tf == null || tf.getText() == null || tf.getText().isBlank())
            return fb;
        try {
            return Integer.parseInt(tf.getText().trim());
        } catch (NumberFormatException e) {
            return fb;
        }
    }

    private static double parseDoubleOr(TextField tf, double fb) {
        if (tf == null || tf.getText() == null || tf.getText().isBlank())
            return fb;
        try {
            return Double.parseDouble(tf.getText().trim());
        } catch (NumberFormatException e) {
            return fb;
        }
    }
}
