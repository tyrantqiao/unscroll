package com.unscroll;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Store {
    public final SharedPreferences prefs;

    public Store(Context context) {
        prefs = context.getSharedPreferences("unscroll", Context.MODE_PRIVATE);
        // Migrate rules written by v0.1, including rules that are disabled.
        Set<String> packages = configuredPackages();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("enabled") && entry.getValue() instanceof Boolean) {
                packages.add(key.substring(0, key.length() - "enabled".length()));
            }
        }
        prefs.edit().putStringSet("rulePackages", packages).apply();
    }

    public Set<String> configuredPackages() {
        return new HashSet<>(prefs.getStringSet("rulePackages", new HashSet<>()));
    }

    /** Calendar days in the device timezone, oldest first, including today. */
    public static java.util.List<String> recentDays(int count, java.util.Date now) {
        if (count < 1 || count > 366) throw new IllegalArgumentException("Invalid day count");
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1 - count);
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
        java.util.List<String> dates = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            dates.add(format.format(calendar.getTime()));
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return dates;
    }

    public long monitoredTime(String packageName, java.util.List<String> dates) {
        long total = 0;
        for (String date : dates) total += Math.max(0, prefs.getLong(packageName + "today" + date, 0));
        return total;
    }

    public int value(String packageName, String key, int fallback) {
        return prefs.getInt(packageName + key, fallback);
    }

    public boolean enabled(String packageName) {
        return prefs.getBoolean(packageName + "enabled", false);
    }

    public void setEnabled(String packageName, boolean enabled) {
        Set<String> packages = configuredPackages();
        packages.add(packageName);
        SharedPreferences.Editor editor = prefs.edit()
                .putStringSet("rulePackages", packages)
                .putBoolean(packageName + "enabled", enabled);
        if (!enabled) clearSession(editor, packageName);
        editor.apply();
    }

    private void clearSession(SharedPreferences.Editor editor, String packageName) {
        editor.putLong(packageName + "used", 0)
                .putLong(packageName + "blocked", 0)
                .putLong(packageName + "away", -1)
                .putBoolean(packageName + "warned", false)
                .putBoolean(packageName + "lockDone", false);
    }

    public void save(String packageName, int warn, int stop, int rest,
                     boolean lock, boolean enabled) {
        Set<String> packages = configuredPackages();
        packages.add(packageName);
        SharedPreferences.Editor editor = prefs.edit()
                .putStringSet("rulePackages", packages)
                .putInt(packageName + "warn", warn)
                .putInt(packageName + "stop", stop)
                .putInt(packageName + "rest", rest)
                .putBoolean(packageName + "lock", lock)
                .putBoolean(packageName + "enabled", enabled);
        if (!enabled) clearSession(editor, packageName);
        editor.apply();
    }
    public boolean continuous(String p) { return prefs.getBoolean(p + "continuousRule", true); }
    public boolean daily(String p) { return prefs.getBoolean(p + "dailyRule", false); }

    public UsagePolicy.Rules rules(String p) {
        UsagePolicy.Rules r = new UsagePolicy.Rules();
        r.continuous = continuous(p); r.daily = daily(p);
        r.warn = value(p, "warn", 15) * UsagePolicy.MINUTE;
        r.stop = value(p, "stop", 20) * UsagePolicy.MINUTE;
        r.rest = value(p, "rest", 5) * UsagePolicy.MINUTE;
        r.dailyWarn = value(p, "dailyWarn", 45) * UsagePolicy.MINUTE;
        r.dailyStop = value(p, "dailyStop", 60) * UsagePolicy.MINUTE;
        return r;
    }

    public void saveRules(String p, int warn, int stop, int rest, boolean lock,
                          boolean enabled, boolean continuous, boolean daily,
                          int dailyWarn, int dailyStop) {
        save(p, warn, stop, rest, lock, enabled);
        prefs.edit().putBoolean(p + "continuousRule", continuous)
                .putBoolean(p + "dailyRule", daily)
                .putInt(p + "dailyWarn", dailyWarn)
                .putInt(p + "dailyStop", dailyStop).apply();
    }
}
