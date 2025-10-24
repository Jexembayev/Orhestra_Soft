package orhestra.coordinator.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import orhestra.coordinator.model.SpotRow;
import orhestra.coordinator.server.CoordinatorNettyServer;

import java.time.Instant;
import java.util.List;

public class SpotMonitoringController {

    @FXML private TableView<SpotRow> table;
    @FXML private TableColumn<SpotRow, Number> colId;
    @FXML private TableColumn<SpotRow, String> colAddr;
    @FXML private TableColumn<SpotRow, Number> colCPU;
    @FXML private TableColumn<SpotRow, Number> colTasks;
    @FXML private TableColumn<SpotRow, String>  colBeat;
    @FXML private TableColumn<SpotRow, String>  colStatus;
    @FXML private Label lblTotal;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(   d -> d.getValue().idProperty());
        colAddr.setCellValueFactory( d -> d.getValue().addrProperty());
        colCPU.setCellValueFactory(  d -> d.getValue().cpuProperty());
        colTasks.setCellValueFactory(d -> d.getValue().tasksProperty());

        colBeat.setCellValueFactory(d -> {
            long now = Instant.now().getEpochSecond();
            long age = Math.max(0, now - d.getValue().getLastBeatEpoch());
            return new SimpleStringProperty(String.valueOf(age));
        });

        colStatus.setCellValueFactory(d -> d.getValue().statusProperty());

        handleRefresh(); // первичное заполнение
    }

    @FXML
    private void handleRefresh() {
        List<SpotRow> rows = CoordinatorNettyServer.REGISTRY.snapshot();
        table.getItems().setAll(rows);
        lblTotal.setText(String.valueOf(rows.size()));
        table.refresh();
    }
}


