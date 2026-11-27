package com.lichader.hipdanext.ui.setting;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.ui.SettingActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

@RunWith(AndroidJUnit4.class)
public class SettingNestedFragmentInjectionTest {

    @Test
    public void injectsAppWideHttpHelper() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, SettingActivity.class)
                .putExtra(SettingNestedFragment.TAG_KEY, 1);
        try (ActivityScenario<SettingActivity> scenario = ActivityScenario.launch(intent)) {
            ShadowLooper.idleMainLooper();
            scenario.onActivity(activity -> {
                Fragment f = null;
                for (Fragment candidate : activity.getSupportFragmentManager().getFragments()) {
                    if (candidate instanceof SettingNestedFragment) f = candidate;
                }
                assertNotNull("SettingNestedFragment is shown", f);
                assertNotNull(((SettingNestedFragment) f).mOkHttpHelper);
                assertSame(SingletonEntryPoint.get().okHttpHelper(), ((SettingNestedFragment) f).mOkHttpHelper);
            });
        }
    }
}
