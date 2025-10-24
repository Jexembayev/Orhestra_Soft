package orhestra.coordinator.ui;

import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class MainController {

    @FXML private TabPane tabPane;
    @FXML private Region spacer;   // тот самый <Region fx:id="spacer"/> из FXML

    @FXML
    public void initialize() {
        // делаем spacer «растяжимым», чтобы увести правую часть тулбара вправо
        HBox.setHgrow(spacer, Priority.ALWAYS);
    }

    @FXML
    private void handleRefresh() {
        // глобальный refresh — позже дернём сервисы
        System.out.println("Refresh clicked");
    }
}

