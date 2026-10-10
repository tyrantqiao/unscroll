package com.unscroll;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Apps with launcher entries in the current Android user/profile. */
public final class AppCatalog {
    public static final class App {
        public final String packageName;
        public final String name;
        public final Drawable icon;
        public final boolean controllable;

        App(String packageName, String name, Drawable icon, boolean controllable) {
            this.packageName = packageName;
            this.name = name;
            this.icon = icon;
            this.controllable = controllable;
        }
    }

    public static Set<String> protectedPackages(Context context) {
        Set<String> result = new HashSet<>();
        result.add(context.getPackageName());
        result.add("com.android.settings");
        result.add("com.android.systemui");
        PackageManager pm = context.getPackageManager();
        for (Intent intent : new Intent[] {
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
                new Intent(android.provider.Settings.ACTION_SETTINGS) }) {
            for (ResolveInfo entry : pm.queryIntentActivities(intent, 0)) {
                if (entry.activityInfo != null) result.add(entry.activityInfo.packageName);
            }
        }
        android.telecom.TelecomManager telecom =
                (android.telecom.TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecom != null && telecom.getDefaultDialerPackage() != null) {
            result.add(telecom.getDefaultDialerPackage());
        }
        return result;
    }

    public static List<App> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> protectedApps = protectedPackages(context);
        Map<String, App> unique = new HashMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo entry : pm.queryIntentActivities(launcher, 0)) {
            if (entry.activityInfo == null) continue;
            String packageName = entry.activityInfo.packageName;
            if (unique.containsKey(packageName)) continue;
            try {
                android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                unique.put(packageName, new App(packageName,
                        pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info),
                        !protectedApps.contains(packageName)));
            } catch (PackageManager.NameNotFoundException ignored) {
                // App may be uninstalled while reading the list.
            }
        }
        List<App> apps = new ArrayList<>(unique.values());
        Collator collator = Collator.getInstance(java.util.Locale.CHINA);
        apps.sort((a, b) -> {
            int order = collator.compare(a.name, b.name);
            return order == 0 ? a.packageName.compareTo(b.packageName) : order;
        });
        return apps;
    }
}
