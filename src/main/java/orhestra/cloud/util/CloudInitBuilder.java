package orhestra.cloud.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CloudInitBuilder {

    private CloudInitBuilder() {}

    public static String readPublicKey(String path) {
        try {
            return Files.readString(Path.of(path)).trim();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read SSH public key: " + path, e);
        }
    }

    public static String buildUserData(String user, String sshPublicKey, String coordinatorUrl) {
        // Очень простой cloud-init
        StringBuilder sb = new StringBuilder();
        sb.append("#cloud-config\n");
        sb.append("users:\n");
        sb.append("  - name: ").append(user).append("\n");
        sb.append("    sudo: ALL=(ALL) NOPASSWD:ALL\n");
        sb.append("    groups: sudo\n");
        sb.append("    shell: /bin/bash\n");
        sb.append("    ssh_authorized_keys:\n");
        sb.append("      - ").append(sshPublicKey).append("\n");

        if (coordinatorUrl != null && !coordinatorUrl.isBlank()) {
            sb.append("write_files:\n");
            sb.append("  - path: /etc/orhestra/coordinator.url\n");
            sb.append("    content: |\n");
            sb.append("      ").append(coordinatorUrl).append("\n");
        }
        return sb.toString();
    }
}


