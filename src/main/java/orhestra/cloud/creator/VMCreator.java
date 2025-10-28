package orhestra.cloud.creator;

import com.google.protobuf.InvalidProtocolBufferException;
import orhestra.cloud.auth.AuthService;
import orhestra.cloud.config.IniLoader;
import yandex.cloud.api.compute.v1.InstanceOuterClass;
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;
import yandex.cloud.api.operation.OperationOuterClass;
import yandex.cloud.sdk.utils.OperationUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

// orhestra/cloud/creator/VMCreator.java
public class VMCreator {

    private static final long GB = 1024L * 1024 * 1024;
    private final AuthService auth;

    public VMCreator(AuthService auth) { this.auth = auth; }

    public void createMany(IniLoader.VmConfig cfg)
            throws InvalidProtocolBufferException, InterruptedException {

        for (int i = 0; i < cfg.vmCount; i++) {
            String name = cfg.vmName + "-" + UUID.randomUUID();

            String coordinatorIp = "172.27.228.4"; // IP координатора в VPN

            String cloudInit = """
            #cloud-config
            datasource:
              Ec2:
                strict_id: false
            ssh_pwauth: no
        
            users:
              - name: %s
                sudo: ALL=(ALL) NOPASSWD:ALL
                shell: /bin/bash
                ssh_authorized_keys:
                  - %s
        
            runcmd:
              # записываем адрес координатора (временно хардкод)
              - 'echo "COORDINATOR_URL=http://172.27.228.2:8081" > /etc/default/spot-agent'
        
              # создаём systemd unit для запуска агента
              - |
                cat >/etc/systemd/system/spot-agent.service <<'EOF'
                [Unit]
                Description=Orhestra Spot Agent
                After=network-online.target
                Wants=network-online.target
        
                [Service]
                User=spot
                EnvironmentFile=/etc/default/spot-agent
                WorkingDirectory=/home/spot
                ExecStart=/usr/bin/java -jar /home/spot/NettyVMServer-1.0-SNAPSHOT-jar-with-dependencies.jar ${COORDINATOR_URL}
                Restart=always
                RestartSec=5
        
                [Install]
                WantedBy=multi-user.target
                EOF
        
              - systemctl daemon-reload
              - systemctl enable --now spot-agent
            """.formatted(cfg.userName, cfg.sshKey);






            InstanceServiceOuterClass.CreateInstanceRequest req =
                    buildCreateInstanceRequest(
                            cfg.folderId, cfg.zoneId, cfg.platformId,
                            name, cfg.imageId, cfg.subnetId,
                            (cfg.securityGroupId == null || cfg.securityGroupId.isBlank())
                                    ? List.of() : List.of(cfg.securityGroupId),
                            cfg.cpu, cfg.ramGb, cfg.diskGb,
                            cfg.assignPublicIp,
                            cloudInit
                    );

            OperationOuterClass.Operation op = auth.getInstanceService().create(req);

            String instanceId = op.getMetadata()
                    .unpack(InstanceServiceOuterClass.CreateInstanceMetadata.class)
                    .getInstanceId();
            System.out.printf("[INFO] Create sent: name=%s id=%s%n", name, instanceId);

            OperationUtils.wait(auth.getOperationService(), op, Duration.ofMinutes(5));
            System.out.printf("[OK] VM created: %s (id=%s)%n", name, instanceId);
        }
    }

    // «Топорная» сборка запроса строго из конфига
    private static InstanceServiceOuterClass.CreateInstanceRequest buildCreateInstanceRequest(
            String folderId,
            String zoneId,
            String platformId,
            String name,
            String imageId,
            String subnetId,
            List<String> securityGroupIds,
            int cpu,
            int ramGb,
            int diskGb,
            boolean assignPublicIp,
            String cloudInitUserData
    ) {
        var resources = InstanceServiceOuterClass.ResourcesSpec.newBuilder()
                .setCores(cpu)
                .setMemory(ramGb * GB)
                .build();

        var disk = InstanceServiceOuterClass.AttachedDiskSpec.DiskSpec.newBuilder()
                .setImageId(imageId)
                .setSize(diskGb * GB)
                .build();

        var boot = InstanceServiceOuterClass.AttachedDiskSpec.newBuilder()
                .setAutoDelete(true)
                .setDiskSpec(disk)
                .build();

        var nic = InstanceServiceOuterClass.NetworkInterfaceSpec.newBuilder()
                .setSubnetId(subnetId);

        if (securityGroupIds != null && !securityGroupIds.isEmpty()) {
            nic.addAllSecurityGroupIds(securityGroupIds);
        }

        var addr = InstanceServiceOuterClass.PrimaryAddressSpec.newBuilder();
        if (assignPublicIp) {
            addr.setOneToOneNatSpec(
                    InstanceServiceOuterClass.OneToOneNatSpec.newBuilder()
                            .setIpVersion(InstanceOuterClass.IpVersion.IPV4)
                            .build()
            );
        }
        nic.setPrimaryV4AddressSpec(addr.build());

        return InstanceServiceOuterClass.CreateInstanceRequest.newBuilder()
                .setFolderId(folderId)
                .setName(name)
                .setZoneId(zoneId)
                .setPlatformId(platformId)
                .setResourcesSpec(resources)
                .setBootDiskSpec(boot)
                .addNetworkInterfaceSpecs(nic)
                .putMetadata("user-data", cloudInitUserData)
                .setSchedulingPolicy(
                        InstanceOuterClass.SchedulingPolicy.newBuilder()
                                .setPreemptible(true)
                                .build()
                )
                .build();
    }
}


