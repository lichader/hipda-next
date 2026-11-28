package com.lichader.hipdanext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lichader.hipdanext.async.PostHelper;
import com.lichader.hipdanext.job.SimpleListJob;
import com.lichader.hipdanext.ui.BaseActivity;
import com.lichader.hipdanext.ui.BaseFragment;
import com.lichader.hipdanext.ui.MainFrameActivity;
import com.lichader.hipdanext.ui.PostActivity;
import com.lichader.hipdanext.ui.PostFragment;
import com.lichader.hipdanext.ui.SettingActivity;
import com.lichader.hipdanext.ui.SimpleListActivity;
import com.lichader.hipdanext.ui.SimpleListFragment;
import com.lichader.hipdanext.ui.SmsActivity;
import com.lichader.hipdanext.ui.SmsFragment;
import com.lichader.hipdanext.ui.ThreadDetailActivity;
import com.lichader.hipdanext.ui.ThreadDetailFragment;
import com.lichader.hipdanext.ui.UserInfoActivity;
import com.lichader.hipdanext.ui.UserinfoFragment;
import com.lichader.hipdanext.ui.setting.AboutFragment;
import com.lichader.hipdanext.ui.setting.BaseSettingFragment;
import com.lichader.hipdanext.ui.setting.BlacklistFragment;
import com.lichader.hipdanext.ui.setting.SettingNestedFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

import dagger.hilt.internal.GeneratedComponentManagerHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Opens every screen of the app and checks that it gets to the resumed state, and survives
 * being recreated by a configuration change, without crashing. Network calls fail offline;
 * the screens must cope with that.
 */
@RunWith(AndroidJUnit4.class)
public class ActivityLaunchTest {

    @Before
    public void setUp() {
        TestSetup.initWorkManager();
    }

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static <A extends Activity> void assertLaunches(Intent intent) {
        try (ActivityScenario<A> scenario = ActivityScenario.launch(intent)) {
            ShadowLooper.idleMainLooper();
            scenario.moveToState(Lifecycle.State.RESUMED);
            Map<String, String> sessions = new HashMap<>();
            scenario.onActivity(a -> {
                assertNotNull(a.getWindow().getDecorView());
                assertInjected(a);
                sessions.putAll(sessionIds(a));
            });

            // A configuration change, such as a rotation or a dark mode switch, recreates the
            // activity and its fragments. Hilt fragments crash here if they are retained.
            scenario.recreate();
            ShadowLooper.idleMainLooper();
            scenario.onActivity(a -> {
                assertInjected(a);
                // Job results and drafts are keyed by the session ID, so it must survive.
                assertEquals(sessions, sessionIds(a));
            });
        }
    }

    /** The session ID of each {@link BaseFragment} on the screen, by fragment class. */
    private static Map<String, String> sessionIds(Activity activity) {
        Map<String, String> ids = new HashMap<>();
        if (activity instanceof FragmentActivity) {
            for (Fragment f : ((FragmentActivity) activity).getSupportFragmentManager().getFragments()) {
                if (f instanceof BaseFragment) {
                    ids.put(f.getClass().getName(), ((BaseFragment) f).mSessionId);
                }
            }
        }
        return ids;
    }

    /**
     * Screens read their services from {@code @Inject} fields in the base classes, which
     * stay null unless the concrete class is an {@code @AndroidEntryPoint}.
     */
    private static void assertInjected(Activity activity) {
        if (activity instanceof BaseActivity) {
            assertTrue(activity.getClass().getName() + " must be an @AndroidEntryPoint",
                    activity instanceof GeneratedComponentManagerHolder);
        }
        if (activity instanceof FragmentActivity) {
            for (Fragment f : ((FragmentActivity) activity).getSupportFragmentManager().getFragments()) {
                if (f instanceof BaseFragment || f instanceof BaseSettingFragment) {
                    assertTrue(f.getClass().getName() + " must be an @AndroidEntryPoint",
                            f instanceof GeneratedComponentManagerHolder);
                }
            }
        }
    }

    @Test
    public void mainFrame() {
        assertLaunches(new Intent(context(), MainFrameActivity.class));
    }

    @Test
    public void settingsMain() {
        assertLaunches(new Intent(context(), SettingActivity.class));
    }

    @Test
    public void settingsNestedScreens() {
        for (int screen = 1; screen <= 5; screen++) {
            assertLaunches(new Intent(context(), SettingActivity.class)
                    .putExtra(SettingNestedFragment.TAG_KEY, screen));
        }
    }

    @Test
    public void settingsAbout() {
        assertLaunches(new Intent(context(), SettingActivity.class)
                .putExtra(AboutFragment.TAG_KEY, AboutFragment.TAG_KEY));
    }

    @Test
    public void settingsBlacklist() {
        assertLaunches(new Intent(context(), SettingActivity.class)
                .putExtra(BlacklistFragment.TAG_KEY, BlacklistFragment.TAG_KEY));
    }

    @Test
    public void threadDetail() {
        assertLaunches(new Intent(context(), ThreadDetailActivity.class)
                .putExtra(ThreadDetailFragment.ARG_TID_KEY, "1")
                .putExtra(ThreadDetailFragment.ARG_TITLE_KEY, "title")
                .putExtra(ThreadDetailFragment.ARG_MAX_PAGE_KEY, 1));
    }

    @Test
    public void userInfo() {
        assertLaunches(new Intent(context(), UserInfoActivity.class)
                .putExtra(UserinfoFragment.ARG_UID, "1")
                .putExtra(UserinfoFragment.ARG_USERNAME, "user"));
    }

    @Test
    public void sms() {
        assertLaunches(new Intent(context(), SmsActivity.class)
                .putExtra(SmsFragment.ARG_AUTHOR, "user")
                .putExtra(SmsFragment.ARG_UID, "1"));
    }

    @Test
    public void simpleLists() {
        int[] types = {SimpleListJob.TYPE_MYREPLY, SimpleListJob.TYPE_MYPOST, SimpleListJob.TYPE_SMS,
                SimpleListJob.TYPE_THREAD_NOTIFY, SimpleListJob.TYPE_FAVORITES,
                SimpleListJob.TYPE_ATTENTION, SimpleListJob.TYPE_HISTORIES};
        for (int type : types) {
            assertLaunches(new Intent(context(), SimpleListActivity.class)
                    .putExtra(SimpleListFragment.ARG_TYPE, type));
        }
    }

    @Test
    public void newThread() {
        assertLaunches(new Intent(context(), PostActivity.class)
                .putExtra(PostFragment.ARG_MODE_KEY, PostHelper.MODE_NEW_THREAD)
                .putExtra(PostFragment.ARG_FID_KEY, 2)
                .putExtra(PostFragment.ARG_PARENT_ID, "test"));
    }
}
