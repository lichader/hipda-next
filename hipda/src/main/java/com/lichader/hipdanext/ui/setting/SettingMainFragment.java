package com.lichader.hipdanext.ui.setting;

import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.preference.Preference;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.glide.GlideHelper;
import com.lichader.hipdanext.service.NotiWorker;
import com.lichader.hipdanext.ui.FragmentUtils;
import com.lichader.hipdanext.ui.HiApplication;
import com.lichader.hipdanext.ui.SettingActivity;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Utils;

import java.util.List;
import java.util.Set;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * main setting fragment
 * Created by GreenSkinMonster on 2015-09-11.
 */
@AndroidEntryPoint
public class SettingMainFragment extends BaseSettingFragment {

    private int mScreenOrietation;
    private List<Integer> mForums;
    private Set<String> mFreqMenus;
    private String mFont;
    static boolean mCacheCleared;
    private String mForumServer;
    private boolean mCircleAvatar;
    private boolean mNotiTaskEnabled;

    private boolean mFirstResume = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_main);

        bindPreferenceSummaryToValue();

        // "nested_#" is the <Preference android:key="nested" android:persistent="false"/>
        for (int i = 1; i <= 5; i++) {
            final int screenKey = i;
            findPreference("nested_" + i).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                private long mLastClickTime;

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    //avoid double click
                    long currentClickTime = System.currentTimeMillis();
                    long elapsedTime = currentClickTime - mLastClickTime;
                    mLastClickTime = currentClickTime;
                    if (elapsedTime <= Constants.MIN_CLICK_INTERVAL)
                        return true;

                    Intent intent = new Intent(getActivity(), SettingActivity.class);
                    intent.putExtra(SettingNestedFragment.TAG_KEY, screenKey);
                    ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(getActivity(), R.anim.slide_in_right, 0);
                    ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
                    return true;
                }
            });
        }

        mScreenOrietation = mSettings.getScreenOrietation();
        mForums = mSettings.getForums();
        mFreqMenus = mSettings.getFreqMenus();
        mFont = mSettings.getFont();
        mForumServer = mSettings.getForumServer();
        mCircleAvatar = mSettings.isCircleAvatar();
        mNotiTaskEnabled = mSettings.isNotiTaskEnabled();

        setActionBarTitle(R.string.title_fragment_settings);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mFirstResume) {
            updateSettingStatus();
        } else {
            mFirstResume = false;
        }
    }

    private void updateSettingStatus() {
        mSettings.reload();

        if (mSettings.isCircleAvatar() != mCircleAvatar)
            GlideHelper.initDefaultFiles(mSettings);

        if (mNotiTaskEnabled != mSettings.isNotiTaskEnabled()) {
            NotiWorker.scheduleOrCancelWork(requireContext().getApplicationContext(), mSettings);
        }

        if (mCacheCleared
                || !mSettings.getFont().equals(mFont)) {
            HiApplication.setSettingStatus(HiApplication.RESTART);
        } else if (mSettings.getScreenOrietation() != mScreenOrietation
                || !mSettings.getForums().equals(mForums)
                || !mSettings.getFreqMenus().equals(mFreqMenus)
                || !mSettings.getForumServer().equals(mForumServer)) {
            HiApplication.setSettingStatus(HiApplication.RECREATE);
        } else {
            HiApplication.setSettingStatus(HiApplication.RELOAD);
        }
    }

    private void bindPreferenceSummaryToValue() {
        Preference dialogPref = findPreference(HiSettingsHelper.PERF_ABOUT);

        dialogPref.setSummary(HiApplication.getAppVersion()
                + (Utils.isFromGooglePlay(getActivity()) ? " (Google Play)" : ""));
        dialogPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {

                Intent intent = new Intent(getActivity(), SettingActivity.class);
                intent.putExtra(AboutFragment.TAG_KEY, AboutFragment.TAG_KEY);
                ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(getActivity(), R.anim.slide_in_right, 0);
                ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
                return true;
            }
        });

        final Preference checkPreference = findPreference(HiSettingsHelper.PERF_LAST_UPDATE_CHECK);
        checkPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                //checkPreference.setSummary("上次检查 ：" + Utils.shortyTime(new Date()));
                //mUpdateHelper.check(getActivity(), false);
                return true;
            }
        });
//        Date lastCheckTime = mSettings.getLastUpdateCheckTime();
//        if (lastCheckTime != null) {
//            checkPreference.setSummary("上次检查 ：" + Utils.shortyTime(lastCheckTime));
//        } else {
//            checkPreference.setSummary("上次检查 ：- ");
//        }
        checkPreference.setSummary("目前更新检查无效，请查看客户端发布帖");

        Preference supportPreference = findPreference(HiSettingsHelper.PERF_SUPPORT);
        supportPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                FragmentUtils.show(getActivity(),
                        FragmentUtils.parseUrl(HiUtils.BaseUrl + "viewthread.php?tid=" + HiUtils.CLIENT_TID));
                return true;
            }
        });

    }

}
