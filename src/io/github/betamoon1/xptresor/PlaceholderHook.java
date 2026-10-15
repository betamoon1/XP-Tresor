package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.AccountStore.Account;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI placeholders (only loaded when PlaceholderAPI is installed):
 * %xptresor_levels%, %xptresor_xp%, %xptresor_xp_formatted%, %xptresor_limit%, %xptresor_auto%,
 * %xptresor_top_&lt;n&gt;_name%, %xptresor_top_&lt;n&gt;_levels%, %xptresor_top_&lt;n&gt;_xp%
 */
final class PlaceholderHook extends PlaceholderExpansion {

    private final XpTresor plugin;

    private PlaceholderHook(XpTresor plugin) {
        this.plugin = plugin;
    }

    static void install(XpTresor plugin) {
        new PlaceholderHook(plugin).register();
    }

    @Override public String getIdentifier() { return "xptresor"; }
    @Override public String getAuthor() { return "betamoon"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("top_")) return top(p.substring(4));
        if (player == null) return "";
        Account a = plugin.store().get(player.getUniqueId());
        long xp = a == null ? 0 : a.xp();
        Player online = player.getPlayer();
        Messages m = plugin.messages();
        return switch (p) {
            case "levels" -> Integer.toString(Xp.levelOf(xp));
            case "xp" -> Long.toString(xp);
            case "xp_formatted" -> Services.fmt(xp);
            case "auto" -> {
                String key = a != null && a.autoDeposit() ? "words.enabled" : "words.disabled";
                yield online != null ? m.raw(online, key) : m.raw(key);
            }
            case "limit" -> {
                long limit = online != null ? plugin.settings().limitPoints(online) : plugin.settings().defaultLimitPoints();
                if (limit != Long.MAX_VALUE) yield Integer.toString(Xp.levelOf(limit));
                yield online != null ? m.raw(online, "words.unlimited") : m.raw("words.unlimited");
            }
            default -> null;
        };
    }

    private String top(String rest) {
        int sep = rest.indexOf('_');
        if (sep <= 0) return null;
        int rank;
        try {
            rank = Integer.parseInt(rest.substring(0, sep));
        } catch (NumberFormatException e) {
            return null;
        }
        List<Account> top = plugin.store().top();
        if (rank < 1 || rank > top.size()) return "-";
        Account a = top.get(rank - 1);
        return switch (rest.substring(sep + 1)) {
            case "name" -> a.name();
            case "levels" -> Integer.toString(Xp.levelOf(a.xp()));
            case "xp" -> Services.fmt(a.xp());
            default -> null;
        };
    }
}
