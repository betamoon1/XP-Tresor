package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.AccountStore.Account;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.util.List;

/**
 * Optional: the XP storage as Vault economy. The currency is either levels (counted from level 0,
 * like an XP bottle) or raw XP points - see vault-economy.unit in the config.
 * Only loaded when Vault is installed.
 */
@SuppressWarnings("deprecation")
final class VaultEconomy implements Economy {

    private final XpTresor plugin;

    private VaultEconomy(XpTresor plugin) {
        this.plugin = plugin;
    }

    static void register(XpTresor plugin) {
        Bukkit.getServicesManager().register(Economy.class, new VaultEconomy(plugin), plugin, ServicePriority.Normal);
    }

    private boolean points() {
        return plugin.settings().vaultInPoints;
    }

    private long toPoints(double amount) {
        if (amount <= 0) return 0;
        return points() ? (long) Math.floor(amount) : Xp.forLevel((int) Math.min(100_000, Math.round(amount)));
    }

    private double toCurrency(long xp) {
        return points() ? xp : Xp.levelOf(xp);
    }

    private Account account(OfflinePlayer player) {
        return player == null ? null : plugin.store().get(player.getUniqueId());
    }

    private Account account(String name) {
        return name == null ? null : plugin.store().byName(name);
    }

    // ------------------------------------------------------------------ general

    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public String getName() { return "XpTresor"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }
    @Override public String format(double amount) {
        long v = (long) Math.floor(amount);
        return Services.fmt(v) + " " + (v == 1 ? currencyNameSingular() : currencyNamePlural());
    }
    @Override public String currencyNamePlural() { return points() ? "XP" : "Level"; }
    @Override public String currencyNameSingular() { return points() ? "XP" : "Level"; }

    // ------------------------------------------------------------------ accounts

    @Override public boolean hasAccount(String playerName) { return account(playerName) != null; }
    @Override public boolean hasAccount(OfflinePlayer player) { return account(player) != null; }
    @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }

    @Override public boolean createPlayerAccount(String playerName) {
        return account(playerName) != null;
    }
    @Override public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) return false;
        plugin.store().getOrCreate(player.getUniqueId(), player.getName());
        return true;
    }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return createPlayerAccount(playerName); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return createPlayerAccount(player); }

    // ------------------------------------------------------------------ balance

    @Override public double getBalance(String playerName) { return balance(account(playerName)); }
    @Override public double getBalance(OfflinePlayer player) { return balance(account(player)); }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    private double balance(Account a) {
        return a == null ? 0 : toCurrency(a.xp());
    }

    @Override public boolean has(String playerName, double amount) { return has(account(playerName), amount); }
    @Override public boolean has(OfflinePlayer player, double amount) { return has(account(player), amount); }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

    private boolean has(Account a, double amount) {
        return a != null && a.xp() >= toPoints(amount);
    }

    // ------------------------------------------------------------------ withdraw / deposit

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return withdraw(account(playerName), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) { return withdraw(account(player), amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }

    private EconomyResponse withdraw(Account a, double amount) {
        if (a == null) return new EconomyResponse(0, 0, ResponseType.FAILURE, "No account");
        if (amount < 0) return new EconomyResponse(0, balance(a), ResponseType.FAILURE, "Negative amount");
        long points = toPoints(amount);
        if (!plugin.store().add(a, -points)) {
            return new EconomyResponse(0, balance(a), ResponseType.FAILURE, "Not enough XP");
        }
        return new EconomyResponse(amount, balance(a), ResponseType.SUCCESS, null);
    }

    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return deposit(account(playerName), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player != null && account(player) == null) createPlayerAccount(player);
        return deposit(account(player), amount);
    }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

    private EconomyResponse deposit(Account a, double amount) {
        if (a == null) return new EconomyResponse(0, 0, ResponseType.FAILURE, "No account");
        if (amount < 0) return new EconomyResponse(0, balance(a), ResponseType.FAILURE, "Negative amount");
        plugin.store().add(a, toPoints(amount));
        return new EconomyResponse(amount, balance(a), ResponseType.SUCCESS, null);
    }

    // ------------------------------------------------------------------ banks (not supported)

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "XpTresor has no banks");
    }

    @Override public EconomyResponse createBank(String name, String player) { return noBanks(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return noBanks(); }
    @Override public EconomyResponse deleteBank(String name) { return noBanks(); }
    @Override public EconomyResponse bankBalance(String name) { return noBanks(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return noBanks(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return noBanks(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return noBanks(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return noBanks(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return noBanks(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return noBanks(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return noBanks(); }
    @Override public List<String> getBanks() { return List.of(); }
}
