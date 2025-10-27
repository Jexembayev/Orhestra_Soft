package orhestra.coordinator.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import orhestra.coordinator.core.AppBus;
import orhestra.coordinator.server.CoordinatorNettyServer;
import orhestra.coordinator.store.dao.TaskDao.TaskView;

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
        // твоя текущая реализация загрузки JSON → enqueue(...) — оставляем
        // (если ещё нет — напишем отдельным шагом генератор тасков из JSON-диапазонов)
        new Alert(Alert.AlertType.INFORMATION, "Загрузка JSON пока не реализована здесь.", ButtonType.OK).showAndWait();
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
