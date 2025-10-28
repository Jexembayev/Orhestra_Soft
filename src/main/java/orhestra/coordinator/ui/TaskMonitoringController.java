package orhestra.coordinator.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import orhestra.coordinator.core.AppBus;
import orhestra.coordinator.server.CoordinatorNettyServer;
import orhestra.coordinator.store.dao.TaskDao.TaskView;

import java.io.File;
import java.util.List;

public class TaskMonitoringController {
    @FXML private TableView<TaskView> taskTable;
    @FXML private TableColumn<TaskView, String> idColumn, algColumn, funcColumn, runtimeColumn, statusColumn, progressColumn;
    @FXML private TableColumn<TaskView, String> iterColumn;
    @FXML private Label lblTotal;

    @FXML
    private void initialize() {
        // ID / ALG
        idColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id));
        algColumn.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().algId)));

        // FUNC (достанем из payload, если есть "func" в json; иначе "—")
        funcColumn.setCellValueFactory(c -> new SimpleStringProperty(extractFunc(c.getValue().payload)));

        // ITER
        iterColumn.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().iter == null ? "—" : String.valueOf(c.getValue().iter)
        ));

        // RUNTIME
        runtimeColumn.setCellValueFactory(c -> new SimpleStringProperty(formatRuntime(c.getValue().runtimeMs)));

        // STATUS (бейдж)
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().status)));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String st, boolean empty) {
                super.updateItem(st, empty);
                if (empty || st == null) { setText(null); setGraphic(null); return; }
                Label badge = new Label(st);
                badge.getStyleClass().add("status-badge");
                switch (st) {
                    case "DONE"    -> badge.getStyleClass().add("status-up");
                    case "FAILED"  -> badge.getStyleClass().add("status-down");
                    case "RUNNING" -> badge.getStyleClass().add("status-warn");
                    default        -> { /* NEW/CANCELLED/etc */ }
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // PROGRESS (если payload содержит iterations.max и есть iter)
        progressColumn.setCellValueFactory(c -> new SimpleStringProperty(
                calcProgress(c.getValue().payload, c.getValue().iter)
        ));

        // авто-обновление от координатора
        AppBus.onTasksChanged(() -> Platform.runLater(this::refreshTasks));

        // счётчик всего
        if (lblTotal != null) {
            lblTotal.textProperty().bind(Bindings.size(taskTable.getItems()).asString());
        }

        refreshTasks();
    }

    @FXML
    public void refreshTasks() {
        List<TaskView> items = CoordinatorNettyServer.TASKS.listLatest(200);
        taskTable.getItems().setAll(items);
        taskTable.refresh();
    }

    @FXML
    private void onAddJsonFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите JSON с задачами");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        File f = fc.showOpenDialog(taskTable.getScene().getWindow());
        if (f == null) return;

        try {
            String txt = java.nio.file.Files.readString(f.toPath());
            com.fasterxml.jackson.databind.ObjectMapper M = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = M.readTree(txt);

            int enqueued = 0;

            // Формат 1: диапазоны
            // {
            //   "algorithms": ["sphere","rastrigin"],
            //   "iterations": {"min":100,"max":300,"step":100},
            //   "dimension":  {"min":2,"max":4,"step":1},
            //   "...": ...
            // }
            if (root.has("algorithms")) {
                var algs = M.convertValue(root.get("algorithms"), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
                var iters = expandRange(root.path("iterations"));
                var dims  = expandRange(root.path("dimension"));
                if (dims.isEmpty()) dims = java.util.List.of(2); // дефолт на всякий случай
                if (iters.isEmpty()) iters = java.util.List.of(100);

                for (String alg : algs) {
                    for (Integer iterMax : iters) {
                        for (Integer dim : dims) {
                            var payload = new java.util.LinkedHashMap<String,Object>();
                            payload.put("alg", alg);
                            payload.put("iterations", java.util.Map.of("max", iterMax));
                            payload.put("dimension", dim);
                            // Без "cmd": агент в твоём варианте может работать с внешним скриптом — оставляем пусто.
                            String id = java.util.UUID.randomUUID().toString();
                            String payloadJson = M.writeValueAsString(payload);
                            CoordinatorNettyServer.TASKS.enqueue(id, payloadJson, alg, /*runNo*/1, /*prio*/0);
                            enqueued++;
                        }
                    }
                }
            }
            // Формат 2: список готовых payload-ов
            // [ { ... }, { ... } ]
            else if (root.isArray()) {
                for (var n : root) {
                    String payloadJson = n.toString();
                    String alg = n.has("alg") ? n.get("alg").asText() : "ALG";
                    String id = java.util.UUID.randomUUID().toString();
                    CoordinatorNettyServer.TASKS.enqueue(id, payloadJson, alg, 1, 0);
                    enqueued++;
                }
            } else {
                throw new IllegalArgumentException("Неизвестный формат JSON.");
            }

            AppBus.fireTasksChanged();
            refreshTasks();
            new Alert(Alert.AlertType.INFORMATION, "Добавлено задач: " + enqueued, ButtonType.OK).showAndWait();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Ошибка загрузки: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    /** Вспомогалка: вытянуть список значений из {min,max,step} или единственного числа */
    private static java.util.List<Integer> expandRange(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) return out;

        if (node.isObject()) {
            int min  = node.path("min").asInt(0);
            int max  = node.path("max").asInt(min);
            int step = Math.max(1, node.path("step").asInt(1));
            for (int v = min; v <= max; v += step) out.add(v);
        } else if (node.isInt()) {
            out.add(node.asInt());
        }
        return out;
    }


    // ---- helpers ----

    private static String nz(String s) { return s == null || s.isBlank() ? "—" : s; }

    private static String formatRuntime(Long ms) {
        if (ms == null || ms <= 0) return "—";
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private static String extractFunc(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return "—";
        try {
            // очень лёгкий парс без зависимостей Jackson здесь: ищем `"func":"..."`
            int i = payloadJson.indexOf("\"func\"");
            if (i < 0) return "—";
            int c = payloadJson.indexOf(':', i);
            int q1 = payloadJson.indexOf('"', c);
            int q2 = payloadJson.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1) return payloadJson.substring(q1 + 1, q2);
        } catch (Exception ignore) {}
        return "—";
    }

    private static String calcProgress(String payloadJson, Integer iter) {
        if (iter == null) return "—";
        Integer max = extractIterationsMax(payloadJson);
        if (max == null || max <= 0) return iter + " it";
        int pct = (int) Math.max(0, Math.min(100, Math.round(iter * 100.0 / max)));
        return pct + "% (" + iter + "/" + max + ")";
    }

    private static Integer extractIterationsMax(String payloadJson) {
        if (payloadJson == null) return null;
        // ищем `"iterations":{"max":NUMBER}` или `"iterMax":NUMBER` — на всякий
        try {
            int p = payloadJson.indexOf("\"iterations\"");
            int blockStart = (p >= 0) ? p : payloadJson.indexOf("\"iterMax\"");
            if (blockStart < 0) return null;

            int maxKey = payloadJson.indexOf("\"max\"", blockStart);
            if (maxKey >= 0) {
                int colon = payloadJson.indexOf(':', maxKey);
                int end = colon < 0 ? -1 : findNumberEnd(payloadJson, colon + 1);
                if (end > 0) {
                    String num = payloadJson.substring(colon + 1, end).replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) return Integer.parseInt(num);
                }
            }
            // запасной вариант: "iterMax":123
            int im = payloadJson.indexOf("\"iterMax\"");
            if (im >= 0) {
                int colon = payloadJson.indexOf(':', im);
                int end = colon < 0 ? -1 : findNumberEnd(payloadJson, colon + 1);
                if (end > 0) {
                    String num = payloadJson.substring(colon + 1, end).replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) return Integer.parseInt(num);
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static int findNumberEnd(String s, int from) {
        int i = from;
        while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ':')) i++;
        while (i < s.length() && (Character.isDigit(s.charAt(i)))) i++;
        return i;
    }
}
