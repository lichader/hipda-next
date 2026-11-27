package com.lichader.hipdanext.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.async.UpdateHelper;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.okhttp.CachedDns;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Logger;
import com.lichader.hipdanext.utils.SimpleExceptionHandler;
import com.lichader.hipdanext.utils.UIUtils;
import com.lichader.hipdanext.utils.Utils;

import java.io.File;

import javax.inject.Inject;

import dagger.Lazy;

import dagger.hilt.android.HiltAndroidApp;
import io.github.inflationx.calligraphy3.CalligraphyConfig;
import io.github.inflationx.calligraphy3.CalligraphyInterceptor;
import io.github.inflationx.viewpump.ViewPump;

/**
 * Created by GreenSkinMonster on 2015-03-28.
 */
@HiltAndroidApp
public class HiApplication extends Application
        implements Application.ActivityLifecycleCallbacks, Configuration.Provider {

    public final static int IDLE = 0;
    public final static int RELOAD = 1;
    public final static int RECREATE = 2;
    public final static int RESTART = 3;

    private static Context context;
    private static boolean notified;
    private static boolean updated;
    private static boolean fontSet;
    private static int settingStatus;

    // Lazy keeps start-up as before: each service is created on first use.
    @Inject
    Lazy<OkHttpHelper> mOkHttpHelper;
    @Inject
    Lazy<HiSettingsHelper> mSettings;
    @Inject
    Lazy<CachedDns> mCachedDns;
    @Inject
    Lazy<UpdateHelper> mUpdateHelper;
    @Inject
    HiltWorkerFactory mWorkerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        registerActivityLifecycleCallbacks(this);

//        if (mSettings.get().isErrorReportMode()) {
        Thread.setDefaultUncaughtExceptionHandler(new SimpleExceptionHandler());
//        }

        UIUtils.setLightDarkThemeMode(mSettings.get());
        updated = mUpdateHelper.get().updateApp();

        if (!mSettings.get().isLoginInfoValid()) {
            mOkHttpHelper.get().clearCookies();
        }

        try {
            String font = mSettings.get().getFont();
            if (!TextUtils.isEmpty(font)) {
                File fontFile = new File(Utils.getFontsDir(), font);
                if (fontFile.exists()) {
                    fontSet = true;
                    ViewPump.init(ViewPump.builder()
                            .addInterceptor(new CalligraphyInterceptor(
                                    new CalligraphyConfig.Builder()
                                            .setDefaultFontPath(fontFile.getAbsolutePath())
                                            .setFontAttrId(R.attr.fontPath)
                                            .build()))
                            .build());
                } else {
                    mSettings.get().setFont("");
                }
            }
        } catch (Exception e) {
            mSettings.get().setFont("");
            Logger.e(e);
        }
        HiUtils.updateBaseUrls(mSettings.get());
    }

    /** WorkManager is initialized on first use with this configuration, see the manifest. */
    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(mWorkerFactory)
                .build();
    }

    public static Context getAppContext() {
        return HiApplication.context;
    }

    public static String getAppVersion() {
        String version = "0.0.00";
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        return version;
    }

    public static boolean isNotified() {
        return notified;
    }

    public static void setNotified(boolean b) {
        notified = b;
    }

    public static boolean isUpdated() {
        return updated;
    }

    public static void setUpdated(boolean updated) {
        HiApplication.updated = updated;
    }

    public static boolean isFontSet() {
        return fontSet;
    }

    public static int getSettingStatus() {
        return settingStatus;
    }

    public static void setSettingStatus(int settingStatus) {
        HiApplication.settingStatus = settingStatus;
    }

    private static int visibleActivityCount = 0;
    private static int foregroundActivityCount = 0;
    private static int mainActivityCount = 0;

    public static boolean isAppInForeground() {
        return foregroundActivityCount > 0;
    }

    public static boolean isAppVisible() {
        return visibleActivityCount > 0;
    }

    public static int getMainActivityCount() {
        return mainActivityCount;
    }

    public void onActivityCreated(Activity activity, Bundle bundle) {
        if (activity instanceof MainFrameActivity) {
            mainActivityCount++;
        }
    }

    public void onActivityDestroyed(Activity activity) {
        if (activity instanceof MainFrameActivity) {
            mainActivityCount--;
            mCachedDns.get().clearCache();
        }
    }

    public void onActivityResumed(Activity activity) {
        foregroundActivityCount++;
    }

    public void onActivityPaused(Activity activity) {
        foregroundActivityCount--;
    }


    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    public void onActivityStarted(Activity activity) {
        visibleActivityCount++;
    }

    public void onActivityStopped(Activity activity) {
        visibleActivityCount--;
    }

}
