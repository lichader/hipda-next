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

    /**
     * WorkManager in tests runs work at once on the calling thread, and creates workers
     * with the app's Hilt worker factory as it does in the app.
     */
    static void initWorkManager() {
        Context context = ApplicationProvider.getApplicationContext();
        Configuration appConfig = ((Configuration.Provider) context).getWorkManagerConfiguration();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder()
                        .setWorkerFactory(appConfig.getWorkerFactory())
                        .setExecutor(new SynchronousExecutor())
                        .build());
    }
}
