package orhestra.cloud.manager;

import orhestra.cloud.auth.AuthService;
import yandex.cloud.api.compute.v1.ImageServiceOuterClass;
import yandex.cloud.api.compute.v1.InstanceOuterClass;
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;

public class VMManager {

    private final AuthService auth;

    public VMManager(AuthService auth) {
        this.auth = auth;
    }

    public InstanceServiceOuterClass.GetInstanceRequest getRequest(String id) {
        return InstanceServiceOuterClass.GetInstanceRequest.newBuilder()
                .setInstanceId(id)
                .build();
    }

    public InstanceOuterClass.Instance getInstance(String id) {
        return auth.getInstanceService().get(getRequest(id));
    }

    public String getPublicIp(String id) {
        var ni = getInstance(id).getNetworkInterfaces(0).getPrimaryV4Address();
        return ni.hasOneToOneNat() ? ni.getOneToOneNat().getAddress() : "";
    }

    public String getPrivateIp(String id) {
        return getInstance(id).getNetworkInterfaces(0).getPrimaryV4Address().getAddress();
    }

    public void monitorUntilRunning(String id) {
        String status = "";
        while (!"RUNNING".equals(status)) {
            try {
                var inst = getInstance(id);
                status = inst.getStatus().toString();
                System.out.printf("[INFO] VM ID: %s, Name: %s, Status: %s%n",
                        inst.getId(), inst.getName(), status);
                Thread.sleep(1500);
            } catch (Exception e) {
                System.out.println("[ERROR] monitor: " + e.getMessage());
            }
        }
    }

}
