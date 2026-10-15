package io.github.betamoon1.xptresor;

import org.bukkit.entity.Player;

/**
 * Experience math, based on the vanilla formulas:
 * points needed for the next level: 2L+7 (L 0-15), 5L-38 (L 16-30), 9L-158 (L 31+).
 */
public final class Xp {

    private Xp() {
    }

    /** Points needed to get from level {@code level} to {@code level + 1}. */
    public static long toNext(int level) {
        if (level < 0) return 0;
        if (level <= 15) return 2L * level + 7;
        if (level <= 30) return 5L * level - 38;
        return 9L * level - 158;
    }

    /** Total points of a player who is exactly at the start of {@code level}. */
    public static long forLevel(int level) {
        if (level <= 0) return 0;
        long l = level;
        if (l <= 16) return l * l + 6 * l;
        if (l <= 31) return (5 * l * l - 81 * l) / 2 + 360;
        return (9 * l * l - 325 * l) / 2 + 2220;
    }

    /** The level a player with {@code points} total points is at. */
    public static int levelOf(long points) {
        if (points <= 0) return 0;
        int lo = 0;
        int hi = 1;
        while (forLevel(hi) <= points) {
            lo = hi;
            if (hi >= 1 << 29) return hi;
            hi <<= 1;
        }
        // forLevel(lo) <= points < forLevel(hi)
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (forLevel(mid) <= points) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    /** Total points of the player (level plus the progress bar). */
    public static long of(Player player) {
        int level = Math.max(0, player.getLevel());
        float bar = Math.max(0f, Math.min(1f, player.getExp()));
        return forLevel(level) + Math.round(bar * toNext(level));
    }

    /** Sets the player's experience to exactly {@code points} total points. */
    public static void set(Player player, long points) {
        points = Math.max(0, points);
        int level = levelOf(points);
        long rest = points - forLevel(level);
        float bar = (float) ((double) rest / (double) toNext(level));
        player.setLevel(level);
        player.setExp(Math.max(0f, Math.min(0.9999f, bar)));
        player.setTotalExperience((int) Math.min(Integer.MAX_VALUE, points));
    }

    /** Points worth the top {@code levels} levels of a player that has {@code points} points. */
    public static long topLevels(long points, int levels) {
        int level = levelOf(points);
        if (levels >= level) return points;
        return forLevel(level) - forLevel(level - levels);
    }

    /** Points needed to raise a player with {@code points} by {@code levels} levels. */
    public static long nextLevels(long points, int levels) {
        int level = levelOf(points);
        long progress = points - forLevel(level);
        long target = forLevel(level + levels) + progress;
        // the progress always fits into the higher level's bar (bars only get longer)
        return target - points;
    }

    /** How many levels a player with {@code points} could gain with {@code budget} points. */
    public static int levelsAffordable(long points, long budget) {
        int lo = 0;
        int hi = Math.max(1, levelOf(points + budget) - levelOf(points) + 1);
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (nextLevels(points, mid) <= budget) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }
}
