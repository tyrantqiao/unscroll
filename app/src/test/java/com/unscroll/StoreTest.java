package com.unscroll;

import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class StoreTest {
    private static final class Memory {
        final Map<String, Object> data = new HashMap<>();
        final SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("edit")) return editor();
                    if (name.equals("getAll")) return new HashMap<>(data);
                    if (name.startsWith("get")) return data.getOrDefault(args[0], args[1]);
                    if (name.equals("contains")) return data.containsKey(args[0]);
                    return null;
                });

        Object editor() {
            Map<String, Object> pending = new HashMap<>();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.startsWith("put")) {
                            Object value = args[1];
                            if (value instanceof java.util.Set) value = new HashSet<>((java.util.Set<?>) value);
                            pending.put((String) args[0], value);
                            return proxy;
                        }
                        if (name.equals("apply") || name.equals("commit")) {
                            data.putAll(pending);
                            return name.equals("commit") ? Boolean.TRUE : null;
                        }
                        return proxy;
                    });
        }

        Store open() {
            return new Store(new ContextWrapper(null) {
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    return prefs;
                }
            });
        }
    }

    @Test public void arbitraryAppsKeepIndependentRulesAcrossReopen() {
        Memory memory = new Memory();
        Store store = memory.open();
        store.save("example.reader", 5, 10, 2, false, true);
        store.save("example.video", 15, 30, 8, true, false);
        Store reopened = memory.open();
        assertEquals(2, reopened.configuredPackages().size());
        assertEquals(10, reopened.value("example.reader", "stop", 0));
        assertEquals(30, reopened.value("example.video", "stop", 0));
        assertTrue(reopened.enabled("example.reader"));
        assertFalse(reopened.enabled("example.video"));
        assertTrue(reopened.prefs.getBoolean("example.video" + "lock", false));
    }

    @Test public void oldRulesMigrateWithoutChangingValues() {
        Memory memory = new Memory();
        memory.data.put("legacy.appenabled", true);
        memory.data.put("legacy.appwarn", 7);
        memory.data.put("disabled.appenabled", false);
        Store migrated = memory.open();
        assertTrue(migrated.configuredPackages().contains("legacy.app"));
        assertTrue(migrated.configuredPackages().contains("disabled.app"));
        assertEquals(7, migrated.value("legacy.app", "warn", 15));
        assertTrue(migrated.enabled("legacy.app"));
        assertEquals(2, memory.open().configuredPackages().size());
    }

    @Test public void disablingClearsInterruptionButKeepsConfiguration() {
        Memory memory = new Memory();
        Store store = memory.open();
        store.save("example.game", 3, 9, 4, true, true);
        memory.data.put("example.gameblocked", 99999L);
        memory.data.put("example.gameused", 900L);
        store.setEnabled("example.game", false);
        Store reopened = memory.open();
        assertEquals(0, reopened.prefs.getLong("example.gameblocked", -1));
        assertEquals(0, reopened.prefs.getLong("example.gameused", -1));
        assertEquals(9, reopened.value("example.game", "stop", 0));
        reopened.setEnabled("example.game", true);
        assertTrue(reopened.enabled("example.game"));
        assertEquals(4, reopened.value("example.game", "rest", 0));
    }

    @Test public void newAppsAreOffAndReturnedPackageSetIsDetached() {
        Memory memory = new Memory();
        Store store = memory.open();
        assertFalse(store.enabled("new.app"));
        store.setEnabled("new.app", true);
        store.configuredPackages().clear();
        assertTrue(store.configuredPackages().contains("new.app"));
    }
    @Test public void bothRuleConfigurationsPersistIndependently() {
        Memory memory = new Memory(); Store store = memory.open();
        store.saveRules("example.app", 10, 90, 5, false, true, true, true, 100, 180);
        Store reopened = memory.open(); UsagePolicy.Rules rules = reopened.rules("example.app");
        assertTrue(rules.continuous); assertTrue(rules.daily);
        assertEquals(90 * UsagePolicy.MINUTE, rules.stop);
        assertEquals(180 * UsagePolicy.MINUTE, rules.dailyStop);
        store.saveRules("example.app", 10, 90, 5, false, true, false, true, 100, 180);
        assertFalse(memory.open().continuous("example.app"));
        assertTrue(memory.open().daily("example.app"));
    }

    @Test public void legacyRulesKeepContinuousEnabledWithoutAddingDailyLimit() {
        Memory memory = new Memory(); memory.data.put("old.appenabled", true);
        Store store = memory.open();
        assertTrue(store.continuous("old.app")); assertFalse(store.daily("old.app"));
        assertEquals(1, store.configuredPackages().size());
    }

    @Test public void statisticsUseOnlyRequestedDatesAndKeepAppTotalsIndependent() {
        Memory memory = new Memory(); Store store = memory.open();
        memory.data.put("reader.apptoday2026-10-09", 60000L);
        memory.data.put("reader.apptoday2026-10-10", 120000L);
        memory.data.put("reader.apptoday2026-10-01", 999000L);
        memory.data.put("video.apptoday2026-10-10", 300000L);
        java.util.List<String> dates = java.util.Arrays.asList("2026-10-09", "2026-10-10");
        assertEquals(180000L, store.monitoredTime("reader.app", dates));
        assertEquals(300000L, store.monitoredTime("video.app", dates));
        assertEquals(0, store.monitoredTime("missing.app", dates));
        store.setEnabled("reader.app", false);
        assertEquals(180000L, memory.open().monitoredTime("reader.app", dates));
    }

    @Test public void statisticsCalendarRangeIncludesTodayAcrossYearBoundary() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        now.clear(); now.set(2026, java.util.Calendar.JANUARY, 2, 12, 0);
        java.util.List<String> dates = Store.recentDays(7, now.getTime());
        assertEquals(7, dates.size());
        assertEquals("2025-12-27", dates.get(0));
        assertEquals("2026-01-02", dates.get(6));
        assertEquals(java.util.Collections.singletonList("2026-01-02"), Store.recentDays(1, now.getTime()));
    }
}
