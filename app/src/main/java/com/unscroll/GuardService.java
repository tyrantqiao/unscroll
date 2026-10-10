package com.unscroll;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GuardService extends AccessibilityService {
    public static boolean connected;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, UsagePolicy> policies = new HashMap<>();
    private Set<String> protectedApps;
    private Store store;
    private long last;
    private String foreground = "", previous = "", overlayPackage = "";
    private View overlay;
    private TextView countdown;
    private long remindUntil;
    private boolean blocking;
    private UsagePolicy.Kind overlayKind = UsagePolicy.Kind.NONE;
    private final Runnable ticker = new Runnable() {
        public void run() { tick(); handler.postDelayed(this, 500); }
    };

    @Override protected void onServiceConnected() {
        store = new Store(this);
        protectedApps = AppCatalog.protectedPackages(this);
        connected = true;
        last = SystemClock.elapsedRealtime();
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        boolean sameBoot = boot >= 0 && boot == store.prefs.getInt("boot", -2);
        for (String p : store.configuredPackages()) {
            UsagePolicy policy = new UsagePolicy();
            // An interrupted service cannot prove that a continuous session persisted.
            // Keep a pending cooldown within the same boot, but start a fresh session.
            if (sameBoot) policy.session.blockedUntil = store.prefs.getLong(p + "blocked", 0);
            policies.put(p, policy);
        }
        store.prefs.edit().putInt("boot", boot).apply();
        handler.post(ticker);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store != null) tick();
    }

    private String currentPackage() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager key = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!power.isInteractive() || key.isKeyguardLocked()) return "";
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isActive()) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) return "";
            CharSequence name = root.getPackageName();
            String result = name == null ? "" : name.toString();
            root.recycle();
            return result;
        }
        return "";
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        long delta = Math.min(1000, Math.max(0, now - last));
        last = now;
        foreground = currentPackage();
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        java.util.Calendar midnight = java.util.Calendar.getInstance();
        midnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
        midnight.set(java.util.Calendar.MINUTE, 0);
        midnight.set(java.util.Calendar.SECOND, 0);
        midnight.set(java.util.Calendar.MILLISECOND, 0);
        long sinceMidnight = Math.max(0, System.currentTimeMillis() - midnight.getTimeInMillis());
        boolean needsBlock = false;
        for (String p : store.configuredPackages()) {
            UsagePolicy policy = policies.get(p);
            if (policy == null) { policy = new UsagePolicy(); policies.put(p, policy); }
            policy.day(day, store.prefs.getLong(p + "today" + day, 0),
                    store.prefs.getBoolean(p + "dayWarned" + day, false));
            if (!store.enabled(p) || protectedApps.contains(p)) {
                policy.resetContinuous(); policy.session.blockedUntil = 0;
                save(p, policy);
                store.prefs.edit().putBoolean(p + "lockDone", false).apply();
                continue;
            }
            UsagePolicy.Rules rules = store.rules(p);
            boolean active = p.equals(foreground);
            long elapsed = active && p.equals(previous) ? delta : 0;

            if (!active && policy.session.used > 0) store.prefs.edit().putLong(p + "lastSession", policy.session.used).apply();
            UsagePolicy.Result result = policy.advance(now, elapsed, Math.min(elapsed, sinceMidnight), active, rules);
            // Assign the sub-second interval straddling midnight to the correct calendar day.
            if (result.counted > sinceMidnight) {
                long oldDayTime = result.counted - sinceMidnight;
                String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                        .format(new Date(midnight.getTimeInMillis() - 1));
                String key = p + "today" + yesterday;
                store.prefs.edit().putLong(key, store.prefs.getLong(key, 0) + oldDayTime).apply();

            }
            if (result.blocked()) {
                needsBlock = true;
                if (overlay == null || !blocking || !p.equals(overlayPackage) || overlayKind != result.kind) {
                    showOverlay(p, result);
                }
                if (countdown != null) {
                    countdown.setText(result.kind == UsagePolicy.Kind.DAILY_BLOCK
                            ? "今日累计已达上限，次日恢复使用。"
                            : "休息剩余 " + Math.max(0, (policy.session.blockedUntil - now + 999) / 1000) + " 秒");
                }
                String lockKey = result.kind == UsagePolicy.Kind.DAILY_BLOCK
                        ? p + "dailyLock" + day : p + "lockDone";
                if (store.prefs.getBoolean(p + "lock", false) && !store.prefs.getBoolean(lockKey, false)) {
                    store.prefs.edit().putBoolean(lockKey, true).apply();
                    boolean ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                    store.prefs.edit().putString("lockResult", ok
                            ? "系统已接受锁屏请求，需确认实际效果" : "锁屏请求失败，已保留应用中断").apply();
                }
            } else if (result.kind != UsagePolicy.Kind.NONE) {
                showOverlay(p, result);
                remindUntil = now + (result.kind == UsagePolicy.Kind.MILESTONE ? 4000 : 8000);
            }
            if (policy.session.blockedUntil == 0) store.prefs.edit().putBoolean(p + "lockDone", false).apply();
            save(p, policy);
        }
        if (overlay != null && ((blocking && !needsBlock)
                || (!blocking && (!overlayPackage.equals(foreground) || now >= remindUntil)))) removeOverlay();
        previous = foreground;
    }

    private void save(String p, UsagePolicy policy) {
        SharedPreferences.Editor editor = store.prefs.edit();
        editor.putLong(p + "used", policy.session.used)
                .putLong(p + "blocked", policy.session.blockedUntil)
                .putLong(p + "today" + policy.day, policy.total)
                .putBoolean(p + "dayWarned" + policy.day, policy.dailyWarned).apply();
    }

    private void showOverlay(String p, UsagePolicy.Result result) {
        removeOverlay();
        blocking = result.blocked(); overlayPackage = p; overlayKind = result.kind;
        boolean small = result.kind == UsagePolicy.Kind.MILESTONE;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round((small ? 14 : 24) * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackgroundColor(0xfff4f8ef);
        String name = p;
        try { name = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p, 0)).toString(); }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        TextView title = new TextView(this);
        title.setText(small ? name + " 已连续使用 " + result.minutes + " 分钟，记得休息一下。"
                : result.kind == UsagePolicy.Kind.DAILY_BLOCK ? name + " 今日使用已达上限。"
                : blocking ? name + " 这一轮，到这里。"
                : result.kind == UsagePolicy.Kind.DAILY_REMINDER ? name + " 今日累计接近上限。"
                : name + " 连续使用接近上限。");
        title.setTextSize(small ? 15 : 24); title.setTextColor(0xff194d3b); panel.addView(title);
        if (!small) {
            countdown = new TextView(this);
            countdown.setText(blocking ? "休息一会儿，再决定要不要继续。" : "即将到达你设定的使用边界。");
            countdown.setTextSize(18); panel.addView(countdown);
            Button home = new Button(this); home.setText("回到桌面");
            home.setOnClickListener(v -> { performGlobalAction(GLOBAL_ACTION_HOME); removeOverlay(); }); panel.addView(home);
            if (!blocking) {
                Button resume = new Button(this); resume.setText("继续使用");
                resume.setOnClickListener(v -> removeOverlay()); panel.addView(resume);
            }
            Button settings = new Button(this); settings.setText("打开 Unscroll / 调整规则");
            settings.setOnClickListener(v -> { removeOverlay(); startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }); panel.addView(settings);
        }
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (small) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                blocking ? WindowManager.LayoutParams.MATCH_PARENT : WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, android.graphics.PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP;
        try { ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(panel, lp); overlay = panel; }
        catch (RuntimeException e) { store.prefs.edit().putString("error", "提示界面显示失败：" + e.getClass().getSimpleName()).apply(); }
    }

    private void removeOverlay() {
        if (overlay != null) { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay); overlay = null; countdown = null; }
    }
    @Override public void onInterrupt() { removeOverlay(); }
    @Override public void onDestroy() {
        connected = false; handler.removeCallbacksAndMessages(null); removeOverlay(); super.onDestroy();
    }
}
