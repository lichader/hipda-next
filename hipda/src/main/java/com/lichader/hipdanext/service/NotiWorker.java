package com.lichader.hipdanext.service;

import android.content.Context;

import com.lichader.hipdanext.async.LoginHelper;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.ui.HiApplication;
import com.lichader.hipdanext.utils.Logger;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

@HiltWorker
public class NotiWorker extends Worker {

    private final static String WORK_NAME = "noti_work";
    private final static int NOTI_REPEAT_MINUTTE = 15;

    private final HiSettingsHelper mSettings;
    private final LoginHelper mLoginHelper;
    private final NotiHelper mNotiHelper;

    /**
     * @param appContext   The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */
    @AssistedInject
    public NotiWorker(@Assisted @NonNull Context appContext,
                      @Assisted @NonNull WorkerParameters workerParams,
                      HiSettingsHelper settings,
                      LoginHelper loginHelper,
                      NotiHelper notiHelper) {
        super(appContext, workerParams);
        mSettings = settings;
        mLoginHelper = loginHelper;
        mNotiHelper = notiHelper;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            if (!HiApplication.isAppVisible()
                    && mLoginHelper.isLoggedIn()
                    && !mSettings.isInSilentMode()) {
                mSettings.setNotiJobLastRunTime();
                mNotiHelper.fetchNotification(null);
                mNotiHelper.showNotification(getApplicationContext());
            }
        } catch (Exception e) {
            Logger.e(e);
        }
        return Result.success();
    }

    public static void scheduleOrCancelWork(Context context, HiSettingsHelper settings) {
        if (settings.isNotiTaskEnabled()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest request
                    = new PeriodicWorkRequest
                    .Builder(NotiWorker.class, NOTI_REPEAT_MINUTTE, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();
            WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        }
    }
}
