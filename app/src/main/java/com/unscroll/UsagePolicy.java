package com.unscroll;

/** Pure policy, independent of Android. Durations use monotonic milliseconds. */
public final class UsagePolicy {
    public static final long MINUTE = 60000L;
    private static final int[] MILESTONES = {10, 30, 60};
    public final Session session = new Session();
    public long total;
    public boolean dailyWarned;
    public int milestones;
    public String day = "";

    public static final class Rules {
        public boolean continuous = true, daily;
        public long warn = 15 * MINUTE, stop = 20 * MINUTE, rest = 5 * MINUTE;
        public long dailyWarn = 45 * MINUTE, dailyStop = 60 * MINUTE;
    }
    public enum Kind { NONE, MILESTONE, CONTINUOUS_REMINDER, DAILY_REMINDER, CONTINUOUS_BLOCK, DAILY_BLOCK }
    public static final class Result {
        public final Kind kind;
        public final int minutes;
        public final long counted;
        Result(Kind kind, int minutes, long counted) {
            this.kind = kind; this.minutes = minutes; this.counted = counted;
        }
        public boolean blocked() {return kind == Kind.CONTINUOUS_BLOCK || kind == Kind.DAILY_BLOCK;}
    }

    public void day(String next, long storedTotal, boolean storedWarned) {
        if (!day.equals(next)) {
            day = next; total = storedTotal; dailyWarned = storedWarned;
        }
    }

    public Result advance(long now, long delta, boolean active, Rules rules) {
        return advance(now, delta, delta, active, rules);
    }

    public Result advance(long now, long delta, long dailyDelta, boolean active, Rules rules) {
        if (!rules.continuous) session.blockedUntil = 0;
        if (session.blockedUntil > 0 && now >= session.blockedUntil) {
            session.blockedUntil = 0; resetContinuous();
        }
        if (!active) {
            resetContinuous();
            return new Result(Kind.NONE, 0, 0);
        }
        if (rules.daily && total >= rules.dailyStop) return new Result(Kind.DAILY_BLOCK, 0, 0);
        if (rules.continuous && now < session.blockedUntil) return new Result(Kind.CONTINUOUS_BLOCK, 0, 0);
        long counted = Math.max(0, delta);
        total += Math.max(0, Math.min(counted, dailyDelta));
        session.used += counted;
        boolean dailyReminder = rules.daily && !dailyWarned && total >= rules.dailyWarn;
        boolean continuousReminder = rules.continuous && !session.warned && session.used >= rules.warn;
        if (dailyReminder) dailyWarned = true;
        if (continuousReminder) session.warned = true;
        int milestone = 0;
        if (rules.continuous) {
            for (int i = 0; i < MILESTONES.length; i++) {
                if (session.used >= MILESTONES[i] * MINUTE && (milestones & (1 << i)) == 0) {
                    milestones |= 1 << i;
                    milestone = MILESTONES[i];
                }
            }
        }
        if (rules.daily && total >= rules.dailyStop) return new Result(Kind.DAILY_BLOCK, 0, counted);
        if (rules.continuous && session.used >= rules.stop) {
            session.blockedUntil = now + rules.rest;
            return new Result(Kind.CONTINUOUS_BLOCK, 0, counted);
        }
        if (dailyReminder) return new Result(Kind.DAILY_REMINDER, 0, counted);
        if (continuousReminder) return new Result(Kind.CONTINUOUS_REMINDER, 0, counted);
        return new Result(milestone == 0 ? Kind.NONE : Kind.MILESTONE, milestone, counted);
    }

    public void resetContinuous() {
        session.used = 0; session.warned = false; session.awaySince = -1; milestones = 0;
    }
}
