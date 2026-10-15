package io.github.betamoon1.xptresor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Messages from plugins/XpTresor/lang/messages_&lt;code&gt;.yml.
 *
 * Every file in that folder is a language - server owners can add their own (e.g. messages_cs.yml).
 * Missing keys fall back to the built-in file of that language, then to English.
 * With per-player-language every player gets the language of their Minecraft client, if it exists.
 * Colors: &amp;a, &amp;l ... and &amp;#rrggbb.
 */
public final class Messages {

    /** Languages shipped inside the jar. */
    public static final List<String> BUILT_IN = List.of("en", "de", "es", "fr", "it", "nl", "pl", "pt", "ru", "tr");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private final Map<String, YamlConfiguration> files = new HashMap<>();
    private final Map<String, YamlConfiguration> builtIn = new HashMap<>();
    private final String defaultLanguage;
    private final boolean perPlayer;

    Messages(XpTresor plugin, String language, boolean perPlayer) {
        this.perPlayer = perPlayer;
        File folder = new File(plugin.getDataFolder(), "lang");
        for (String lang : BUILT_IN) {
            String path = "lang/messages_" + lang + ".yml";
            if (!new File(plugin.getDataFolder(), path).exists()) plugin.saveResource(path, false);
            builtIn.put(lang, load(plugin, path));
        }
        File[] list = folder.listFiles((d, n) -> n.startsWith("messages_") && n.endsWith(".yml"));
        if (list != null) {
            for (File f : list) {
                String code = f.getName().substring("messages_".length(), f.getName().length() - 4).toLowerCase(Locale.ROOT);
                files.put(code, YamlConfiguration.loadConfiguration(f));
            }
        }
        String lang = normalize(language);
        if (!files.containsKey(lang)) {
            plugin.getLogger().warning("lang/messages_" + language + ".yml not found - using English. Available: "
                    + new TreeSet<>(files.keySet()));
            lang = "en";
        }
        this.defaultLanguage = lang;
    }

    private static YamlConfiguration load(XpTresor plugin, String path) {
        YamlConfiguration y = new YamlConfiguration();
        try (InputStream in = plugin.getResource(path)) {
            if (in != null) y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // no built-in file for that language
        }
        return y;
    }

    /** "pt_BR", "pt-br", "PT" -> "pt"; keeps full codes that exist as file (e.g. "zh_tw"). */
    private String normalize(String code) {
        if (code == null) return "en";
        String c = code.toLowerCase(Locale.ROOT).replace('-', '_').trim();
        if (files.containsKey(c)) return c;
        int sep = c.indexOf('_');
        return sep > 0 ? c.substring(0, sep) : c;
    }

    /** Language used for this receiver. */
    public String languageOf(CommandSender who) {
        if (perPlayer && who instanceof Player player) {
            Locale locale = player.locale();
            String full = (locale.getLanguage() + "_" + locale.getCountry()).toLowerCase(Locale.ROOT);
            if (files.containsKey(full)) return full;
            if (files.containsKey(locale.getLanguage())) return locale.getLanguage();
        }
        return defaultLanguage;
    }

    public String defaultLanguage() {
        return defaultLanguage;
    }

    public java.util.Set<String> available() {
        return new TreeSet<>(files.keySet());
    }

    private String lookup(String lang, String key) {
        YamlConfiguration f = files.get(lang);
        String text = f == null ? null : f.getString(key);
        if (text == null && builtIn.containsKey(lang)) text = builtIn.get(lang).getString(key);
        if (text == null) text = files.containsKey("en") ? files.get("en").getString(key) : null;
        if (text == null) text = builtIn.get("en").getString(key, key);
        return text;
    }

    private static String fill(String text, Object... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return text;
    }

    /** Text in the default language (items, placeholders, console). */
    public String raw(String key, Object... placeholders) {
        return fill(lookup(defaultLanguage, key), placeholders);
    }

    /** Text in the language of this receiver. */
    public String raw(CommandSender who, String key, Object... placeholders) {
        return fill(lookup(languageOf(who), key), placeholders);
    }

    public Component component(String key, Object... placeholders) {
        return LEGACY.deserialize(raw(key, placeholders));
    }

    public static Component colored(String text) {
        return LEGACY.deserialize(text);
    }

    /** Sends a message with the prefix. Empty messages are not sent (lets admins switch messages off). */
    public void send(CommandSender to, String key, Object... placeholders) {
        String lang = languageOf(to);
        String text = fill(lookup(lang, key), placeholders);
        if (text.isEmpty()) return;
        to.sendMessage(LEGACY.deserialize(lookup(lang, "prefix") + text));
    }

    /** Sends without prefix (lists, headers). */
    public void plain(CommandSender to, String key, Object... placeholders) {
        String text = raw(to, key, placeholders);
        if (text.isEmpty()) return;
        to.sendMessage(LEGACY.deserialize(text));
    }

    public void actionBar(Player to, String key, Object... placeholders) {
        String text = raw(to, key, placeholders);
        if (text.isEmpty()) return;
        to.sendActionBar(LEGACY.deserialize(text));
    }
}
