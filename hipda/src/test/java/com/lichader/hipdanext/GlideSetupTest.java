package com.lichader.hipdanext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bumptech.glide.Glide;
import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.glide.MyGlideModule;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.EntryPointAccessors;

@RunWith(AndroidJUnit4.class)
public class GlideSetupTest {

    @After
    public void tearDown() {
        Glide.tearDown();
    }

    @Test
    public void glideInitializesWithAppModule() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(Glide.get(context));
    }

    @Test
    public void glideModuleSharesAppSingletons() {
        Context context = ApplicationProvider.getApplicationContext();
        MyGlideModule.Dependencies deps =
                EntryPointAccessors.fromApplication(context, MyGlideModule.Dependencies.class);
        assertSame(SingletonEntryPoint.get().settings(), deps.settings());
        assertSame(SingletonEntryPoint.get().cachedDns(), deps.cachedDns());
    }
}
