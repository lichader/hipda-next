package com.lichader.hipdanext.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.bean.NotificationBean;
import com.lichader.hipdanext.forum.lists.ListItem;
import com.lichader.hipdanext.glide.GlideHelper;
import com.lichader.hipdanext.job.NotificationEvent;
import com.lichader.hipdanext.ui.HiApplication;
import com.lichader.hipdanext.ui.IntentActivity;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Logger;
import com.lichader.hipdanext.utils.Utils;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * parse and fetch notifications
 */
@Singleton
public class NotiHelper {

    public final static String DEFAUL_SLIENT_BEGIN = "22:00";
    public final static String DEFAUL_SLIENT_END = "08:00";
    public final static String CHANNEL_ID = "HIPDA_NOTI";
    public final static String CHANNEL_NAME = "论坛通知";

    private final static long HOLD_FETCH_MILLIS = 10 * 1000;

    private final NotificationBean mCurrentBean = new NotificationBean();

    private volatile long mHoldFetchUntil = 0;

    private final Context mContext;
    private final HiSettingsHelper mSettings;
    private final NewMessagesCheck mNewMessagesCheck;

    @Inject
    public NotiHelper(@ApplicationContext Context context, HiSettingsHelper settings, NewMessagesCheck newMessagesCheck) {
        mContext = context;
        mSettings = settings;
        mNewMessagesCheck = newMessagesCheck;
    }

    public NotificationBean getCurrentNotification() {
        return mCurrentBean;
    }

    public void fetchNotification(Document doc) {
        fetchNotification(doc, -1);
    }

    /**
     * @param pageNoticeCount the notice count already read from a page's header by the site
     *                        adapter, with a null doc; -1 to read it from doc
     */
    private void fetchNotification(Document doc, int pageNoticeCount) {
        int smsCount = -1;
        int threadCount = pageNoticeCount;
        ListItem smsItem = null;

        try {
            //check new sms
            if ((doc == null && pageNoticeCount < 0) || mSettings.isCheckSms()) {
                mSettings.setLastCheckSmsTime(System.currentTimeMillis());

                NewMessages newMessages = mNewMessagesCheck.fetch();
                smsCount = newMessages.getCount();
                smsItem = newMessages.getSingle();
                // The new-messages page's header counts the notices, as a page passed in would.
                doc = null;
                threadCount = newMessages.getNoticeCount();
            }

            if (doc != null) {
                threadCount = 0;
                String[] prompts = {"prompt_threads", "prompt_systempm", "prompt_friend"};
                for (String prompt : prompts) {
                    Elements promptES = doc.select("div.promptcontent a#" + prompt);
                    if (promptES.size() > 0) {
                        threadCount += Utils.parseInt(Utils.getMiddleString(promptES.first().text(), "(", ")"));
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(e);
        }

        if (threadCount >= 0)
            mCurrentBean.setThreadCount(threadCount);

        if (smsCount >= 0) {
            mCurrentBean.setSmsCount(smsCount);
            if (smsCount == 1 && smsItem != null) {
                mCurrentBean.setUsername(smsItem.getAuthor());
                mCurrentBean.setUid(smsItem.getUid());
                mCurrentBean.setContent(smsItem.getTitle());
            } else {
                mCurrentBean.setUsername("");
                mCurrentBean.setUid("");
                mCurrentBean.setContent("");
            }
        }

        if (threadCount > 0 || smsCount > 0) {
            NotificationEvent event = new NotificationEvent();
            EventBus.getDefault().postSticky(event);
        }
    }

    public void showNotification(Context context) {
        if (!HiApplication.isAppVisible()) {
            if (mCurrentBean.hasNew()) {
                sendNotification(context,
                        mCurrentBean.getThreadCount(),
                        mCurrentBean.getSmsCount(),
                        mCurrentBean.getUsername(),
                        mCurrentBean.getUid(),
                        mCurrentBean.getContent());
                HiApplication.setNotified(true);

                //clean count to avoid notification button on start up
                mCurrentBean.setSmsCount(0);
                mCurrentBean.setThreadCount(0);

            } else {
                cancelNotification(context);
            }
        }
    }

    private void cancelNotification(Context context) {
        if (HiApplication.isNotified()) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(0);
            HiApplication.setNotified(false);
        }
    }

    private static String getContentText(int threadCount, int smsCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("您有 ");
        sb.append(smsCount > 0 ? smsCount + " 条新的短消息" : "");
        if (smsCount > 0 && threadCount > 0)
            sb.append("， ");
        sb.append(threadCount > 0 ? threadCount + " 条新的帖子通知" : "");
        return sb.toString();
    }

    /**
     * Reads the notification counts from a page the app has just loaded, and shows a
     * notification if the app is in the background. Runs on a new thread.
     */
    public void fetchNotificationAsync(final Context context, final Document doc) {
        new Thread(() -> {
            if (System.currentTimeMillis() > mHoldFetchUntil) {
                fetchNotification(doc);
                showNotification(context);
            }
        }).start();
    }

    /**
     * {@link #fetchNotificationAsync(Context, Document)} for a page the site adapter has parsed:
     * takes the header's notice count instead of the page.
     */
    public void fetchNotificationAsync(final Context context, final int pageNoticeCount) {
        new Thread(() -> {
            if (System.currentTimeMillis() > mHoldFetchUntil) {
                fetchNotification(null, pageNoticeCount);
                showNotification(context);
            }
        }).start();
    }

    /** Skips {@link #fetchNotificationAsync} for the next ten seconds. */
    public void holdFetchNotify() {
        mHoldFetchUntil = System.currentTimeMillis() + HOLD_FETCH_MILLIS;
    }

    public void sendNotification(final Context context, final int threadCount, final int smsCount, final String username, final String uid, final String smsContent) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            sendNotificationInBackground(context, threadCount, smsCount, username, uid, smsContent);
        });
    }


    private void sendNotificationInBackground(Context context, int threadCount, int smsCount, String username, String uid, String smsContent) {
        Intent intent = new Intent(context, IntentActivity.class);
        intent.setAction(Constants.INTENT_NOTIFICATION);
        intent.putExtra(Constants.EXTRA_SMS_COUNT, smsCount);
        intent.putExtra(Constants.EXTRA_THREAD_COUNT, threadCount);
        if (!TextUtils.isEmpty(username))
            intent.putExtra(Constants.EXTRA_USERNAME, username);
        if (HiUtils.isValidId(uid))
            intent.putExtra(Constants.EXTRA_UID, uid);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, flags);

        String title = "HiPDA论坛提醒";
        String content = getContentText(threadCount, smsCount);
        Bitmap icon = null;

        if (smsCount == 1 && threadCount == 0) {
            title = username + " 的短消息";
            content = smsContent;
            File avatarFile = GlideHelper.getAvatarFile(context, HiUtils.getAvatarUrlByUid(uid));
            if (avatarFile != null && avatarFile.exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                icon = Utils.getCircleBitmap(BitmapFactory.decodeFile(avatarFile.getPath(), options));
            }
        }

        if (icon == null)
            icon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);

        int color = ContextCompat.getColor(context, R.color.icon_blue);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.ic_stat_hi)
                .setLargeIcon(icon)
                .setColor(color)
                .setLights(color, 1000, 3000);

        String sound = mSettings.getStringValue(HiSettingsHelper.PERF_NOTI_SOUND, "");
        if (!TextUtils.isEmpty(sound))
            builder.setSound(Uri.parse(sound));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            builder.setPriority(Notification.PRIORITY_HIGH)
                    .setVibrate(new long[0]);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

    public void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context context = mContext;

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                int color = ContextCompat.getColor(context, R.color.icon_blue);
                NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(color);
                notificationChannel.enableVibration(false);
                notificationChannel.setBypassDnd(false);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
    }

    public void clearNotification() {
        mCurrentBean.clearSmsCount();
        mCurrentBean.clearThreadCount();
    }
}
