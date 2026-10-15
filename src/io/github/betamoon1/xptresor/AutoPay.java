package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.AccountStore.Account;
import io.github.betamoon1.xptresor.AccountStore.Reason;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pays enchanting and anvil costs from the vault when the player's own levels are not enough.
 *
 * Minecraft only lets a player click an enchantment / take an anvil result if they already have the
 * levels, so while the enchanting table or anvil is open the missing levels are lent from the vault
 * to the player. What is spent is paid from the lent XP first, the rest goes back into the vault when
 * the menu closes. The result is exactly the same as withdrawing the missing levels by hand first.
 *
 * Enchanting needs the level of the most expensive offer to make all buttons clickable; when one is
 * clicked the loan is lowered to exactly what that offer needs before Minecraft takes the levels, so
 * a cheap enchantment never costs the expensive levels at the top.
 */
final class AutoPay implements Listener {

    enum Kind { ENCHANTING, ANVIL }

    private static final class Session {
        final Kind kind;
        /** Points currently on the player that belong to the vault. */
        long lent;
        /** Player total after our last change - a drop below it means Minecraft took levels. */
        long lastTotal;
        /** Points the vault paid during this session. */
        long paid;

        Session(Kind kind) {
            this.kind = kind;
        }
    }

    private final XpTresor plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    AutoPay(XpTresor plugin) {
        this.plugin = plugin;
    }

    boolean isActive(Player player) {
        Session s = sessions.get(player.getUniqueId());
        return s != null && s.lent > 0;
    }

    /** Is auto-pay on for this player (permission, server setting, own switch)? */
    boolean isOn(Player player) {
        Settings s = plugin.settings();
        if (!s.autoPayEnabled || XpTresor.isBot(player) || !player.hasPermission("xptresor.autopay")) return false;
        Account a = plugin.store().get(player.getUniqueId());
        if (a == null) return false;
        return a.autoPay() != null ? a.autoPay() : s.autoPayDefault;
    }

    private boolean usable(Player player, Kind kind) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false; // levels don't matter there
        Settings s = plugin.settings();
        if (kind == Kind.ENCHANTING && !s.autoPayEnchanting) return false;
        if (kind == Kind.ANVIL && !s.autoPayAnvil) return false;
        return isOn(player);
    }

    // ------------------------------------------------------------------ enchanting

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        int need = 0;
        EnchantmentOffer[] offers = event.getOffers();
        if (offers != null) for (EnchantmentOffer o : offers) if (o != null) need = Math.max(need, o.getCost());
        int needed = need;
        // the offers are sent to the client after this event - adjust the levels right after it
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (open(player, InventoryType.ENCHANTING)) prepare(player, Kind.ENCHANTING, needed);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.lent <= 0) return;
        // keep only what THIS offer needs - Minecraft takes the levels right after this event
        adjust(player, s, event.getExpLevelCost());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session now = sessions.get(player.getUniqueId());
            if (now != null) settle(player, now);
        });
    }

    // ------------------------------------------------------------------ anvil

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        // the cost is final only after all plugins handled the event - read it one tick later
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!open(player, InventoryType.ANVIL)) return;
            InventoryView view = player.getOpenInventory();
            if (!(view instanceof AnvilView anvil)) return;
            ItemStack result = anvil.getTopInventory().getItem(2);
            int cost = anvil.getRepairCost();
            boolean tooExpensive = cost >= anvil.getMaximumRepairCost() && !anvil.bypassesEnchantmentLevelRestriction();
            int need = result == null || result.getType().isAir() || cost <= 0 || tooExpensive ? 0 : cost;
            prepare(player, Kind.ANVIL, need);
        });
    }

    // ------------------------------------------------------------------ end of a session

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) end(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer());
    }

    /** Dying with the table open: the lent XP goes back first, so it can't drop. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!sessions.containsKey(player.getUniqueId())) return;
        end(player);
        if (!event.getKeepLevel()) event.setDroppedExp(Math.min(player.getLevel() * 7, 100));
    }

    void endAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p);
        }
        sessions.clear();
    }

    // ------------------------------------------------------------------ the bookkeeping

    private static boolean open(Player player, InventoryType type) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getType() == type;
    }

    /** Makes sure the player has at least {@code needLevel} levels, lending from the vault if needed. */
    private void prepare(Player player, Kind kind, int needLevel) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null && s.kind != kind) {
            end(player);
            s = null;
        }
        if (!usable(player, kind)) {
            if (s != null) end(player);
            return;
        }
        if (s == null) {
            if (needLevel <= player.getLevel()) return; // enough own levels - nothing to do
            s = new Session(kind);
            s.lastTotal = Xp.of(player);
            sessions.put(player.getUniqueId(), s);
        }
        long before = s.lent;
        adjust(player, s, needLevel);
        if (s.lent > before) {
            int levels = player.getLevel() - Xp.levelOf(Xp.of(player) - s.lent);
            plugin.messages().actionBar(player, "auto-pay.ready", "levels", levels, "xp", Services.fmt(s.lent));
        }
    }

    /** Sets the loan so the player has exactly what {@code needLevel} requires (or nothing extra). */
    private void adjust(Player player, Session s, int needLevel) {
        settle(player, s);
        Account account = plugin.store().get(player.getUniqueId());
        if (account == null) return;
        long total = Xp.of(player);
        long own = Math.max(0, total - s.lent);
        long wanted = needLevel > Xp.levelOf(own) ? Xp.forLevel(needLevel) - own : 0;
        if (wanted > s.lent + account.xp()) wanted = 0; // the vault can't cover it - lend nothing
        long delta = wanted - s.lent;
        if (delta == 0) return;
        if (!plugin.store().moveLoan(account, delta)) return;
        s.lent = wanted;
        Xp.set(player, own + wanted);
        s.lastTotal = Xp.of(player);
    }

    /** Levels Minecraft took since our last change are paid from the loan first. */
    private static void settle(Player player, Session s) {
        long total = Xp.of(player);
        if (total < s.lastTotal) {
            long spent = s.lastTotal - total;
            long fromVault = Math.min(spent, s.lent);
            s.lent -= fromVault;
            s.paid += fromVault;
        }
        s.lastTotal = total;
    }

    /** Gives the unused loan back to the vault and notes what the vault paid. */
    void end(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s == null) return;
        settle(player, s);
        Account account = plugin.store().get(player.getUniqueId());
        long back = 0;
        if (s.lent > 0) {
            long total = Xp.of(player);
            back = Math.min(s.lent, total);
            Xp.set(player, total - back);
        }
        if (account != null) plugin.store().closeLoan(account, back);
        if (s.paid > 0 && account != null) {
            plugin.store().logAndSave(account, s.kind == Kind.ENCHANTING ? Reason.AUTOPAY_ENCHANT : Reason.AUTOPAY_ANVIL,
                    -s.paid, null);
            if (player.isOnline()) {
                plugin.messages().send(player, s.kind == Kind.ENCHANTING ? "auto-pay.paid-enchanting" : "auto-pay.paid-anvil",
                        "xp", Services.fmt(s.paid), "levels", Xp.levelOf(s.paid), "balance", Xp.levelOf(account.xp()));
                plugin.services().sound(player, Settings.SoundType.AUTO_PAY);
            }
        }
    }
}
