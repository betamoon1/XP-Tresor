package io.github.betamoon1.xptresor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** config.yml, read once per (re)load. */
public final class Settings {

    public enum SoundType { DEPOSIT, WITHDRAW, SEND, RECEIVE, AUTO_DEPOSIT, REPAIR, BOTTLE }

    public final String language;
    public final boolean perPlayerLanguage;
    public final int startLevels;
    public final int minDepositLevels;
    public final int defaultLimitLevels;          // -1 = unlimited
    public final Map<String, Integer> rankLimits;  // rank -> levels
    public final int autoMinLevel;
    public final boolean keepXpOnDeath;
    public final boolean sendToOffline;
    public final boolean repairEnabled;
    public final boolean repairRequireMending;
    public final int repairDurabilityPerXp;
    public final boolean bottleEnabled;
    public final int bottleMaxAmount;
    public final boolean soundsEnabled;
    public final Map<SoundType, String> sounds = new EnumMap<>(SoundType.class);
    public final boolean vault;
    public final boolean vaultInPoints;
    public final int topSize;
    public final Set<String> hiddenInTop = new HashSet<>();
    public final boolean legacyBottles;

    Settings(FileConfiguration c) {
        language = c.getString("language", "en").toLowerCase(Locale.ROOT);
        perPlayerLanguage = c.getBoolean("per-player-language", true);
        startLevels = Math.max(0, c.getInt("start-balance-levels", 0));
        minDepositLevels = Math.max(0, c.getInt("deposit.min-levels", 0));
        defaultLimitLevels = c.getInt("limit.default", 120);
        rankLimits = new LinkedHashMap<>();
        ConfigurationSection ranks = c.getConfigurationSection("limit.ranks");
        if (ranks != null) {
            for (String rank : ranks.getKeys(false)) rankLimits.put(rank.toLowerCase(Locale.ROOT), ranks.getInt(rank));
        }
        autoMinLevel = Math.max(1, c.getInt("auto-deposit.min-level", 5));
        keepXpOnDeath = c.getBoolean("keep-xp-on-death", false);
        sendToOffline = c.getBoolean("send.to-offline-players", true);
        repairEnabled = c.getBoolean("repair.enabled", true);
        repairRequireMending = c.getBoolean("repair.require-mending", true);
        repairDurabilityPerXp = Math.max(1, c.getInt("repair.durability-per-xp", 2));
        bottleEnabled = c.getBoolean("bottle.enabled", true);
        bottleMaxAmount = Math.max(1, c.getInt("bottle.max-amount", 64));
        legacyBottles = c.getBoolean("bottle.accept-xp-deposit-bottles", true);
        soundsEnabled = c.getBoolean("sounds.enabled", true);
        for (SoundType t : SoundType.values()) {
            String key = c.getString("sounds." + t.name().toLowerCase(Locale.ROOT).replace('_', '-'), "");
            if (key != null && !key.isBlank()) sounds.put(t, key.trim().toLowerCase(Locale.ROOT));
        }
        vault = c.getBoolean("vault-economy.enabled", false);
        vaultInPoints = "points".equalsIgnoreCase(c.getString("vault-economy.unit", "levels"));
        topSize = Math.max(1, Math.min(100, c.getInt("top.size", 10)));
        for (String n : c.getStringList("top.hidden")) hiddenInTop.add(n.toLowerCase(Locale.ROOT));
    }

    /** Storage limit of this player in points, Long.MAX_VALUE = unlimited. */
    public long limitPoints(Player player) {
        if (player.hasPermission("xptresor.limit.unlimited")) return Long.MAX_VALUE;
        int best = defaultLimitLevels;
        if (best < 0) return Long.MAX_VALUE;
        for (Map.Entry<String, Integer> e : rankLimits.entrySet()) {
            if (player.hasPermission("xptresor.limit." + e.getKey())) {
                if (e.getValue() < 0) return Long.MAX_VALUE;
                best = Math.max(best, e.getValue());
            }
        }
        return Xp.forLevel(best);
    }

    /** Limit for players that are offline (permissions unknown): the default limit. */
    public long defaultLimitPoints() {
        return defaultLimitLevels < 0 ? Long.MAX_VALUE : Xp.forLevel(defaultLimitLevels);
    }
}
