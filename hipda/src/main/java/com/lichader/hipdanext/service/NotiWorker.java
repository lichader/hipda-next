package com.lichader.hipdanext.service;

import android.content.Context;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.ui.HiApplication;
import com.lichader.hipdanext.utils.Logger;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.lichader.hipdanext.di.SingletonEntryPoint;

/**
 * Created by GreenSkinMonster on 2019-03-16.
 */
public class NotiWorker extends Worker {

    private final static String WORK_NAME = "noti_work";
    private final static int NOTI_REPEAT_MINUTTE = 15;

    /**
     * @param appContext   The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */
    public NotiWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            if (!HiApplication.isAppVisible()
                    && SingletonEntryPoint.get().loginHelper().isLoggedIn()
                    && !HiSettingsHelper.getInstance().isInSilentMode()) {
                HiSettingsHelper.getInstance().setNotiJobLastRunTime();
                SingletonEntryPoint.get().notiHelper().fetchNotification(null);
                SingletonEntryPoint.get().notiHelper().showNotification(HiApplication.getAppContext());
            }
        } catch (Exception e) {
            Logger.e(e);
        }
        return Result.success();
    }

    public static void scheduleOrCancelWork() {
        if (HiSettingsHelper.getInstance().isNotiTaskEnabled()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest request
                    = new PeriodicWorkRequest
                    .Builder(NotiWorker.class, NOTI_REPEAT_MINUTTE, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();
            WorkManager.getInstance(HiApplication.getAppContext())
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
        } else {
            WorkManager.getInstance(HiApplication.getAppContext()).cancelUniqueWork(WORK_NAME);
        }
    }
}
