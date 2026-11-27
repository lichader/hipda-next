package com.lichader.hipdanext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ContextWrapper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lichader.hipdanext.glide.GlideHelper;
import com.lichader.hipdanext.utils.UIUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/** Hilt fragments hand out a ContextWrapper; Activity checks must see through it. */
@RunWith(AndroidJUnit4.class)
public class FindActivityTest {

    @Test
    public void unwrapsContextWrappers() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        ContextWrapper wrapped = new ContextWrapper(new ContextWrapper(activity));

        assertSame(activity, UIUtils.findActivity(activity));
        assertSame(activity, UIUtils.findActivity(wrapped));
        assertNull(UIUtils.findActivity(activity.getApplicationContext()));
        assertNull(UIUtils.findActivity(null));

        assertTrue(GlideHelper.isOkToLoad(wrapped));
        controller.pause().stop().destroy();
        assertFalse("must not load into a destroyed activity", GlideHelper.isOkToLoad(wrapped));
    }
}
