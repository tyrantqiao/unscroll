package com.unscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class UsagePolicyTest {
    private UsagePolicy policy() {
        UsagePolicy p = new UsagePolicy(); p.day("2026-10-05", 0, false); return p;
    }
    private UsagePolicy.Rules longSession() {
        UsagePolicy.Rules r = new UsagePolicy.Rules();
        r.warn = 100 * UsagePolicy.MINUTE; r.stop = 120 * UsagePolicy.MINUTE; return r;
    }

    @Test public void tenThirtySixtyAppearOnlyOnceEachSession() {
        UsagePolicy p = policy(); UsagePolicy.Rules r = longSession();
        int[] targets = {10,30,60}; long previous = 0;
        for (int target : targets) {
            long t = target * UsagePolicy.MINUTE;
            UsagePolicy.Result result = p.advance(t, t-previous, true, r);
            assertEquals(UsagePolicy.Kind.MILESTONE, result.kind); assertEquals(target, result.minutes);
            assertEquals(UsagePolicy.Kind.NONE, p.advance(t, 0, true, r).kind);
            previous = t;
        }
    }

    @Test public void leavingStartsNewContinuousSessionButRetainsDailyTotal() {
        UsagePolicy p = policy(); UsagePolicy.Rules r = longSession();
        p.advance(600000,600000,true,r); p.advance(600001,0,false,r);
        assertEquals(0,p.session.used); assertEquals(600000,p.total);
        assertEquals(UsagePolicy.Kind.MILESTONE,p.advance(1200001,600000,true,r).kind);
        assertEquals(1200000,p.total);
    }

    @Test public void dailyRuleWorksWithContinuousRuleDisabled() {
        UsagePolicy p=policy();UsagePolicy.Rules r=longSession();r.continuous=false;r.daily=true;
        assertEquals(UsagePolicy.Kind.DAILY_REMINDER,p.advance(2700000,2700000,true,r).kind);
        p.advance(2700001,0,false,r);
        assertEquals(UsagePolicy.Kind.DAILY_BLOCK,p.advance(3600001,900000,true,r).kind);
        assertEquals(UsagePolicy.Kind.DAILY_BLOCK,p.advance(3700001,100000,true,r).kind);
        assertEquals(3600000,p.total);
    }

    @Test public void dailyLimitDoesNotBlockOtherAppsAndResetsNextDay() {
        UsagePolicy p=policy();UsagePolicy.Rules r=longSession();r.daily=true;
        p.advance(3600000,3600000,true,r);
        assertEquals(UsagePolicy.Kind.NONE,p.advance(3600001,0,false,r).kind);
        p.day("2026-10-06",0,false);
        assertEquals(UsagePolicy.Kind.NONE,p.advance(3600002,0,true,r).kind);
        assertEquals(0,p.total);
    }

    @Test public void continuousCooldownPersistsAfterLeavingButDailyTotalDoesNotReset() {
        UsagePolicy p=policy();UsagePolicy.Rules r=new UsagePolicy.Rules();
        assertEquals(UsagePolicy.Kind.CONTINUOUS_BLOCK,p.advance(1200000,1200000,true,r).kind);
        p.advance(1200001,0,false,r);
        assertEquals(UsagePolicy.Kind.CONTINUOUS_BLOCK,p.advance(1200002,0,true,r).kind);
        assertEquals(UsagePolicy.Kind.NONE,p.advance(1500000,0,true,r).kind);
        assertEquals(1200000,p.total);
    }

    @Test public void dailyBlockWinsWhenBothLimitsHitTogether() {
        UsagePolicy p=policy();UsagePolicy.Rules r=new UsagePolicy.Rules();r.daily=true;r.dailyStop=r.stop;
        assertEquals(UsagePolicy.Kind.DAILY_BLOCK,p.advance(r.stop,r.stop,true,r).kind);
    }

    @Test public void customReminderAtMilestoneDoesNotDuplicate() {
        UsagePolicy p=policy();UsagePolicy.Rules r=longSession();r.warn=600000;
        assertEquals(UsagePolicy.Kind.CONTINUOUS_REMINDER,p.advance(600000,600000,true,r).kind);
        assertEquals(UsagePolicy.Kind.NONE,p.advance(600001,1,true,r).kind);
    }

    @Test public void limitAtMilestoneDoesNotShowSmallReminder() {
        UsagePolicy p=policy();UsagePolicy.Rules r=longSession();r.stop=600000;
        assertEquals(UsagePolicy.Kind.CONTINUOUS_BLOCK,p.advance(600000,600000,true,r).kind);
    }

    @Test public void midnightSplitsDailyTimeButPreservesContinuousTime() {
        UsagePolicy p=policy();UsagePolicy.Rules r=longSession();
        p.advance(599500,599500,true,r);p.day("2026-10-06",0,false);
        p.advance(600000,500,200,true,r);
        assertEquals(600000,p.session.used);assertEquals(200,p.total);
    }

    @Test public void dailyWarningIsRetainedOnServiceRestoreAndSessionsAreIndependent() {
        UsagePolicy a=policy(),b=policy();UsagePolicy.Rules r=longSession();r.daily=true;
        a.day("2026-10-06",2700000,true);
        assertEquals(UsagePolicy.Kind.NONE,a.advance(1,0,true,r).kind);
        assertEquals(0,b.total);
        assertEquals(0,b.session.used);
    }
}
