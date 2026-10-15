package io.github.betamoon1.xptresor;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional connection to ArmoredElytra. Armored elytras store their own durability next to the vanilla
 * damage, so they are repaired through ArmoredElytra's DurabilityManager, which keeps both in sync.
 */
final class ArmoredElytraBridge {

    private static boolean warned;

    private final Object manager;
    private final Method get;
    private final Method set;

    private ArmoredElytraBridge(Object manager, Method get, Method set) {
        this.manager = manager;
        this.get = get;
        this.set = set;
    }

    /** null if ArmoredElytra is not installed or not compatible. Connects fresh each time (config changes). */
    static ArmoredElytraBridge connect(XpTresor plugin) {
        Plugin ae = Bukkit.getPluginManager().getPlugin("ArmoredElytra");
        if (ae == null || !ae.isEnabled()) return null;
        try {
            ClassLoader cl = ae.getClass().getClassLoader();
            String base = "nl.pim16aap2.armoredElytra.";
            Class<?> editorType = Class.forName(base + "nbtEditor.NBTEditor", true, cl);
            Class<?> configType = Class.forName(base + "util.ConfigLoader", true, cl);
            Class<?> tierType = Class.forName(base + "util.ArmorTier", true, cl);
            Class<?> managerType = Class.forName(base + "nbtEditor.DurabilityManager", true, cl);
            Object editor = ae.getClass().getMethod("getNbtEditor").invoke(ae);
            Object config = ae.getClass().getMethod("getConfigLoader").invoke(ae);
            Object manager = managerType.getConstructor(editorType, configType).newInstance(editor, config);
            return new ArmoredElytraBridge(manager,
                    managerType.getMethod("getRealDurability", ItemStack.class, tierType),
                    managerType.getMethod("setDurability", ItemStack.class, int.class, tierType));
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "ArmoredElytra is installed, but its durability API is not "
                        + "compatible - armored elytras can't be repaired with stored XP", e);
            }
            return null;
        }
    }

    /** Damage in ArmoredElytra's own scale (0 = fully repaired). */
    int damage(ItemStack item) {
        try {
            return (int) get.invoke(manager, item, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ArmoredElytra getRealDurability failed", e);
        }
    }

    void setDamage(ItemStack item, int damage) {
        try {
            set.invoke(manager, item, damage, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ArmoredElytra setDurability failed", e);
        }
    }
}
