package com.lichader.hipdanext.job;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.okhttp.OkHttpHelper;

import androidx.annotation.Nullable;

public abstract class BaseJob extends Job {

    /**
     * Jobs are created with {@code new} by the screens that start them, so Hilt cannot
     * inject them; they look their services up instead. The queue never persists jobs.
     */
    protected final transient SingletonEntryPoint mServices = SingletonEntryPoint.get();
    protected final transient HiSettingsHelper mSettings = mServices.settings();
    protected final transient OkHttpHelper mOkHttpHelper = mServices.okHttpHelper();

    protected String mSessionId;

    public BaseJob(String sessionId) {
        this(sessionId, JobMgr.PRIORITY_MIDIUM);
    }

    public BaseJob(String sessionId, int priority) {
        super(new Params(priority)
                .setPersistent(false)
                .setRequiresNetwork(false)
                .addTags(sessionId));
        mSessionId = sessionId;
    }

    protected BaseJob(Params params) {
        super(params);
    }

    @Override
    protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(Throwable throwable,
                                                     int runCount,
                                                     int maxRunCount) {
        return RetryConstraint.CANCEL;
    }

    @Override
    protected int getRetryLimit() {
        return 0;
    }
}