package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.AccountStore.Account;
import io.github.betamoon1.xptresor.Settings.SoundType;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Everything that moves XP: deposit, withdraw, send, auto-deposit, repair and bottles. */
public final class Services implements Listener {

    /** An amount typed by a player: "all", "10" (levels) or "500p" (points). */
    public record Amount(Kind kind, long value) {
        public enum Kind { ALL, LEVELS, POINTS }

        /** "all" in the supported languages. */
        private static final java.util.Set<String> ALL_WORDS = java.util.Set.of("all", "max", "alles", "todo", "tout",
                "tutto", "wszystko", "tudo", "все", "всё", "hepsi");

        /** null if the text is not a valid amount. */
        public static Amount parse(String text) {
            String t = text.toLowerCase(Locale.ROOT).trim();
            if (ALL_WORDS.contains(t)) return new Amount(Kind.ALL, 0);
            Kind kind = Kind.LEVELS;
            if (t.endsWith("xp")) {
                kind = Kind.POINTS;
                t = t.substring(0, t.length() - 2);
            } else if (t.endsWith("p")) {
                kind = Kind.POINTS;
                t = t.substring(0, t.length() - 1);
            } else if (t.endsWith("l")) {
                t = t.substring(0, t.length() - 1);
            }
            try {
                long v = Long.parseLong(t);
                if (v <= 0 || (kind == Kind.LEVELS && v > 100_000)) return null;
                return new Amount(kind, v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static final int OFFHAND_SLOT = 40;
    private static final int[] ARMOR_SLOTS = {36, 37, 38, 39};
    private static final NamespacedKey ARMORED_ELYTRA_TIER = NamespacedKey.fromString("armoredelytra:armor_tier_level");
    private static final NamespacedKey LEGACY_BOTTLE = NamespacedKey.fromString("xp-deposit:bottle-xp");

    private final XpTresor plugin;
    private final NamespacedKey bottleKey;
    private final Map<UUID, Long> fullNotice = new HashMap<>();
    /** After withdrawing, auto-deposit waits a moment (the level change of the withdrawal itself). */
    private final Map<UUID, Long> autoBlockedUntil = new HashMap<>();

    Services(XpTresor plugin) {
        this.plugin = plugin;
        this.bottleKey = new NamespacedKey(plugin, "xp");
    }

    private Settings s() {
        return plugin.settings();
    }

    private Messages m() {
        return plugin.messages();
    }

    private AccountStore store() {
        return plugin.store();
    }

    public static String fmt(long n) {
        return String.format(Locale.GERMANY, "%,d", n);
    }

    public void sound(Player player, SoundType type) {
        if (player == null || !s().soundsEnabled) return;
        String key = s().sounds.get(type);
        if (key == null) return;
        try {
            player.playSound(player.getLocation(), key, 1f, 1f);
        } catch (RuntimeException ignored) {
            // unknown sound key in the config
        }
    }

    /** Room left in the account in points (Long.MAX_VALUE = unlimited). */
    private long room(Account account, long limit) {
        return limit == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, limit - account.xp());
    }

    // ------------------------------------------------------------------ deposit / withdraw

    public void deposit(Player player, Amount amount) {
        Account account = store().getOrCreate(player.getUniqueId(), player.getName());
        long have = Xp.of(player);
        int levelBefore = player.getLevel();
        long points;
        switch (amount.kind()) {
            case ALL -> points = have;
            case LEVELS -> {
                if (amount.value() > levelBefore) {
                    m().send(player, "deposit.not-enough-levels", "levels", levelBefore);
                    return;
                }
                points = Xp.topLevels(have, (int) amount.value());
            }
            default -> {
                if (amount.value() > have) {
                    m().send(player, "deposit.not-enough-points", "xp", fmt(have));
                    return;
                }
                points = amount.value();
            }
        }
        if (points <= 0) {
            m().send(player, "deposit.nothing");
            return;
        }
        if (s().minDepositLevels > 0 && points < Xp.forLevel(s().minDepositLevels)) {
            m().send(player, "deposit.minimum", "levels", s().minDepositLevels, "xp", fmt(Xp.forLevel(s().minDepositLevels)));
            return;
        }
        long limit = s().limitPoints(player);
        long room = room(account, limit);
        boolean partial = false;
        if (room <= 0) {
            m().send(player, "deposit.full", "limit", Xp.levelOf(limit));
            return;
        }
        if (points > room) {
            if (amount.kind() != Amount.Kind.ALL) {
                m().send(player, "deposit.too-much", "xp", fmt(room), "limit", Xp.levelOf(limit));
                return;
            }
            points = room;
            partial = true;
        }
        Xp.set(player, have - points);
        store().add(account, points);
        int levels = levelBefore - player.getLevel();
        m().send(player, partial ? "deposit.partial" : "deposit.done", "levels", levels, "xp", fmt(points),
                "balance", Xp.levelOf(account.xp()));
        sound(player, SoundType.DEPOSIT);
    }

    public void withdraw(Player player, Amount amount) {
        Account account = store().getOrCreate(player.getUniqueId(), player.getName());
        long balance = account.xp();
        if (balance <= 0) {
            m().send(player, "withdraw.empty");
            return;
        }
        long have = Xp.of(player);
        long points;
        switch (amount.kind()) {
            case ALL -> points = balance;
            case LEVELS -> {
                points = Xp.nextLevels(have, (int) amount.value());
                if (points > balance) {
                    m().send(player, "withdraw.not-enough-levels", "max", Xp.levelsAffordable(have, balance),
                            "xp", fmt(balance));
                    return;
                }
            }
            default -> {
                points = amount.value();
                if (points > balance) {
                    m().send(player, "withdraw.not-enough-points", "xp", fmt(balance));
                    return;
                }
            }
        }
        int levelBefore = player.getLevel();
        if (!store().add(account, -points)) return;
        autoBlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1500);
        Xp.set(player, have + points);
        m().send(player, "withdraw.done", "levels", player.getLevel() - levelBefore, "xp", fmt(points),
                "balance", Xp.levelOf(account.xp()));
        sound(player, SoundType.WITHDRAW);
    }

    // ------------------------------------------------------------------ send

    public void send(Player player, String targetName, Amount amount) {
        Account from = store().getOrCreate(player.getUniqueId(), player.getName());
        Player online = Bukkit.getPlayerExact(targetName);
        Account to;
        if (online != null && !XpTresor.isBot(online)) {
            to = store().getOrCreate(online.getUniqueId(), online.getName());
        } else {
            to = store().byName(targetName);
            if (to != null && !s().sendToOffline) {
                m().send(player, "send.offline-disabled", "player", to.name());
                return;
            }
        }
        if (to == null) {
            m().send(player, "player-unknown", "player", targetName);
            return;
        }
        if (to.id().equals(player.getUniqueId())) {
            m().send(player, "send.self");
            return;
        }
        long balance = from.xp();
        long points;
        switch (amount.kind()) {
            case ALL -> points = balance;
            case LEVELS -> {
                if (amount.value() > Xp.levelOf(balance)) {
                    m().send(player, "send.not-enough", "levels", Xp.levelOf(balance), "xp", fmt(balance));
                    return;
                }
                points = Xp.topLevels(balance, (int) amount.value());
            }
            default -> points = amount.value();
        }
        if (points <= 0 || points > balance) {
            m().send(player, "send.not-enough", "levels", Xp.levelOf(balance), "xp", fmt(balance));
            return;
        }
        Player receiver = Bukkit.getPlayer(to.id());
        long limit = receiver != null ? s().limitPoints(receiver) : s().defaultLimitPoints();
        long room = room(to, limit);
        if (points > room) {
            m().send(player, "send.receiver-full", "player", to.name(), "free", fmt(room));
            return;
        }
        if (!store().transfer(from, to, points)) return;
        int levelsWorth = Xp.levelOf(points);
        m().send(player, "send.done", "player", to.name(), "xp", fmt(points), "levels", levelsWorth);
        sound(player, SoundType.SEND);
        if (receiver != null) {
            m().send(receiver, "send.received", "player", player.getName(), "xp", fmt(points), "levels", levelsWorth);
            sound(receiver, SoundType.RECEIVE);
        }
    }

    // ------------------------------------------------------------------ auto deposit

    /**
     * Called one tick after a player gained levels. Deposits only when the player crosses their
     * auto-deposit level (like XP-Deposit did) - not on every level above it, otherwise XP that was
     * just withdrawn would go straight back into the vault.
     */
    public void autoDeposit(Player player, int oldLevel, int newLevel) {
        if (!player.isOnline() || !player.hasPermission("xptresor.auto")) return;
        Long blocked = autoBlockedUntil.get(player.getUniqueId());
        if (blocked != null && System.currentTimeMillis() < blocked) return;
        Account account = store().get(player.getUniqueId());
        if (account == null || !account.autoDeposit()) return;
        int threshold = Math.max(account.autoLevel(), s().autoMinLevel);
        if (oldLevel >= threshold || newLevel < threshold || player.getLevel() < threshold) return;
        long have = Xp.of(player);
        long limit = s().limitPoints(player);
        long room = room(account, limit);
        if (room <= 0) {
            long now = System.currentTimeMillis();
            Long last = fullNotice.get(player.getUniqueId());
            if (last == null || now - last > 60_000) {
                fullNotice.put(player.getUniqueId(), now);
                m().send(player, "auto.full", "limit", Xp.levelOf(limit));
            }
            return;
        }
        long points = Math.min(have, room);
        int levelBefore = player.getLevel();
        Xp.set(player, have - points);
        store().add(account, points);
        m().actionBar(player, "auto.deposited", "levels", levelBefore - player.getLevel(), "xp", fmt(points),
                "balance", Xp.levelOf(account.xp()));
        sound(player, SoundType.AUTO_DEPOSIT);
    }

    void forget(UUID player) {
        fullNotice.remove(player);
        autoBlockedUntil.remove(player);
    }

    // ------------------------------------------------------------------ repair

    private enum Check { OK, OK_ARMORED, NOTHING, NO_MENDING, INVALID, ARMORED_ELYTRA }

    public void repair(Player player, boolean all) {
        if (!s().repairEnabled) {
            m().send(player, "repair.disabled");
            return;
        }
        Account account = store().getOrCreate(player.getUniqueId(), player.getName());
        long balance = account.xp();
        if (balance <= 0) {
            m().send(player, "repair.empty");
            return;
        }
        ArmoredElytraBridge armored = ArmoredElytraBridge.connect(plugin);
        PlayerInventory inv = player.getInventory();
        int[] slots = all
                ? new int[]{inv.getHeldItemSlot(), OFFHAND_SLOT, ARMOR_SLOTS[0], ARMOR_SLOTS[1], ARMOR_SLOTS[2], ARMOR_SLOTS[3]}
                : new int[]{inv.getHeldItemSlot()};
        int perXp = s().repairDurabilityPerXp;
        long used = 0;
        int repairedItems = 0;
        boolean stillDamaged = false;
        Check problem = null;

        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            Check check = check(item, armored);
            if (check != Check.OK && check != Check.OK_ARMORED) {
                if (problem == null) problem = check;
                continue;
            }
            boolean isArmored = check == Check.OK_ARMORED;
            int damage = isArmored ? armored.damage(item) : ((Damageable) item.getItemMeta()).getDamage();
            if (damage <= 0) {
                if (problem == null) problem = Check.NOTHING;
                continue;
            }
            long needXp = (damage + perXp - 1) / perXp;
            long useXp = Math.min(needXp, balance - used);
            if (useXp <= 0) {
                stillDamaged = true;
                break;
            }
            int repaired = (int) Math.min(damage, useXp * perXp);
            if (isArmored) {
                armored.setDamage(item, damage - repaired);
            } else {
                Damageable meta = (Damageable) item.getItemMeta();
                meta.setDamage(damage - repaired);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            used += useXp;
            repairedItems++;
            if (repaired < damage) stillDamaged = true;
        }

        if (repairedItems == 0) {
            String key = switch (all || problem == null ? Check.NOTHING : problem) {
                case NO_MENDING -> "repair.no-mending";
                case ARMORED_ELYTRA -> "repair.armored-elytra";
                case INVALID -> "repair.invalid";
                default -> "repair.nothing";
            };
            m().send(player, key);
            return;
        }
        store().add(account, -used);
        m().send(player, stillDamaged ? "repair.partial" : "repair.done", "items", repairedItems, "xp", fmt(used));
        sound(player, SoundType.REPAIR);
    }

    private Check check(ItemStack item, ArmoredElytraBridge armored) {
        if (item == null || item.getType().isAir()) return Check.INVALID;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) return Check.INVALID;
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (max <= 0) return Check.INVALID;
        boolean isArmored = ARMORED_ELYTRA_TIER != null
                && meta.getPersistentDataContainer().has(ARMORED_ELYTRA_TIER, PersistentDataType.INTEGER);
        // ArmoredElytra keeps its own durability value: only repair through ArmoredElytra itself
        if (isArmored && armored == null) return Check.ARMORED_ELYTRA;
        if (s().repairRequireMending && !meta.hasEnchant(Enchantment.MENDING)) return Check.NO_MENDING;
        if (isArmored) return Check.OK_ARMORED;
        return damageable.getDamage() > 0 ? Check.OK : Check.NOTHING;
    }

    // ------------------------------------------------------------------ bottles

    public void bottle(Player player, Amount perBottle, int count) {
        if (!s().bottleEnabled) {
            m().send(player, "bottle.disabled");
            return;
        }
        if (count < 1 || count > s().bottleMaxAmount) {
            m().send(player, "bottle.max", "max", s().bottleMaxAmount);
            return;
        }
        if (perBottle.kind() == Amount.Kind.ALL) {
            m().send(player, "bottle.usage", "cmd", "xpt");
            return;
        }
        long xpEach = perBottle.kind() == Amount.Kind.LEVELS ? Xp.forLevel((int) perBottle.value()) : perBottle.value();
        if (xpEach <= 0 || xpEach > Integer.MAX_VALUE) {
            m().send(player, "number-invalid");
            return;
        }
        Account account = store().getOrCreate(player.getUniqueId(), player.getName());
        long total = xpEach * count;
        if (total > account.xp()) {
            m().send(player, "bottle.not-enough", "xp", fmt(account.xp()), "needed", fmt(total));
            return;
        }
        if (!store().add(account, -total)) return;
        ItemStack bottles = createBottle((int) xpEach, count);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(bottles);
        if (!leftover.isEmpty()) {
            for (ItemStack rest : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            m().send(player, "bottle.dropped");
        }
        m().send(player, "bottle.done", "amount", count, "xp", fmt(xpEach), "levels", Xp.levelOf(xpEach));
        sound(player, SoundType.BOTTLE);
    }

    private ItemStack createBottle(int xp, int count) {
        ItemStack stack = new ItemStack(Material.EXPERIENCE_BOTTLE, count);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(m().component("bottle.name", "xp", fmt(xp), "levels", Xp.levelOf(xp))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(m().component("bottle.lore", "xp", fmt(xp), "levels", Xp.levelOf(xp))
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(bottleKey, PersistentDataType.INTEGER, xp);
        stack.setItemMeta(meta);
        return stack;
    }

    /** A thrown XpTresor bottle releases exactly its XP; normal bottles stay vanilla. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBottle(ExpBottleEvent event) {
        ItemMeta meta = event.getEntity().getItem().getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer xp = pdc.get(bottleKey, PersistentDataType.INTEGER);
        if (xp == null && s().legacyBottles && LEGACY_BOTTLE != null) xp = pdc.get(LEGACY_BOTTLE, PersistentDataType.INTEGER);
        if (xp != null && xp > 0) event.setExperience(xp);
    }

    // ------------------------------------------------------------------ helpers for commands

    /** Online player, or a known account by name. */
    public Account findAccount(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && !XpTresor.isBot(online)) return store().getOrCreate(online.getUniqueId(), online.getName());
        return store().byName(name);
    }

    public static String nameOf(OfflinePlayer p) {
        return p.getName() == null ? p.getUniqueId().toString() : p.getName();
    }
}
