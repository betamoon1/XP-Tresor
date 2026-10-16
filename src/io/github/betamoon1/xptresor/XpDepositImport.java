package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.AccountStore.Account;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads the account files of the old XP-Deposit plugin (plugins/XP-Deposit/users/&lt;uuid&gt;.yml) so a
 * server can switch without anyone losing stored XP. Each account is imported only once.
 */
final class XpDepositImport {

    record Entry(UUID id, String name, long xp, boolean autoOn, int autoLevel) {
    }

    record Result(int imported, int skipped, long totalXp) {
    }

    private final XpTresor plugin;

    XpDepositImport(XpTresor plugin) {
        this.plugin = plugin;
    }

    File folder() {
        return new File(plugin.getDataFolder().getParentFile(), "XP-Deposit/users");
    }

    /** All readable old accounts. */
    List<Entry> read() {
        List<Entry> entries = new ArrayList<>();
        File[] files = folder().listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return entries;
        for (File file : files) {
            UUID id;
            try {
                id = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
            } catch (IllegalArgumentException e) {
                continue;
            }
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            // the file has one top-level section named after the player
            for (String name : yml.getKeys(false)) {
                ConfigurationSection sec = yml.getConfigurationSection(name);
                if (sec == null) continue;
                long xp = Math.max(0, sec.getLong("experience", 0));
                if (sec.contains("XP-Balance")) xp = Math.max(xp, Xp.forLevel(sec.getInt("XP-Balance"))); // very old format: levels
                entries.add(new Entry(id, name, xp, sec.getBoolean("auto-deposit.toggle", false),
                        sec.getInt("auto-deposit.deposit-number", 0)));
                break;
            }
        }
        return entries;
    }

    /** Adds the old balances to the accounts (once per account). */
    Result run(List<Entry> entries) {
        AccountStore store = plugin.store();
        int imported = 0;
        int skipped = 0;
        long total = 0;
        for (Entry e : entries) {
            boolean existed = store.exists(e.id());
            Account account = store.getOrCreate(e.id(), e.name());
            if (account.importedXpDeposit()) {
                skipped++;
                continue;
            }
            // new accounts start with the old balance only (no extra starting balance)
            long sum = (existed ? account.xp() : 0) + e.xp();
            if (sum < account.xp()) sum = Long.MAX_VALUE;
            store.markImported(account, sum);
            if (e.autoLevel() > 0 && account.autoLevel() == 0) store.setAuto(account, e.autoOn(), e.autoLevel());
            imported++;
            total += e.xp();
        }
        return new Result(imported, skipped, total);
    }
}
