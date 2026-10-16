package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.Services.Amount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.Vault;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * The XP vault as a block: a craftable vault (the trial chamber block) that opens the vault menu on
 * right-click and deposits all XP on sneak + right-click. Everyone uses their own account with it,
 * like an ender chest. Placed blocks are marked in the block's own data, so nothing extra is stored.
 */
final class TresorBlock implements Listener {

    private final XpTresor plugin;
    private final NamespacedKey key;
    private final NamespacedKey recipeKey;
    private boolean recipeRegistered;

    TresorBlock(XpTresor plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "vault_block");
        this.recipeKey = new NamespacedKey(plugin, "vault_block");
    }

    /** (Re)registers the crafting recipe from the config. */
    void loadRecipe() {
        unloadRecipe();
        Settings s = plugin.settings();
        if (!s.blockEnabled || !s.blockRecipe) return;
        List<String> shape = s.blockShape;
        if (shape.isEmpty() || shape.size() > 3) {
            plugin.getLogger().warning("vault-block.recipe.shape needs 1-3 rows - recipe disabled");
            return;
        }
        try {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem());
            recipe.shape(shape.toArray(new String[0]));
            for (Map.Entry<Character, String> e : s.blockIngredients.entrySet()) {
                Material m = Material.matchMaterial(e.getValue());
                if (m == null || !m.isItem()) {
                    plugin.getLogger().warning("vault-block.recipe: unknown item '" + e.getValue() + "' - recipe disabled");
                    return;
                }
                recipe.setIngredient(e.getKey(), m);
            }
            Bukkit.addRecipe(recipe);
            recipeRegistered = true;
            if (!Bukkit.getOnlinePlayers().isEmpty()) Bukkit.updateRecipes(); // /xpt reload while players are online
            for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Invalid vault-block recipe - recipe disabled", e);
        }
    }

    void unloadRecipe() {
        if (!recipeRegistered) return;
        Bukkit.removeRecipe(recipeKey);
        recipeRegistered = false;
    }

    /** The placeable vault block item. */
    ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.VAULT);
        ItemMeta meta = stack.getItemMeta();
        Messages m = plugin.messages();
        meta.displayName(m.component("block.name").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(m.component("block.lore1").decoration(TextDecoration.ITALIC, false));
        lore.add(m.component("block.lore2").decoration(TextDecoration.ITALIC, false));
        if (plugin.settings().blockSneakDeposit) lore.add(m.component("block.lore3").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isTresorItem(ItemStack item) {
        if (item == null || item.getType() != Material.VAULT || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    boolean isTresor(Block block) {
        if (block.getType() != Material.VAULT) return false;
        BlockState state = block.getState(false);
        return state instanceof TileState tile && tile.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isTresorItem(event.getItemInHand())) return;
        if (!plugin.settings().blockEnabled) {
            event.setCancelled(true);
            return;
        }
        BlockState state = event.getBlockPlaced().getState();
        if (!(state instanceof Vault vault)) return;
        vault.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        try {
            // never "wake up" like a trial chamber vault (no floating loot preview)
            vault.setActivationRange(0);
        } catch (RuntimeException ignored) {
            // older server versions may not allow 0
        }
        vault.update(true, false);
        plugin.messages().send(event.getPlayer(), "block.placed");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !isTresor(block)) return;
        // never act like a trial chamber vault (trial keys etc.) and never place blocks against it
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!plugin.settings().blockEnabled) return;
        if (!player.hasPermission("xptresor.block")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (player.isSneaking() && plugin.settings().blockSneakDeposit && player.hasPermission("xptresor.deposit")) {
            if (Xp.of(player) <= 0) plugin.messages().send(player, "deposit.nothing");
            else plugin.services().deposit(player, new Amount(Amount.Kind.ALL, 0));
            return;
        }
        plugin.menu().open(player);
        plugin.services().sound(player, Settings.SoundType.MENU_CLICK);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isTresor(block)) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().dropItemNaturally(at, createItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTresor);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTresor);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (recipeRegistered) event.getPlayer().discoverRecipe(recipeKey);
    }
}
