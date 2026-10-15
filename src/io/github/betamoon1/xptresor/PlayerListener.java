package io.github.betamoon1.xptresor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PlayerListener implements Listener {

    private final XpTresor plugin;

    PlayerListener(XpTresor plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (XpTresor.isBot(player)) return; // AFK bots / NPCs get no account
        plugin.store().getOrCreate(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.services().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.settings().keepXpOnDeath || !event.getEntity().hasPermission("xptresor.keepxp")) return;
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        if (event.getNewLevel() <= event.getOldLevel()) return;
        Player player = event.getPlayer();
        if (XpTresor.isBot(player)) return;
        int oldLevel = event.getOldLevel();
        int newLevel = event.getNewLevel();
        // change the XP after the event, not inside it
        Bukkit.getScheduler().runTask(plugin, () -> plugin.services().autoDeposit(player, oldLevel, newLevel));
    }
}
