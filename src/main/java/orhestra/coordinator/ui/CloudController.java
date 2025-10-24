package orhestra.coordinator.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import orhestra.cloud.auth.AuthService;
import orhestra.cloud.config.CloudConfig;
import orhestra.cloud.config.IniLoader;
import orhestra.cloud.creator.VMCreator;              // <-- используем VMCreator
import orhestra.coordinator.model.CloudInstanceRow;
import orhestra.coordinator.model.EnvState;
import orhestra.coordinator.service.CloudProbe;
import orhestra.coordinator.service.CoordinatorService;
import orhestra.coordinator.service.OvpnService;
import orhestra.coordinator.service.VpnProbe;
import yandex.cloud.api.compute.v1.InstanceOuterClass;
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;
import yandex.cloud.sdk.utils.OperationUtils;
import yandex.cloud.api.operation.OperationOuterClass;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CloudController {

    // -------- FXML --------
    @FXML private TextField iniPathField;
    @FXML private Label statusLabel;

    @FXML private Label dotVpn, dotCloud, dotOvpn, dotCoord;
    @FXML private Button btnOvpnOn, btnOvpnOff;
    @FXML private Button btnCheckVpn;
    @FXML private Button btnCheckCloud;
    @FXML private Button btnStartCoordinator;
    @FXML private Button btnStopCoordinator;
    @FXML private Button btnCreateSpot;
    @FXML private TextArea coordLogArea;

    @FXML private TableView<CloudInstanceRow> instancesTable;
    @FXML private TableColumn<CloudInstanceRow, String> colId;
    @FXML private TableColumn<CloudInstanceRow, String> colName;
    @FXML private TableColumn<CloudInstanceRow, String> colZone;
    @FXML private TableColumn<CloudInstanceRow, String> colStatus;
    @FXML private TableColumn<CloudInstanceRow, String> colIP;
    @FXML private TableColumn<CloudInstanceRow, String> colCreated;
    @FXML private TableColumn<CloudInstanceRow, String> colPreempt;

    // -------- Model/State --------
    private final ObservableList<CloudInstanceRow> data = FXCollections.observableArrayList();
    private final EnvState env = new EnvState();

    private AuthService auth;       // лениво: токен из ENV OAUTH_TOKEN
    private CloudConfig cfg;        // то, что считали из INI

    // services
    private final VpnProbe vpnProbe = new VpnProbe();
    private final CoordinatorService coordSvc = new CoordinatorService();
    private CloudProbe cloudProbe;    // зависит от auth
    private OvpnService ovpnSvc;      // зависит от auth + cfg

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    @FXML private void handleClearCoordLog() {
        if (coordLogArea != null) coordLogArea.clear();
    }

    // =====================================================================
    @FXML
    private void initialize() {
        // биндинги колонок
        colId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        colZone.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("zone"));
        colStatus.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
        colIP.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("ip"));
        colCreated.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("createdAt"));
        colPreempt.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("preemptible"));
        instancesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        instancesTable.setItems(data);

        // реакция на смену статусов: цветные точки и доступность кнопок
        env.vpnProperty().addListener((o, a, b) -> colorize(dotVpn, b));
        env.cloudProperty().addListener((o, a, b) -> colorize(dotCloud, b));
        env.ovpnProperty().addListener((o, a, b) -> colorize(dotOvpn, b));
        env.coordProperty().addListener((o, a, b) -> colorize(dotCoord, b));
        env.anyProperty().addListener((o, a, b) -> updateButtons());

        // лог координатора в TextArea
        coordSvc.setLogSink(s ->
                javafx.application.Platform.runLater(() -> {
                    if (coordLogArea != null) coordLogArea.appendText(s);
                })
        );

        setStatus("Load INI, OAuth from ENV: OAUTH_TOKEN");
        updateButtons();
    }

    // ==================== INI ====================

    @FXML
    private void handleChooseIni() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите INI-файл");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("INI files", "*.ini"));
        File f = fc.showOpenDialog(instancesTable.getScene().getWindow());
        if (f != null) iniPathField.setText(f.getAbsolutePath());
    }

    @FXML
    private void handleLoadIni() {
        try {
            var path = required(iniPathField.getText(), "INI path");
            var opt = IniLoader.load(new File(path));
            if (opt.isEmpty()) {
                setStatus("INI parse error");
                return;
            }
            cfg = opt.get();
            setStatus("INI loaded ✓");

            // лениво создаём auth и сервисы, зависящие от него
            ensureAuth();
            this.cloudProbe = new CloudProbe(auth);
            this.ovpnSvc = new OvpnService(auth, cfg);

            // сразу проверим доступность облака (тихая проверка)
            env.setCloud(cloudProbe.quickPing(cfg.folderId));
            updateButtons();
        } catch (Exception e) {
            setStatus("INI load error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== ПРОВЕРКИ ====================

    @FXML
    private void handleCheckVpn() {
        boolean ok = vpnProbe.isVpnUp();
        env.setVpn(ok);
        setStatus(ok ? "✅ VPN OK" : "⚠️ VPN не обнаружен");
    }

    @FXML
    private void handleCheckCloud() {
        try {
            ensureAuth();
            ensureCfg();
            boolean ok = cloudProbe.quickPing(cfg.folderId);
            env.setCloud(ok);
            setStatus(ok ? "✅ Облако доступно" : "❌ Облако недоступно");
        } catch (Exception e) {
            env.setCloud(false);
            setStatus("❌ Облако недоступно: " + e.getMessage());
        }
    }

    // OpenVPN Access Server
    @FXML private void handleOvpnOn()  { callOvpn(true); }
    @FXML private void handleOvpnOff() { callOvpn(false); }

    private void callOvpn(boolean on) {
        try {
            ensureAuth();
            ensureCfg();

            // Если ovpnInstanceId отсутствует — предупреждаем
            if (cfg.ovpnInstanceId == null || cfg.ovpnInstanceId.isBlank()) {
                setStatus("⚠️ В INI не указан [OVPN] instance_id — запуск невозможен");
                env.setOvpn(false);
                return;
            }

            boolean ok = on ? ovpnSvc.start() : ovpnSvc.stop();
            env.setOvpn(ok);

            if (ok) {
                setStatus(on ? "✅ OpenVPN Access Server запущен" : "✅ OpenVPN AS остановлен");
            } else {
                setStatus(on ? "❌ Не удалось запустить OpenVPN AS" : "❌ Не удалось остановить OpenVPN AS");
            }
        } catch (Exception e) {
            env.setOvpn(false);
            setStatus("OVPN ошибка: " + e.getMessage());
        }
    }

    @FXML
    private void handleStartCoordinator() {
        boolean ok = coordSvc.start(8081);
        env.setCoord(ok);
        setStatus(ok ? "✅ Координатор запущен на 8081" : "❌ Координатор не запустился");
    }

    @FXML
    private void handleStopCoordinator() {
        coordSvc.stop();
        env.setCoord(false);
        setStatus("Координатор остановлен");
    }

    // ==================== ОБЛАКО ====================

    @FXML
    private void handleListInstances() {
        try {
            ensureAuth();
            ensureCfg();
            var listReq = InstanceServiceOuterClass.ListInstancesRequest.newBuilder()
                    .setFolderId(cfg.folderId)
                    .build();
            var resp = auth.getInstanceService().list(listReq);
            List<InstanceOuterClass.Instance> items = resp.getInstancesList();
            data.setAll(items.stream().map(this::toRow).toList());
            setStatus("Instances: " + items.size());
        } catch (Exception e) {
            setStatus("List error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCreateSpot() {
        runAsync("Создание SPOT-инстансов…", () -> {
            try {
                ensureAuth();
                ensureCfg();

                var iniFile = new File(required(iniPathField.getText(), "INI path"));
                var vmCfgOpt = IniLoader.loadVmConfig(iniFile);
                if (vmCfgOpt.isEmpty()) {
                    updateStatusAsync("❌ INI (VmConfig) некорректен — проверь секции [AUTH]/[NETWORK]/[VM]/[SSH]");
                    return;
                }

                var creator = new VMCreator(auth);
                creator.createMany(vmCfgOpt.get());  // внутри ждёт OperationUtils.wait(...)

                updateStatusAsync("✅ SPOT-инстансы созданы");
                javafx.application.Platform.runLater(this::handleListInstances);
            } catch (Exception e) {
                updateStatusAsync("❌ Ошибка создания: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @FXML
    private void handleDeleteSelected() {
        var selected = instancesTable.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            setStatus("Nothing selected");
            return;
        }
        try {
            ensureAuth();
            for (CloudInstanceRow row : selected) {
                var delReq = InstanceServiceOuterClass.DeleteInstanceRequest.newBuilder()
                        .setInstanceId(row.getId()).build();
                OperationOuterClass.Operation op = auth.getInstanceService().delete(delReq);
                OperationUtils.wait(auth.getOperationService(), op, java.time.Duration.ofMinutes(5));
            }
            handleListInstances();
            setStatus("Delete: done");
        } catch (Exception e) {
            setStatus("Delete error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== HELPERS ====================

    private void ensureAuth() {
        if (auth == null) auth = new AuthService(); // возьмёт токен из ENV: OAUTH_TOKEN
    }

    private void ensureCfg() {
        if (cfg == null) throw new IllegalStateException("INI not loaded");
    }

    private CloudInstanceRow toRow(InstanceOuterClass.Instance inst) {
        String id = inst.getId();
        String name = inst.getName();
        String zone = inst.getZoneId();
        String status = inst.getStatus().name();
        String ip = inst.getNetworkInterfacesCount() > 0
                ? inst.getNetworkInterfaces(0).getPrimaryV4Address().getAddress()
                : "";
        String created = inst.hasCreatedAt()
                ? TS.format(Instant.ofEpochSecond(inst.getCreatedAt().getSeconds()))
                : "";
        boolean spot = inst.hasSchedulingPolicy() && inst.getSchedulingPolicy().getPreemptible();
        return CloudInstanceRow.of(id, name, zone, status, ip, created, spot);
    }

    private void setStatus(String s) { statusLabel.setText(s); }

    private void updateButtons() {
        boolean iniOk = (cfg != null);
        // Создавать SPOT можно без VPN/координатора — лишь бы INI загружен и облако OK
        boolean enableCreate = iniOk && env.isCloud();
        if (btnCreateSpot != null) btnCreateSpot.setDisable(!enableCreate);

        // Кнопки координатора
        if (btnStopCoordinator != null) btnStopCoordinator.setDisable(!env.isCoord());
        if (btnStartCoordinator != null) btnStartCoordinator.setDisable(env.isCoord());
    }

    private static void colorize(Label dot, boolean ok) {
        if (dot == null) return;
        dot.setStyle("-fx-text-fill: " + (ok ? "#11aa11" : "#999999") + "; -fx-font-size: 16;");
    }

    private static String required(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing: " + name);
        return v;
    }

    /** Базовый helper для фоновых задач с обновлением статуса и блокировкой кнопки Create SPOT. */
    private void runAsync(String startMsg, Runnable job) {
        updateStatusAsync(startMsg);
        if (btnCreateSpot != null) btnCreateSpot.setDisable(true);
        new Thread(() -> {
            try {
                job.run();
            } finally {
                javafx.application.Platform.runLater(this::updateButtons);
            }
        }, "cloud-ui-worker").start();
    }

    private void updateStatusAsync(String msg) {
        javafx.application.Platform.runLater(() -> setStatus(msg));
    }
}

