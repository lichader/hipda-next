package com.lichader.hipdanext.async;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.db.ContentDao;
import com.lichader.hipdanext.db.HistoryDao;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.ui.widget.HiProgressDialog;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Utils;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TaskHelper {

    private final HiSettingsHelper mSettings;
    private final OkHttpHelper mOkHttpHelper;
    private final LoginHelper mLoginHelper;
    private final BlacklistHelper mBlacklistHelper;
    private final ContentDao mContentDao;
    private final HistoryDao mHistoryDao;

    @Inject
    public TaskHelper(HiSettingsHelper settings, OkHttpHelper okHttpHelper,
                      LoginHelper loginHelper, BlacklistHelper blacklistHelper,
                      ContentDao contentDao, HistoryDao historyDao) {
        mSettings = settings;
        mOkHttpHelper = okHttpHelper;
        mLoginHelper = loginHelper;
        mBlacklistHelper = blacklistHelper;
        mContentDao = contentDao;
        mHistoryDao = historyDao;
    }

    public void runDailyTask(boolean force) {
        String millis = mSettings
                .getStringValue(HiSettingsHelper.PERF_LAST_TASK_TIME, "0");
        Date last = null;
        if (millis.length() > 0) {
            try {
                last = new Date(Long.parseLong(millis));
            } catch (Exception ignored) {
            }
        }
        if (force || last == null || System.currentTimeMillis() > last.getTime() + 24 * 60 * 60 * 1000) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mContentDao.cleanup();
                    mHistoryDao.cleanup();
                    Utils.cleanPictures();
                    Utils.cleanTempUploadFiles();
                    //FavoriteHelper.getInstance().fetchMyFavorites();
                    //FavoriteHelper.getInstance().fetchMyAttention();
                }
            }).start();
            mSettings
                    .setStringValue(HiSettingsHelper.PERF_LAST_TASK_TIME, System.currentTimeMillis() + "");
        }
        Date bSyncDate = mSettings.getBlacklistSyncTime();
        if (mLoginHelper.isLoggedIn()
                && (force || bSyncDate == null
                || System.currentTimeMillis() > bSyncDate.getTime() + 24 * 60 * 60 * 1000)) {
            mBlacklistHelper.syncBlacklists();
        }
    }

    public void updateImageHost() {
        updateImageHost(null, null);
    }

    public void updateImageHost(final Activity activity, final Preference preference) {
        final String imageHostPerf = mSettings.getStringValue(HiSettingsHelper.PERF_IMAGE_HOST, "");

        long imageHostUpdateTime = mSettings.getLongValue(HiSettingsHelper.PERF_IMAGE_HOST_UPDATE_TIME, 0);
        if (activity != null
                || TextUtils.isEmpty(imageHostPerf)
                || !imageHostPerf.contains("://")
                || System.currentTimeMillis() - imageHostUpdateTime > 60 * 60 * 1000) {
            HiProgressDialog dialog;
            if (activity != null) {
                dialog = HiProgressDialog.show(activity, "正在更新...");
            } else {
                dialog = null;
            }
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                Exception runtimeError = null;
                try {
                    updateSetting();
                } catch (Exception ex) {
                    runtimeError = ex;
                }
                final Exception e = runtimeError;
                handler.post(() -> {
                    if (e != null) {
                        if (dialog != null)
                            dialog.dismissError("发生错误 : " + OkHttpHelper.getErrorMessage(e));
                    } else {
                        if (dialog != null)
                            dialog.dismiss("服务器已更新 \n\n"
                                            + "论坛:" + mSettings.getForumServer() + "\n"
                                            + "图片:" + mSettings.getImageHost(),
                                    2000);
                        if (preference != null)
                            preference.setSummary(mSettings.getForumServer());
                    }
                });
            });
        }

    }

    private void updateSetting() throws Exception {
        mSettings.setForumServer(HiUtils.ForumServer);
        String response = mOkHttpHelper.get(HiUtils.ForumServer + "/config.php");
        Gson gson = new Gson();
        Type stringStringMap = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> map = gson.fromJson(response, stringStringMap);
        String imageHost = map.get("CDN");
        mSettings.setImageHost(imageHost);
        HiUtils.updateBaseUrls(mSettings);
        mSettings.setLongValue(HiSettingsHelper.PERF_IMAGE_HOST_UPDATE_TIME, System.currentTimeMillis());
    }

}
