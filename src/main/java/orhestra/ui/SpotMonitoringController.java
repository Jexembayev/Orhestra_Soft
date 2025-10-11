// src/main/java/orhestra/ui/SpotMonitoringController.java
package orhestra.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import orhestra.ui.model.SpotRow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SpotMonitoringController {
    @FXML private Label coordinatorStatusLabel;
    @FXML private TableView<SpotRow> spotTable;
    @FXML private TableColumn<SpotRow, String> idColumn, ipColumn, lastSeenColumn, statusColumn;
    @FXML private TableColumn<SpotRow, Number> cpuColumn, activeColumn, coresColumn;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(c -> c.getValue().idProperty());
        ipColumn.setCellValueFactory(c -> c.getValue().ipProperty());
        cpuColumn.setCellValueFactory(c -> c.getValue().cpuProperty());
        activeColumn.setCellValueFactory(c -> c.getValue().activeTasksProperty());
        coresColumn.setCellValueFactory(c -> c.getValue().coresProperty());
        lastSeenColumn.setCellValueFactory(c -> c.getValue().lastSeenProperty());
        statusColumn.setCellValueFactory(c -> c.getValue().statusProperty());

        coordinatorStatusLabel.setText("✅ Координатор доступен");
        fillMock();
    }

    @FXML
    private void refreshSpots() {
        // TODO: звать REST координатора
        fillMock();
    }

    private void fillMock() {
        var now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        spotTable.setItems(FXCollections.observableArrayList(
                SpotRow.of("spot-001", "10.130.0.21", 23, 0, 4, now, "Ready"),
                SpotRow.of("spot-002", "10.130.0.22", 88, 2, 4, now, "Busy"),
                SpotRow.of("spot-003", "10.130.0.23",  6, 0, 8, now, "Ready")
        ));
    }
}

