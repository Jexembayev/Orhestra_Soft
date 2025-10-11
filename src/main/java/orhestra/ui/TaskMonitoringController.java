// src/main/java/orhestra/ui/TaskMonitoringController.java
package orhestra.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import orhestra.ui.model.TaskRow;

public class TaskMonitoringController {
    @FXML private TableView<TaskRow> taskTable;
    @FXML private TableColumn<TaskRow, String> idColumn, algColumn, funcColumn, runtimeColumn, statusColumn, progressColumn;
    @FXML private TableColumn<TaskRow, Number> iterColumn;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(c -> c.getValue().idProperty());
        algColumn.setCellValueFactory(c -> c.getValue().algProperty());
        funcColumn.setCellValueFactory(c -> c.getValue().funcProperty());
        iterColumn.setCellValueFactory(c -> c.getValue().iterProperty());
        runtimeColumn.setCellValueFactory(c -> c.getValue().runtimeProperty());
        statusColumn.setCellValueFactory(c -> c.getValue().statusProperty());
        progressColumn.setCellValueFactory(c -> c.getValue().progressProperty());

        refreshTasks();
    }

    @FXML
    private void refreshTasks() {
        taskTable.setItems(FXCollections.observableArrayList(
                TaskRow.of("t-101","COA","f1",230,"12.3s","RUNNING","46%"),
                TaskRow.of("t-102","COA","f2",  0,"—","QUEUED","0%"),
                TaskRow.of("t-103","GA", "f5",1000,"7.2s","DONE","100%")
        ));
    }
}

