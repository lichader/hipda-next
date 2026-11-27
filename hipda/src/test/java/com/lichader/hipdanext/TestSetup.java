package com.lichader.hipdanext;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

/** Shared set-up for Robolectric tests. */
final class TestSetup {

    private TestSetup() {
    }

    /** Robolectric does not run AndroidX Startup, so WorkManager is initialized by hand. */
    static void initWorkManager() {
        Context context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
    }
}
