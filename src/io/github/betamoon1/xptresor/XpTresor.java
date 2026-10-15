package io.github.betamoon1.xptresor;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Level;

/** XpTresor - a personal XP storage for every player. */
public final class XpTresor extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private AccountStore store;
    private Services services;
    private AutoPay autoPay;
    private TresorMenu menu;
    private TresorBlock block;
    private TresorCommand command;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        store = new AccountStore(this);
        store.loadAll();
        services = new Services(this);
        autoPay = new AutoPay(this);
        menu = new TresorMenu(this);
        block = new TresorBlock(this);
        command = new TresorCommand(this);
        block.loadRecipe();

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(services, this);
        Bukkit.getPluginManager().registerEvents(autoPay, this);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(block, this);

        PluginCommand pc = getCommand("xptresor");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isBot(p)) store.getOrCreate(p.getUniqueId(), p.getName());
        }

        if (settings.vault) hookVault();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                PlaceholderHook.install(this);
                getLogger().info("PlaceholderAPI placeholders registered (%xptresor_...%)");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "PlaceholderAPI found, but the placeholders could not be registered", t);
            }
        }
    }

    private void hookVault() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            getLogger().warning("vault-economy is enabled, but Vault is not installed");
            return;
        }
        try {
            VaultEconomy.register(this);
            getLogger().info("Registered as Vault economy (" + (settings.vaultInPoints ? "XP points" : "levels") + ")");
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Could not register the Vault economy", t);
        }
    }

    @Override
    public void onDisable() {
        // lent enchanting/anvil XP goes back into the vaults before the server stops
        if (autoPay != null) autoPay.endAll();
        if (menu != null) menu.closeAll();
        if (block != null) block.unloadRecipe();
    }

    void loadSettings() {
        reloadConfig();
        ConfigUpdater.update(this);
        settings = new Settings(getConfig());
        messages = new Messages(this, settings.language.toLowerCase(Locale.ROOT), settings.perPlayerLanguage);
        if (block != null) block.loadRecipe(); // /xpt reload
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public AccountStore store() {
        return store;
    }

    public Services services() {
        return services;
    }

    AutoPay autoPay() {
        return autoPay;
    }

    TresorMenu menu() {
        return menu;
    }

    TresorBlock block() {
        return block;
    }

    TresorCommand command() {
        return command;
    }

    /** AFK bots and other NPCs (common convention: metadata "NPC") get no account. */
    public static boolean isBot(Player player) {
        return player.hasMetadata("NPC");
    }
}
