import io.github.betamoon1.xptresor.Xp;
import io.github.betamoon1.xptresor.Services.Amount;
public class XpTest {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }
    public static void main(String[] a) {
        check(Xp.forLevel(16) == 352 && Xp.forLevel(17) == 394 && Xp.forLevel(30) == 1395 && Xp.forLevel(31) == 1507 && Xp.forLevel(32) == 1628, "known values");
        for (int l = 0; l < 30000; l++) {
            check(Xp.forLevel(l + 1) - Xp.forLevel(l) == Xp.toNext(l), "step " + l);
            check(Xp.levelOf(Xp.forLevel(l)) == l, "levelOf exact " + l);
            check(Xp.levelOf(Xp.forLevel(l + 1) - 1) == l, "levelOf below " + l);
        }
        // deposit 10 levels at level 50 (+ some progress) -> level 40 with same progress points
        long p = Xp.forLevel(50) + 37;
        long dep = Xp.topLevels(p, 10);
        check(Xp.levelOf(p - dep) == 40 && (p - dep) - Xp.forLevel(40) == 37, "topLevels");
        // withdraw: from level 40 + 37 gain 10 -> level 50 + 37
        long w = Xp.nextLevels(Xp.forLevel(40) + 37, 10);
        check(Xp.levelOf(Xp.forLevel(40) + 37 + w) == 50, "nextLevels");
        // affordable
        long have = Xp.forLevel(12) + 5;
        for (long budget : new long[]{0, 1, 50, 500, 5000, 123456}) {
            int n = Xp.levelsAffordable(have, budget);
            check(Xp.nextLevels(have, n) <= budget && Xp.nextLevels(have, n + 1) > budget, "affordable " + budget + " -> " + n);
        }
        check(Amount.parse("all").kind() == Amount.Kind.ALL && Amount.parse("alles") != null, "all");
        check(Amount.parse("10").kind() == Amount.Kind.LEVELS && Amount.parse("10").value() == 10, "levels");
        check(Amount.parse("500p").kind() == Amount.Kind.POINTS && Amount.parse("500xp").value() == 500, "points");
        check(Amount.parse("0") == null && Amount.parse("-5") == null && Amount.parse("abc") == null && Amount.parse("999999") == null, "invalid");
        System.out.println("level 30 = " + Xp.forLevel(30) + " XP, level 100 = " + Xp.forLevel(100) + " XP, 120 = " + Xp.forLevel(120));
        System.out.println("all tests passed");
    }
}
