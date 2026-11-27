package com.lichader.hipdanext.job;

import android.content.Context;
import android.util.Log;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.log.CustomLogger;

import com.lichader.hipdanext.utils.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Created by GreenSkinMonster on 2015-08-27.
 */
@Singleton
public class JobMgr {

    public final static int PRIORITY_HIGH = 9;
    public final static int PRIORITY_MIDIUM = 6;
    public final static int PRIORITY_LOW = 3;

    private final JobManager jobManager;
    private final JobManager glideJobManager;

    @Inject
    public JobMgr(@ApplicationContext Context context) {
        CustomLogger logger = new CustomLogger() {
            private static final String TAG = "JOBS";

            @Override
            public boolean isDebugEnabled() {
                return Logger.isDebug();
            }

            @Override
            public void d(String text, Object... args) {
//                if (isDebugEnabled())
//                    Log.d(TAG, String.format(text, args));
            }

            @Override
            public void e(Throwable t, String text, Object... args) {
                Log.e(TAG, String.format(text, args), t);
            }

            @Override
            public void e(String text, Object... args) {
                Log.e(TAG, String.format(text, args));
            }

            @Override
            public void v(String text, Object... args) {
            }
        };

        jobManager = new JobManager(new Configuration.Builder(context)
                .customLogger(logger)
                .minConsumerCount(1)
                .maxConsumerCount(3)
                .loadFactor(2)
                .build());
        glideJobManager = new JobManager(new Configuration.Builder(context)
                .customLogger(logger)
                .maxConsumerCount(5)
                .loadFactor(1)
                .build());
    }

    public void addJob(BaseJob job) {
        if (job instanceof GlideImageJob) {
            glideJobManager.addJobInBackground(job);
        } else {
            jobManager.addJobInBackground(job);
        }
    }
}
