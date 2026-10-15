package io.github.betamoon1.xptresor;

import io.github.betamoon1.xptresor.AccountStore.Account;
import io.github.betamoon1.xptresor.Services.Amount;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /xptresor (aliases /xpt, /tresor). Every subcommand has an English and a German name.
 */
final class TresorCommand implements TabExecutor {

    private enum Sub {
        HELP("help", "hilfe", "?"),
        DEPOSIT("deposit", "einzahlen", "ein", "d"),
        WITHDRAW("withdraw", "abheben", "ab", "w"),
        BALANCE("balance", "kontostand", "konto", "bal", "b"),
        TOP("top", "baltop", "rangliste"),
        SEND("send", "senden", "give", "pay", "geben"),
        AUTO("auto", "autodeposit"),
        REPAIR("repair", "reparieren", "rep"),
        BOTTLE("bottle", "flasche", "bot"),
        INFO("info", "i"),
        ADMIN("admin"),
        RELOAD("reload");

        final String[] names;

        Sub(String... names) {
            this.names = names;
        }

        static Sub of(String text) {
            String t = text.toLowerCase(Locale.ROOT);
            for (Sub s : values()) for (String n : s.names) if (n.equals(t)) return s;
            return null;
        }
    }

    private static final Map<Sub, String> PERMISSION = Map.ofEntries(
            Map.entry(Sub.DEPOSIT, "xptresor.deposit"),
            Map.entry(Sub.WITHDRAW, "xptresor.withdraw"),
            Map.entry(Sub.BALANCE, "xptresor.balance"),
            Map.entry(Sub.TOP, "xptresor.top"),
            Map.entry(Sub.SEND, "xptresor.send"),
            Map.entry(Sub.AUTO, "xptresor.auto"),
            Map.entry(Sub.REPAIR, "xptresor.repair"),
            Map.entry(Sub.BOTTLE, "xptresor.bottle"),
            Map.entry(Sub.INFO, "xptresor.admin.info"),
            Map.entry(Sub.ADMIN, "xptresor.admin.modify"),
            Map.entry(Sub.RELOAD, "xptresor.reload"));

    /** Names shown in tab completion when the language is German. */
    private static final Map<Sub, String> GERMAN = Map.of(
            Sub.DEPOSIT, "einzahlen", Sub.WITHDRAW, "abheben", Sub.BALANCE, "kontostand",
            Sub.SEND, "senden", Sub.REPAIR, "reparieren", Sub.BOTTLE, "flasche");

    private final XpTresor plugin;

    TresorCommand(XpTresor plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    private Services services() {
        return plugin.services();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("xptresor.balance")) {
                showOwnBalance(p);
                m().plain(p, "help.hint", "cmd", label);
            } else {
                help(sender, label);
            }
            return true;
        }
        Sub sub = Sub.of(args[0]);
        if (sub == null) {
            m().send(sender, "unknown-command", "cmd", label);
            return true;
        }
        if (sub == Sub.HELP) {
            help(sender, label);
            return true;
        }
        String permission = PERMISSION.get(sub);
        boolean importCmd = sub == Sub.ADMIN && args.length >= 2 && args[1].equalsIgnoreCase("import");
        if (importCmd) permission = "xptresor.admin.import";
        if (permission != null && !sender.hasPermission(permission)) {
            m().send(sender, "no-permission");
            return true;
        }

        switch (sub) {
            case BALANCE -> balance(sender, args, label);
            case TOP -> top(sender, args);
            case INFO -> info(sender, args, label);
            case ADMIN -> {
                if (importCmd) runImport(sender, args, label);
                else admin(sender, args, label);
            }
            case RELOAD -> {
                plugin.loadSettings();
                m().send(sender, "reload");
            }
            default -> {
                if (!(sender instanceof Player player)) {
                    m().send(sender, "players-only");
                    return true;
                }
                playerCommand(player, sub, args, label);
            }
        }
        return true;
    }

    private void playerCommand(Player player, Sub sub, String[] args, String label) {
        switch (sub) {
            case DEPOSIT -> {
                Amount a = args.length == 2 ? Amount.parse(args[1]) : null;
                if (a == null) m().send(player, "usage.deposit", "cmd", label);
                else services().deposit(player, a);
            }
            case WITHDRAW -> {
                Amount a = args.length == 2 ? Amount.parse(args[1]) : null;
                if (a == null) m().send(player, "usage.withdraw", "cmd", label);
                else services().withdraw(player, a);
            }
            case SEND -> {
                Amount a = args.length == 3 ? Amount.parse(args[2]) : null;
                if (a == null) m().send(player, "usage.send", "cmd", label);
                else services().send(player, args[1], a);
            }
            case AUTO -> auto(player, args, label);
            case REPAIR -> {
                boolean all = args.length == 2 && (args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("alle"));
                if (args.length > 2 || (args.length == 2 && !all)) m().send(player, "usage.repair", "cmd", label);
                else services().repair(player, all);
            }
            case BOTTLE -> {
                Amount a = args.length >= 2 && args.length <= 3 ? Amount.parse(args[1]) : null;
                int count = 1;
                if (a != null && args.length == 3) {
                    try {
                        count = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        a = null;
                    }
                }
                if (a == null) m().send(player, "bottle.usage", "cmd", label);
                else services().bottle(player, a, count);
            }
            default -> m().send(player, "unknown-command", "cmd", label);
        }
    }

    // ------------------------------------------------------------------ balance / top / info

    private void showOwnBalance(Player player) {
        Account a = plugin.store().getOrCreate(player.getUniqueId(), player.getName());
        long limit = plugin.settings().limitPoints(player);
        String limitText = limit == Long.MAX_VALUE ? m().raw(player, "words.unlimited")
                : m().raw(player, "words.levels", "levels", Xp.levelOf(limit));
        m().send(player, "balance.self", "levels", Xp.levelOf(a.xp()), "xp", Services.fmt(a.xp()));
        m().plain(player, "balance.self-details", "now", Xp.levelsAffordable(Xp.of(player), a.xp()), "limit", limitText);
    }

    private void balance(CommandSender sender, String[] args, String label) {
        if (args.length == 1) {
            if (sender instanceof Player p) showOwnBalance(p);
            else m().send(sender, "usage.balance", "cmd", label);
            return;
        }
        if (!sender.hasPermission("xptresor.balance.others")) {
            m().send(sender, "no-permission");
            return;
        }
        Account a = services().findAccount(args[1]);
        if (a == null) {
            m().send(sender, "player-unknown", "player", args[1]);
            return;
        }
        m().send(sender, "balance.other", "player", a.name(), "levels", Xp.levelOf(a.xp()), "xp", Services.fmt(a.xp()));
    }

    private void top(CommandSender sender, String[] args) {
        List<Account> top = plugin.store().top();
        if (top.isEmpty()) {
            m().send(sender, "top.empty");
            return;
        }
        int size = plugin.settings().topSize;
        int pages = (top.size() + size - 1) / size;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                // first page
            }
        }
        m().plain(sender, "top.header", "page", page, "pages", pages);
        for (int i = (page - 1) * size; i < Math.min(top.size(), page * size); i++) {
            Account a = top.get(i);
            m().plain(sender, "top.line", "rank", i + 1, "player", a.name(), "levels", Xp.levelOf(a.xp()),
                    "xp", Services.fmt(a.xp()));
        }
    }

    private void info(CommandSender sender, String[] args, String label) {
        if (args.length != 2) {
            m().send(sender, "usage.info", "cmd", label);
            return;
        }
        Account a = services().findAccount(args[1]);
        if (a == null) {
            m().send(sender, "player-unknown", "player", args[1]);
            return;
        }
        Player online = Bukkit.getPlayer(a.id());
        String limit;
        if (online == null) {
            long def = plugin.settings().defaultLimitPoints();
            limit = (def == Long.MAX_VALUE ? m().raw(sender, "words.unlimited") : m().raw(sender, "words.levels", "levels", Xp.levelOf(def)))
                    + " " + m().raw(sender, "words.offline-default");
        } else {
            long l = plugin.settings().limitPoints(online);
            limit = l == Long.MAX_VALUE ? m().raw(sender, "words.unlimited") : m().raw(sender, "words.levels", "levels", Xp.levelOf(l));
        }
        String yes = m().raw(sender, "words.positive");
        String no = m().raw(sender, "words.negative");
        m().plain(sender, "info.header", "player", a.name());
        m().plain(sender, "info.balance", "levels", Xp.levelOf(a.xp()), "xp", Services.fmt(a.xp()));
        m().plain(sender, "info.limit", "limit", limit);
        m().plain(sender, "info.auto", "state", a.autoDeposit() ? m().raw(sender, "words.enabled") : m().raw(sender, "words.disabled"),
                "level", Math.max(a.autoLevel(), plugin.settings().autoMinLevel));
        if (online != null) m().plain(sender, "info.keepxp", "state",
                plugin.settings().keepXpOnDeath && online.hasPermission("xptresor.keepxp") ? yes : no);
        m().plain(sender, "info.imported", "state", a.importedXpDeposit() ? yes : no);
        m().plain(sender, "info.uuid", "uuid", a.id());
    }

    // ------------------------------------------------------------------ auto deposit

    private void auto(Player player, String[] args, String label) {
        AccountStore store = plugin.store();
        Account a = store.getOrCreate(player.getUniqueId(), player.getName());
        int min = plugin.settings().autoMinLevel;
        int level = Math.max(a.autoLevel(), min);
        if (args.length == 1) {
            boolean on = !a.autoDeposit();
            store.setAuto(a, on, level);
            if (on) {
                m().send(player, "auto.enabled", "level", level);
                depositNowIfAbove(player);
            } else {
                m().send(player, "auto.disabled");
            }
            return;
        }
        String arg = args[1].toLowerCase(Locale.ROOT);
        switch (arg) {
            case "on", "an" -> {
                store.setAuto(a, true, level);
                m().send(player, "auto.enabled", "level", level);
                depositNowIfAbove(player);
            }
            case "off", "aus" -> {
                store.setAuto(a, false, a.autoLevel());
                m().send(player, "auto.disabled");
            }
            default -> {
                int wanted;
                try {
                    wanted = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    m().send(player, "usage.auto", "cmd", label);
                    return;
                }
                if (wanted < min) {
                    m().send(player, "auto.min", "min", min);
                    return;
                }
                store.setAuto(a, true, wanted);
                m().send(player, "auto.enabled", "level", wanted);
                depositNowIfAbove(player);
            }
        }
    }

    /** Switching auto-deposit on while already above the level deposits right away. */
    private void depositNowIfAbove(Player player) {
        Account a = plugin.store().get(player.getUniqueId());
        if (a == null) return;
        int threshold = Math.max(a.autoLevel(), plugin.settings().autoMinLevel);
        if (player.getLevel() >= threshold) services().autoDeposit(player, threshold - 1, player.getLevel());
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String[] args, String label) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        action = switch (action) {
            case "geben", "add" -> "give";
            case "nehmen", "remove" -> "take";
            case "setzen" -> "set";
            case "zuruecksetzen", "zurücksetzen", "clear" -> "reset";
            default -> action;
        };
        boolean needsAmount = action.equals("give") || action.equals("take") || action.equals("set");
        if (!(needsAmount && args.length == 4) && !(action.equals("reset") && args.length == 3)) {
            m().send(sender, "usage.admin", "cmd", label);
            return;
        }
        Account a = services().findAccount(args[2]);
        if (a == null) {
            m().send(sender, "player-unknown", "player", args[2]);
            return;
        }
        long points = 0;
        if (needsAmount) {
            if (action.equals("set") && args[3].equals("0")) {
                points = 0;
            } else {
                Amount amount = Amount.parse(args[3]);
                if (amount == null || amount.kind() == Amount.Kind.ALL) {
                    m().send(sender, "number-invalid");
                    return;
                }
                points = amount.kind() == Amount.Kind.LEVELS ? Xp.forLevel((int) amount.value()) : amount.value();
            }
        }
        AccountStore store = plugin.store();
        String by = sender instanceof Player p ? p.getName() : "Console";
        switch (action) {
            case "give" -> store.add(a, points);
            case "take" -> {
                if (!store.add(a, -points)) {
                    m().send(sender, "admin.not-enough", "player", a.name(), "xp", Services.fmt(a.xp()));
                    return;
                }
            }
            case "set" -> store.setXp(a, points);
            default -> store.setXp(a, 0);
        }
        Object[] ph = {"player", a.name(), "by", by, "xp", Services.fmt(points), "levels", Xp.levelOf(points),
                "balance", Xp.levelOf(a.xp()), "balancexp", Services.fmt(a.xp())};
        m().send(sender, "admin." + action, ph);
        Player target = Bukkit.getPlayer(a.id());
        if (target != null && target != sender) m().send(target, "admin.notify-" + action, ph);
    }

    private void runImport(CommandSender sender, String[] args, String label) {
        XpDepositImport importer = new XpDepositImport(plugin);
        List<XpDepositImport.Entry> entries = importer.read();
        if (entries.isEmpty()) {
            m().send(sender, "import.none", "folder", "plugins/XP-Deposit/users");
            return;
        }
        if (args.length < 3 || !(args[2].equalsIgnoreCase("confirm") || args[2].equalsIgnoreCase("bestaetigen"))) {
            long total = 0;
            int already = 0;
            for (XpDepositImport.Entry e : entries) {
                Account a = plugin.store().get(e.id());
                if (a != null && a.importedXpDeposit()) already++;
                else total += e.xp();
            }
            m().send(sender, "import.preview", "count", entries.size() - already, "already", already,
                    "xp", Services.fmt(total), "cmd", label);
            return;
        }
        XpDepositImport.Result r = importer.run(entries);
        m().send(sender, "import.done", "imported", r.imported(), "skipped", r.skipped(), "xp", Services.fmt(r.totalXp()));
    }

    // ------------------------------------------------------------------ help

    private void help(CommandSender sender, String label) {
        m().plain(sender, "help.header");
        String[][] lines = {
                {"xptresor.deposit", "help.deposit"},
                {"xptresor.withdraw", "help.withdraw"},
                {"xptresor.balance", "help.balance"},
                {"xptresor.top", "help.top"},
                {"xptresor.send", "help.send"},
                {"xptresor.auto", "help.auto"},
                {"xptresor.repair", "help.repair"},
                {"xptresor.bottle", "help.bottle"},
        };
        for (String[] l : lines) if (sender.hasPermission(l[0])) m().plain(sender, l[1], "cmd", label);
        m().plain(sender, "help.amounts");
        if (sender.hasPermission("xptresor.admin.info") || sender.hasPermission("xptresor.admin.modify")
                || sender.hasPermission("xptresor.admin.import") || sender.hasPermission("xptresor.reload")) {
            m().plain(sender, "help.admin-header");
            if (sender.hasPermission("xptresor.admin.info")) m().plain(sender, "help.info", "cmd", label);
            if (sender.hasPermission("xptresor.admin.modify")) m().plain(sender, "help.admin", "cmd", label);
            if (sender.hasPermission("xptresor.admin.import")) m().plain(sender, "help.import", "cmd", label);
            if (sender.hasPermission("xptresor.reload")) m().plain(sender, "help.reload", "cmd", label);
        }
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean german = m().languageOf(sender).startsWith("de");
        if (args.length == 1) {
            out.add(german ? "hilfe" : "help");
            for (Sub s : Sub.values()) {
                if (s == Sub.HELP) continue;
                String perm = PERMISSION.get(s);
                if (perm != null && !sender.hasPermission(perm)) continue;
                out.add(german ? GERMAN.getOrDefault(s, s.names[0]) : s.names[0]);
            }
            if (sender.hasPermission("xptresor.admin.import") && !out.contains("admin")) out.add("admin");
        } else {
            Sub sub = Sub.of(args[0]);
            if (sub == null) return out;
            switch (sub) {
                case DEPOSIT, WITHDRAW -> {
                    if (args.length == 2) out.addAll(List.of(german ? "alles" : "all", "1", "5", "10", "30"));
                }
                case SEND -> {
                    if (args.length == 2) players(out);
                    else if (args.length == 3) out.addAll(List.of("1", "5", "10", german ? "alles" : "all"));
                }
                case BALANCE, INFO -> {
                    if (args.length == 2 && (sub == Sub.INFO || sender.hasPermission("xptresor.balance.others"))) known(out);
                }
                case AUTO -> {
                    if (args.length == 2) out.addAll(german ? List.of("an", "aus", "30") : List.of("on", "off", "30"));
                }
                case REPAIR -> {
                    if (args.length == 2) out.add(german ? "alle" : "all");
                }
                case BOTTLE -> {
                    if (args.length == 2) out.addAll(List.of("1", "5", "10", "30"));
                    else if (args.length == 3) out.addAll(List.of("1", "16", "64"));
                }
                case ADMIN -> {
                    if (args.length == 2) {
                        if (sender.hasPermission("xptresor.admin.modify")) out.addAll(List.of("give", "take", "set", "reset"));
                        if (sender.hasPermission("xptresor.admin.import")) out.add("import");
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("import")) {
                        out.add("confirm");
                    } else if (args.length == 3) {
                        known(out);
                    } else if (args.length == 4) {
                        out.addAll(List.of("10", "30", "100", "500p"));
                    }
                }
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }

    private static void players(List<String> out) {
        for (Player p : Bukkit.getOnlinePlayers()) if (!XpTresor.isBot(p)) out.add(p.getName());
    }

    private void known(List<String> out) {
        out.addAll(plugin.store().names());
    }
}
