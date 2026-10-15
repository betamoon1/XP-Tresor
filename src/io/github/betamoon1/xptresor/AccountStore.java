package io.github.betamoon1.xptresor;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * All accounts, kept in memory and written to plugins/XpTresor/players/&lt;uuid&gt;.yml on every change
 * (one small file per player, so nothing is lost on a crash).
 */
public final class AccountStore {

    public static final class Account {
        private final UUID id;
        private String name;
        private long xp;
        private boolean autoDeposit;
        private int autoLevel;
        private boolean importedXpDeposit;

        Account(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public long xp() {
            return xp;
        }

        public boolean autoDeposit() {
            return autoDeposit;
        }

        public int autoLevel() {
            return autoLevel;
        }

        public boolean importedXpDeposit() {
            return importedXpDeposit;
        }
    }

    private final XpTresor plugin;
    private final File folder;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    AccountStore(XpTresor plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
    }

    synchronized void loadAll() {
        accounts.clear();
        byName.clear();
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String idPart = file.getName().substring(0, file.getName().length() - 4);
            UUID id;
            try {
                id = UUID.fromString(idPart);
            } catch (IllegalArgumentException e) {
                continue;
            }
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            Account a = new Account(id, yml.getString("name", idPart));
            a.xp = Math.max(0, yml.getLong("xp", 0));
            a.autoDeposit = yml.getBoolean("auto-deposit.enabled", false);
            a.autoLevel = yml.getInt("auto-deposit.level", 0);
            a.importedXpDeposit = yml.getBoolean("imported-from-xp-deposit", false);
            put(a);
        }
    }

    private void put(Account a) {
        accounts.put(a.id, a);
        if (a.name != null) byName.put(a.name.toLowerCase(Locale.ROOT), a.id);
    }

    public synchronized Account get(UUID id) {
        return accounts.get(id);
    }

    public synchronized Account byName(String name) {
        UUID id = byName.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : accounts.get(id);
    }

    public synchronized boolean exists(UUID id) {
        return accounts.containsKey(id);
    }

    /** Returns the account, creating it with the starting balance if needed. Updates the stored name. */
    public synchronized Account getOrCreate(UUID id, String name) {
        Account a = accounts.get(id);
        if (a == null) {
            a = new Account(id, name);
            a.xp = Xp.forLevel(plugin.settings().startLevels);
            put(a);
            save(a);
            return a;
        }
        if (name != null && !name.equals(a.name)) {
            if (a.name != null) byName.remove(a.name.toLowerCase(Locale.ROOT));
            a.name = name;
            byName.put(name.toLowerCase(Locale.ROOT), id);
            save(a);
        }
        return a;
    }

    public synchronized void setXp(Account a, long xp) {
        a.xp = Math.max(0, xp);
        save(a);
    }

    /** Adds (or with a negative value removes) points. Returns false if the balance would become negative. */
    public synchronized boolean add(Account a, long delta) {
        long next = a.xp + delta;
        if (next < 0) return false;
        if (delta > 0 && next < a.xp) next = Long.MAX_VALUE; // overflow
        a.xp = next;
        save(a);
        return true;
    }

    /** Moves points between two accounts in one step. */
    public synchronized boolean transfer(Account from, Account to, long points) {
        if (points <= 0 || from.xp < points) return false;
        from.xp -= points;
        to.xp += points;
        save(from);
        save(to);
        return true;
    }

    public synchronized void setAuto(Account a, boolean enabled, int level) {
        a.autoDeposit = enabled;
        a.autoLevel = level;
        save(a);
    }

    synchronized void markImported(Account a, long xp) {
        a.xp = xp;
        a.importedXpDeposit = true;
        save(a);
    }

    public synchronized List<Account> top() {
        List<Account> list = new ArrayList<>(accounts.values());
        list.removeIf(a -> a.xp <= 0 || plugin.settings().hiddenInTop.contains(a.name == null ? "" : a.name.toLowerCase(Locale.ROOT)));
        list.sort(Comparator.comparingLong((Account a) -> a.xp).reversed().thenComparing(a -> a.name == null ? "" : a.name));
        return list;
    }

    public synchronized Collection<String> names() {
        List<String> names = new ArrayList<>();
        for (Account a : accounts.values()) if (a.name != null) names.add(a.name);
        return names;
    }

    private void save(Account a) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("name", a.name);
        yml.set("xp", a.xp);
        yml.set("auto-deposit.enabled", a.autoDeposit);
        yml.set("auto-deposit.level", a.autoLevel);
        if (a.importedXpDeposit) yml.set("imported-from-xp-deposit", true);
        try {
            folder.mkdirs();
            yml.save(new File(folder, a.id + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the account of " + a.name + " (" + a.id + ")", e);
        }
    }
}
