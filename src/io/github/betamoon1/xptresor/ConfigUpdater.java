package io.github.betamoon1.xptresor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Adds the settings of newer versions to an existing config.yml (with their comments), once per
 * config version. Values the server owner already has are never touched.
 */
final class ConfigUpdater {

    static final int VERSION = 2;

    /** Settings added in config version 2 (XpTresor 1.2.0). */
    private static final List<String> ADDED_IN_2 = List.of(
            "menu", "auto-pay", "history", "vault-block", "sounds.menu-click", "sounds.auto-pay");

    private ConfigUpdater() {
    }

    static void update(XpTresor plugin) {
        FileConfiguration config = plugin.getConfig();
        // only the server's file counts - the defaults from the jar always have the newest version
        int version = config.contains("config-version", true) ? config.getInt("config-version") : 1;
        if (version >= VERSION) return;
        YamlConfiguration defaults;
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        int added = 0;
        if (version < 2) {
            for (String path : ADDED_IN_2) added += copy(defaults, config, path);
        }
        config.set("config-version", VERSION);
        config.setComments("config-version", defaults.getComments("config-version"));
        plugin.saveConfig();
        plugin.getLogger().info("config.yml updated to version " + VERSION + " (" + added + " new settings added)");
    }

    /** Copies one setting or a whole section if the server's file doesn't have it yet. */
    private static int copy(YamlConfiguration from, FileConfiguration to, String path) {
        if (to.contains(path, true) || !from.contains(path)) return 0;
        int n = 0;
        if (from.isConfigurationSection(path)) {
            ConfigurationSection section = from.getConfigurationSection(path);
            for (String key : section.getKeys(true)) {
                String full = path + "." + key;
                if (!from.isConfigurationSection(full)) {
                    to.set(full, from.get(full));
                    n++;
                }
                List<String> comments = from.getComments(full);
                if (!comments.isEmpty()) to.setComments(full, comments);
            }
        } else {
            to.set(path, from.get(path));
            n++;
        }
        to.setComments(path, from.getComments(path));
        return n;
    }
}
