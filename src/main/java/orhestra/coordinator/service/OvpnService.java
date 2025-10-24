package orhestra.coordinator.service;

import orhestra.cloud.auth.AuthService;
import orhestra.cloud.config.CloudConfig;
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;
import yandex.cloud.api.operation.OperationOuterClass;
import yandex.cloud.sdk.utils.OperationUtils;

import java.time.Duration;

/**
 * Простой менеджер VM с OpenVPN Access Server.
 * Источник идентификации:
 *  - либо поле [OVPN] instance_id в INI (рекомендуется),
 *  - либо имя/метка из CloudConfig (например imageId/namePrefix) — допиши под себя.
 */
public class OvpnService {
    private final AuthService auth;
    private final CloudConfig cfg;

    public OvpnService(AuthService auth, CloudConfig cfg) {
        this.auth = auth;
        this.cfg = cfg;
    }

    private String resolveOvpnInstanceId() {
        // 1) если ты добавишь в CloudConfig поле ovpnInstanceId — бери его отсюда
        if (cfg.ovpnInstanceId != null && !cfg.ovpnInstanceId.isBlank()) return cfg.ovpnInstanceId;

        // 2) иначе можно искать по имени/метке — тут оставлю TODO:
        // TODO: пролистать instances.list(folderId) и найти по имени "openvpn-as" и вернуть id
        throw new IllegalStateException("OVPN instance id is not configured");
    }

    public boolean start() {
        try {
            String id = resolveOvpnInstanceId();
            OperationOuterClass.Operation op = auth.getInstanceService()
                    .start(InstanceServiceOuterClass.StartInstanceRequest.newBuilder()
                            .setInstanceId(id).build());
            OperationUtils.wait(auth.getOperationService(), op, Duration.ofMinutes(3));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean stop() {
        try {
            String id = resolveOvpnInstanceId();
            OperationOuterClass.Operation op = auth.getInstanceService()
                    .stop(InstanceServiceOuterClass.StopInstanceRequest.newBuilder()
                            .setInstanceId(id).build());
            OperationUtils.wait(auth.getOperationService(), op, Duration.ofMinutes(3));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
