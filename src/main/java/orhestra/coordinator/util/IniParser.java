// src/main/java/orhestra/ui/util/IniParser.java
package orhestra.coordinator.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class IniParser {
    public static IniConfig parse(Path iniPath) throws IOException {
        var cfg = new IniConfig();
        for (var line : Files.readAllLines(iniPath)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
            var kv = line.split("=", 2);
            var k = kv[0].trim().toLowerCase();
            var v = kv[1].trim();

            switch (k) {
                case "vm_count" -> cfg.vmCount = toInt(v, 1);
                case "vm_name_prefix" -> cfg.vmNamePrefix = v;
                case "image_id" -> cfg.imageId = v;
                case "cpu" -> cfg.cpu = toInt(v, 2);
                case "ram_gb" -> cfg.ramGb = toInt(v, 4);
                case "disk_gb" -> cfg.diskGb = toInt(v, 20);
                case "user" -> cfg.user = v;
                case "ssh_key", "ssh_key_path" -> cfg.sshKeyPath = v;
                // доп. поля добавим позже
            }
        }
        return cfg;
    }

    private static int toInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
