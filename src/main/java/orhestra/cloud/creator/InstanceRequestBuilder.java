package orhestra.cloud.creator;

import orhestra.cloud.config.IniLoader;
import yandex.cloud.api.compute.v1.InstanceOuterClass;          // SchedulingPolicy здесь
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;   // Requests/Specs здесь

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

public class InstanceRequestBuilder {

    public static InstanceServiceOuterClass.CreateInstanceRequest build(IniLoader.VmConfig cfg) throws IOException {
        String name   = cfg.vmName + "-" + UUID.randomUUID();
        String sshKey = Files.readString(Paths.get(cfg.sshKey)).trim();

        String cloudInit = """
            #cloud-config
            users:
              - name: %s
                ssh-authorized-keys:
                  - %s
            """.formatted(cfg.userName, sshKey);

        // Resources (RAM в байтах)
        InstanceServiceOuterClass.ResourcesSpec resources =
                InstanceServiceOuterClass.ResourcesSpec.newBuilder()
                        .setCores(cfg.cpu)
                        .setMemory(cfg.ramGb * 1024L * 1024 * 1024)   // GB -> bytes
                        .build();

        // Boot disk
        InstanceServiceOuterClass.AttachedDiskSpec.DiskSpec diskSpec =
                InstanceServiceOuterClass.AttachedDiskSpec.DiskSpec.newBuilder()
                        .setImageId(cfg.imageId)
                        .setSize(cfg.diskGb * 1024L * 1024 * 1024)    // GB -> bytes
                        .build();

        InstanceServiceOuterClass.AttachedDiskSpec bootDisk =
                InstanceServiceOuterClass.AttachedDiskSpec.newBuilder()
                        .setAutoDelete(true)
                        .setDiskSpec(diskSpec)
                        .build();

        // NIC + Security Group + private IP (без public IP/NAT)
        InstanceServiceOuterClass.NetworkInterfaceSpec nic =
                InstanceServiceOuterClass.NetworkInterfaceSpec.newBuilder()
                        .setSubnetId(cfg.subnetId)
                        .addSecurityGroupIds(cfg.securityGroupId)
                        .setPrimaryV4AddressSpec(
                                InstanceServiceOuterClass.PrimaryAddressSpec.newBuilder()
                                        .build()                     // пусто -> приватный IP выдастся автоматически
                        )
                        .build();

        // Spot (preemptible)
        InstanceOuterClass.SchedulingPolicy spotPolicy =
                InstanceOuterClass.SchedulingPolicy.newBuilder()
                        .setPreemptible(true)
                        .build();

        return InstanceServiceOuterClass.CreateInstanceRequest.newBuilder()
                .setFolderId(cfg.folderId)
                .setName(name)
                .setZoneId(cfg.zoneId)
                .setPlatformId(cfg.platformId)
                .setResourcesSpec(resources)
                .setBootDiskSpec(bootDisk)
                .addNetworkInterfaceSpecs(nic)
                .setSchedulingPolicy(spotPolicy)
                .putMetadata("user-data", cloudInit)
                .build();
    }
}


