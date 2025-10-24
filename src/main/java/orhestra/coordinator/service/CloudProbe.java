package orhestra.coordinator.service;

import orhestra.cloud.auth.AuthService;
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;

public class CloudProbe {
    private final AuthService auth;

    public CloudProbe(AuthService auth) { this.auth = auth; }

    /** Самая быстрая проверка — любой успешный list в папке. */
    public boolean quickPing(String folderId) {
        try {
            var req = InstanceServiceOuterClass.ListInstancesRequest.newBuilder()
                    .setFolderId(folderId).setPageSize(1).build();
            auth.getInstanceService().list(req); // если нет исключения — доступ есть
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

