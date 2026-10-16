package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.AccountStore.Account;
import io.github.betamoon1.xptresor.Services.Amount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /xpt menu: deposit, withdraw, auto-deposit, auto-pay, repair, history and top with a click.
 *
 *  row 0:                [balance]
 *  row 1:  +1  +5  +10         -1  -5  -10
 *  row 2:  +20 +30 all         -20 -30 all
 *  row 3:      auto autopay repair history top
 *  row 4:                 [close]
 */
final class TresorMenu implements Listener {

    private static final int SIZE = 45;
    private static final int[] DEPOSIT_SLOTS = {10, 11, 12, 19, 20, 21};
    private static final int[] WITHDRAW_SLOTS = {14, 15, 16, 23, 24, 25};
    /** Levels per button, 0 = everything. */
    private static final int[] STEPS = {1, 5, 10, 20, 30, 0};
    private static final int BALANCE = 4;
    private static final int AUTO = 29;
    private static final int AUTO_PAY = 30;
    private static final int REPAIR = 31;
    private static final int HISTORY = 32;
    private static final int TOP = 33;
    private static final int CLOSE = 40;

    /** Marks our menu. */
    record Holder(UUID player) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /** Marks the read-only recipe view. */
    record RecipeHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final XpTresor plugin;

    TresorMenu(XpTresor plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(player.getUniqueId()), SIZE, m().component(player, "menu.title"));
        render(player, inv);
        player.openInventory(inv);
    }

    private void render(Player player, Inventory inv) {
        Settings s = plugin.settings();
        Account a = plugin.store().getOrCreate(player.getUniqueId(), player.getName());
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), 1);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // balance
        long limit = s.limitPoints(player);
        String limitText = limit == Long.MAX_VALUE ? m().raw(player, "words.unlimited")
                : m().raw(player, "words.levels", "levels", Xp.levelOf(limit));
        long have = Xp.of(player);
        inv.setItem(BALANCE, item(Material.EXPERIENCE_BOTTLE, m().component(player, "menu.balance.name"), List.of(
                m().component(player, "menu.balance.vault", "levels", Xp.levelOf(a.xp()), "xp", Services.fmt(a.xp())),
                m().component(player, "menu.balance.limit", "limit", limitText),
                m().component(player, "menu.balance.withdrawable", "now", Xp.levelsAffordable(have, a.xp())),
                m().component(player, "menu.balance.own", "levels", player.getLevel())), 1));

        // deposit / withdraw buttons
        for (int i = 0; i < STEPS.length; i++) {
            int step = STEPS[i];
            if (player.hasPermission("xptresor.deposit")) {
                boolean possible = step == 0 ? have > 0 : player.getLevel() >= step;
                inv.setItem(DEPOSIT_SLOTS[i], button(player, possible, step, true));
            }
            if (player.hasPermission("xptresor.withdraw")) {
                boolean possible = step == 0 ? a.xp() > 0 : Xp.nextLevels(have, step) <= a.xp();
                inv.setItem(WITHDRAW_SLOTS[i], button(player, possible, step, false));
            }
        }

        // auto-deposit
        if (player.hasPermission("xptresor.auto")) {
            int level = Math.max(a.autoLevel(), s.autoMinLevel);
            inv.setItem(AUTO, item(Material.HOPPER, m().component(player, "menu.auto.name", "state", state(player, a.autoDeposit())),
                    List.of(m().component(player, "menu.auto.level", "level", level),
                            Component.empty(),
                            m().component(player, "menu.auto.left"),
                            m().component(player, "menu.auto.right"),
                            m().component(player, "menu.auto.shift-right")), 1));
        }
        // auto-pay
        if (s.autoPayEnabled && player.hasPermission("xptresor.autopay")) {
            boolean on = plugin.autoPay().isOn(player);
            inv.setItem(AUTO_PAY, glow(item(Material.ENCHANTING_TABLE, m().component(player, "menu.auto-pay.name", "state", state(player, on)),
                    List.of(m().component(player, "menu.auto-pay.lore1"),
                            m().component(player, "menu.auto-pay.lore2"),
                            Component.empty(),
                            m().component(player, "menu.auto-pay.click")), 1), on));
        }
        // repair
        if (s.repairEnabled && player.hasPermission("xptresor.repair")) {
            inv.setItem(REPAIR, item(Material.ANVIL, m().component(player, "menu.repair.name"),
                    List.of(m().component(player, "menu.repair.left"), m().component(player, "menu.repair.right")), 1));
        }
        // history: the last few lines right in the tooltip
        if (player.hasPermission("xptresor.history")) {
            List<Component> lore = new ArrayList<>();
            List<String> lines = plugin.services().historyLines(player, a, 0, 5);
            if (lines.isEmpty()) lore.add(m().component(player, "history.empty"));
            for (String line : lines) lore.add(Messages.colored(line));
            lore.add(Component.empty());
            lore.add(m().component(player, "menu.history.click"));
            inv.setItem(HISTORY, item(Material.WRITABLE_BOOK, m().component(player, "menu.history.name"), lore, 1));
        }
        // top
        if (player.hasPermission("xptresor.top")) {
            List<Component> lore = new ArrayList<>();
            List<Account> top = plugin.store().top();
            for (int i = 0; i < Math.min(5, top.size()); i++) {
                Account t = top.get(i);
                lore.add(m().component(player, "top.line", "rank", i + 1, "player", t.name(), "levels", Xp.levelOf(t.xp()),
                        "xp", Services.fmt(t.xp())));
            }
            if (top.isEmpty()) lore.add(m().component(player, "top.empty"));
            lore.add(Component.empty());
            lore.add(m().component(player, "menu.top.click"));
            inv.setItem(TOP, item(Material.GOLD_INGOT, m().component(player, "menu.top.name"), lore, 1));
        }
        inv.setItem(CLOSE, item(Material.BARRIER, m().component(player, "menu.close"), List.of(), 1));
    }

    private ItemStack button(Player player, boolean possible, int step, boolean deposit) {
        String dir = deposit ? "deposit" : "withdraw";
        if (!possible) {
            String name = step == 0 ? "menu." + dir + ".all-impossible" : "menu." + dir + ".impossible";
            return item(Material.GRAY_DYE, m().component(player, name, "levels", step), List.of(), Math.max(1, step));
        }
        Material mat = step == 0 ? (deposit ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE)
                : (deposit ? Material.LIME_DYE : Material.ORANGE_DYE);
        String name = step == 0 ? "menu." + dir + ".all" : "menu." + dir + ".levels";
        return item(mat, m().component(player, name, "levels", step), List.of(), Math.max(1, step));
    }

    private String state(Player player, boolean on) {
        return m().raw(player, on ? "words.enabled" : "words.disabled");
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder topHolder = event.getView().getTopInventory().getHolder(false);
        if (topHolder instanceof RecipeHolder) {
            event.setCancelled(true); // just a picture of the recipe
            return;
        }
        if (!(topHolder instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Inventory inv = event.getView().getTopInventory();
        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        Services services = plugin.services();

        for (int i = 0; i < STEPS.length; i++) {
            Amount amount = STEPS[i] == 0 ? new Amount(Amount.Kind.ALL, 0) : new Amount(Amount.Kind.LEVELS, STEPS[i]);
            if (slot == DEPOSIT_SLOTS[i] && player.hasPermission("xptresor.deposit")) {
                services.deposit(player, amount);
                render(player, inv);
                return;
            }
            if (slot == WITHDRAW_SLOTS[i] && player.hasPermission("xptresor.withdraw")) {
                services.withdraw(player, amount);
                render(player, inv);
                return;
            }
        }
        switch (slot) {
            case AUTO -> {
                if (!player.hasPermission("xptresor.auto")) return;
                Account a = plugin.store().getOrCreate(player.getUniqueId(), player.getName());
                int min = plugin.settings().autoMinLevel;
                int level = Math.max(a.autoLevel(), min);
                if (click == ClickType.SHIFT_RIGHT) {
                    plugin.store().setAuto(a, a.autoDeposit(), Math.max(min, level - 5));
                } else if (click.isRightClick()) {
                    plugin.store().setAuto(a, a.autoDeposit(), level + 5);
                } else {
                    boolean on = !a.autoDeposit();
                    plugin.store().setAuto(a, on, level);
                    m().send(player, on ? "auto.enabled" : "auto.disabled", "level", level);
                    if (on && player.getLevel() >= level) services.autoDeposit(player, level - 1, player.getLevel());
                }
                services.sound(player, Settings.SoundType.MENU_CLICK);
                render(player, inv);
            }
            case AUTO_PAY -> {
                if (!plugin.settings().autoPayEnabled || !player.hasPermission("xptresor.autopay")) return;
                Account a = plugin.store().getOrCreate(player.getUniqueId(), player.getName());
                boolean on = !plugin.autoPay().isOn(player);
                plugin.store().setAutoPay(a, on);
                m().send(player, on ? "auto-pay.enabled" : "auto-pay.disabled");
                services.sound(player, Settings.SoundType.MENU_CLICK);
                render(player, inv);
            }
            case REPAIR -> {
                if (!plugin.settings().repairEnabled || !player.hasPermission("xptresor.repair")) return;
                services.repair(player, click.isRightClick());
                render(player, inv);
            }
            case HISTORY -> {
                if (!player.hasPermission("xptresor.history")) return;
                player.closeInventory();
                plugin.command().showHistory(player, plugin.store().getOrCreate(player.getUniqueId(), player.getName()), 1, "xpt");
            }
            case TOP -> {
                if (!player.hasPermission("xptresor.top")) return;
                player.closeInventory();
                player.performCommand("xpt top");
            }
            case CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getView().getTopInventory().getHolder(false);
        if (h instanceof Holder || h instanceof RecipeHolder) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ recipe view

    /**
     * /xpt recipe: the crafting recipe of the vault block, read from the config.
     *
     *   [ ][a][b][c][ ][ ][ ][ ][ ]
     *   [ ][d][e][f][ ][→][ ][R][ ]
     *   [ ][g][h][i][ ][ ][ ][ ][ ]
     */
    void openRecipe(Player player) {
        Settings s = plugin.settings();
        if (!s.blockEnabled || !s.blockRecipe || s.blockShape.isEmpty()) {
            m().send(player, "recipe.disabled");
            return;
        }
        Inventory inv = Bukkit.createInventory(new RecipeHolder(), 27, m().component(player, "recipe.title"));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), 1);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        int[] grid = {1, 2, 3, 10, 11, 12, 19, 20, 21};
        for (int g : grid) inv.setItem(g, null);
        List<String> shape = s.blockShape;
        int rowOffset = (3 - Math.min(3, shape.size())) / 2;
        for (int r = 0; r < Math.min(3, shape.size()); r++) {
            String row = shape.get(r);
            int colOffset = (3 - Math.min(3, row.length())) / 2;
            for (int c = 0; c < Math.min(3, row.length()); c++) {
                String name = s.blockIngredients.get(row.charAt(c));
                Material m = name == null ? null : Material.matchMaterial(name);
                if (m != null && m.isItem()) inv.setItem(grid[(r + rowOffset) * 3 + c + colOffset], new ItemStack(m));
            }
        }
        inv.setItem(14, item(Material.ARROW, m().component(player, "recipe.arrow"), List.of(), 1));
        ItemStack result = plugin.block().createItem();
        inv.setItem(16, result);
        player.openInventory(inv);
        plugin.services().sound(player, Settings.SoundType.MENU_CLICK);
    }

    /** Closes open menus (reload / disable). */
    void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder h = p.getOpenInventory().getTopInventory().getHolder(false);
            if (h instanceof Holder || h instanceof RecipeHolder) p.closeInventory();
        }
    }

    // ------------------------------------------------------------------ helpers

    static ItemStack item(Material material, Component name, List<Component> lore, int amount) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>(lore.size());
            for (Component c : lore) lines.add(c.decoration(TextDecoration.ITALIC, false));
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack glow(ItemStack stack, boolean glow) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(glow);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
