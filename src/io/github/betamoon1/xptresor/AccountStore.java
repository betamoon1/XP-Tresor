package io.github.betamoon1.xptresor;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
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

    /** Why the balance changed - shown in /xpt history. */
    public enum Reason {
        DEPOSIT, WITHDRAW, SEND, RECEIVE, AUTO_DEPOSIT, AUTOPAY_ENCHANT, AUTOPAY_ANVIL, REPAIR, BOTTLE,
        ADMIN_GIVE, ADMIN_TAKE, ADMIN_SET, ADMIN_RESET, IMPORT, ECONOMY;

        /** Message key: history.reason.auto-deposit */
        String key() {
            return "history.reason." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        static Reason parse(String s) {
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** One line of the history. points: positive = into the vault, negative = out of it. other: player or null. */
    public record Entry(long time, Reason reason, long points, String other) {
    }

    public static final class Account {
        private final UUID id;
        private String name;
        private long xp;
        private boolean autoDeposit;
        private int autoLevel;
        private boolean importedXpDeposit;
        /** null = server default from the config. */
        private Boolean autoPay;
        /** XP currently lent to the player for enchanting/anvil (see AutoPay). */
        private long loan;
        private final Deque<Entry> history = new ArrayDeque<>();

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

        public Boolean autoPay() {
            return autoPay;
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
            if (yml.isBoolean("auto-pay")) a.autoPay = yml.getBoolean("auto-pay");
            long loan = yml.getLong("auto-pay-loan", 0);
            for (String line : yml.getStringList("history")) {
                Entry e = parse(line);
                if (e != null) a.history.addLast(e);
            }
            put(a);
            if (loan > 0) {
                // the server stopped while an enchanting table was open: the player's XP (saved earlier)
                // doesn't have the lent levels, so they go back into the vault
                a.xp += loan;
                save(a);
                plugin.getLogger().info("Returned " + loan + " lent XP to the vault of " + a.name);
            }
        }
    }

    /** "time;REASON;points;other" */
    private static Entry parse(String line) {
        String[] p = line.split(";", 4);
        if (p.length < 3) return null;
        try {
            Reason r = Reason.parse(p[1]);
            if (r == null) return null;
            String other = p.length == 4 && !p[3].isEmpty() ? p[3] : null;
            return new Entry(Long.parseLong(p[0]), r, Long.parseLong(p[2]), other);
        } catch (NumberFormatException e) {
            return null;
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

    /** Sets the balance and writes the change into the history. */
    public synchronized void setXp(Account a, long xp, Reason reason, String other) {
        long before = a.xp;
        a.xp = Math.max(0, xp);
        log(a, reason, a.xp - before, other);
        save(a);
    }

    /**
     * Adds (or with a negative value removes) points and writes it into the history.
     * Returns false if the balance would become negative.
     */
    public synchronized boolean add(Account a, long delta, Reason reason, String other) {
        long next = a.xp + delta;
        if (next < 0) return false;
        if (delta > 0 && next < a.xp) next = Long.MAX_VALUE; // overflow
        long change = next - a.xp;
        a.xp = next;
        if (reason != null) log(a, reason, change, other);
        save(a);
        return true;
    }

    /**
     * Lends XP to the player (positive) or takes lent XP back (negative). The open loan is saved with the
     * account, so a crash while the enchanting table is open can never lose it.
     */
    synchronized boolean moveLoan(Account a, long toPlayer) {
        if (toPlayer > 0 && a.xp < toPlayer) return false;
        long back = Math.min(-toPlayer, a.loan);
        if (toPlayer > 0) {
            a.xp -= toPlayer;
            a.loan += toPlayer;
        } else {
            a.xp += back;
            a.loan -= back;
        }
        save(a);
        return true;
    }

    /** Ends a loan: {@code back} points return to the vault, the rest was spent. */
    synchronized void closeLoan(Account a, long back) {
        a.xp += Math.max(0, back);
        a.loan = 0;
        save(a);
    }

    /** Moves points between two accounts in one step. */
    public synchronized boolean transfer(Account from, Account to, long points) {
        if (points <= 0 || from.xp < points) return false;
        from.xp -= points;
        to.xp += points;
        log(from, Reason.SEND, -points, to.name);
        log(to, Reason.RECEIVE, points, from.name);
        save(from);
        save(to);
        return true;
    }

    public synchronized void setAuto(Account a, boolean enabled, int level) {
        a.autoDeposit = enabled;
        a.autoLevel = level;
        save(a);
    }

    public synchronized void setAutoPay(Account a, boolean enabled) {
        a.autoPay = enabled;
        save(a);
    }

    synchronized void markImported(Account a, long xp) {
        long before = a.xp;
        a.xp = xp;
        a.importedXpDeposit = true;
        log(a, Reason.IMPORT, xp - before, null);
        save(a);
    }

    /** Adds a history line (only when something changed). */
    public synchronized void log(Account a, Reason reason, long points, String other) {
        int max = plugin.settings().historyMax;
        if (max <= 0 || points == 0) return;
        a.history.addFirst(new Entry(System.currentTimeMillis(), reason, points, other));
        while (a.history.size() > max) a.history.removeLast();
    }

    /** Adds a history line and writes the account. */
    synchronized void logAndSave(Account a, Reason reason, long points, String other) {
        log(a, reason, points, other);
        save(a);
    }

    /** Newest first. */
    public synchronized List<Entry> history(Account a) {
        return new ArrayList<>(a.history);
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
        if (a.autoPay != null) yml.set("auto-pay", a.autoPay);
        if (a.loan > 0) yml.set("auto-pay-loan", a.loan);
        if (a.importedXpDeposit) yml.set("imported-from-xp-deposit", true);
        if (!a.history.isEmpty()) {
            List<String> lines = new ArrayList<>(a.history.size());
            for (Entry e : a.history) {
                String other = e.other() == null ? "" : e.other().replace(';', '_');
                lines.add(e.time() + ";" + e.reason().name() + ";" + e.points() + ";" + other);
            }
            yml.set("history", lines);
        }
        try {
            folder.mkdirs();
            yml.save(new File(folder, a.id + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save the account of " + a.name + " (" + a.id + ")", e);
        }
    }
}
