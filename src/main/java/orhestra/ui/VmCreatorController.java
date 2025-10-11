// src/main/java/orhestra/ui/VmCreatorController.java
package orhestra.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import orhestra.ui.util.IniConfig;
import orhestra.ui.util.IniParser;

import java.io.File;

public class VmCreatorController {
    @FXML private Button createVmButton, deleteAllButton;
    @FXML private TextArea logArea;
    @FXML private Label vmCountLabel, vmNameLabel, imageIdLabel, cpuLabel, ramLabel, diskLabel, userLabel, keyPathLabel;

    private IniConfig cfg;

    @FXML
    private void handleLoadIniFile() {
        var chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("INI files", "*.ini"));
        File f = chooser.showOpenDialog(null);
        if (f == null) return;

        try {
            cfg = IniParser.parse(f.toPath());
            fillPreview(cfg);
            createVmButton.setDisable(false);
            append("INI загружен: " + f.getAbsolutePath());
        } catch (Exception e) {
            append("Ошибка парсинга INI: " + e.getMessage());
        }
    }

    @FXML
    private void handleCreateVm() {
        if (cfg == null) { append("Конфиг не загружен"); return; }
        append("▶ Создание " + cfg.vmCount + " SPOT-ВМ (stub)...");
        // TODO: вызвать yc API / SDK
    }

    @FXML
    private void handleDeleteAllVms() {
        append("🗑 Удаление всех ВМ (stub)...");
        // TODO: вызвать yc API / SDK
    }

    private void fillPreview(IniConfig c) {
        vmCountLabel.setText("vm_count: " + c.vmCount);
        vmNameLabel.setText("vm_name_prefix: " + nvl(c.vmNamePrefix));
        imageIdLabel.setText("image_id: " + nvl(c.imageId));
        cpuLabel.setText("cpu: " + c.cpu);
        ramLabel.setText("ram_gb: " + c.ramGb);
        diskLabel.setText("disk_gb: " + c.diskGb);
        userLabel.setText("user: " + nvl(c.user));
        keyPathLabel.setText("ssh_key: " + nvl(c.sshKeyPath));
    }

    private void append(String msg) { logArea.appendText(msg + "\n"); }
    private static String nvl(String s){ return s==null? "—" : s; }
}
