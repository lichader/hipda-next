package com.lichader.hipdanext.ui;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vanniktech.emoji.EmojiPopup;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.utils.UIUtils;
import com.lichader.hipdanext.utils.Utils;

import java.util.UUID;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import io.github.inflationx.viewpump.ViewPumpContextWrapper;
import javax.inject.Inject;

public class BaseActivity extends AppCompatActivity {

    @Inject
    protected HiSettingsHelper mSettings;

    public String mSessionId;
    protected View mRootView;
    protected View mMainFrameContainer;
    protected Toolbar mToolbar;
    protected AppBarLayout mAppBarLayout;
    protected FloatingActionButton mMainFab;
    protected FloatingActionButton mNotiificationFab;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSessionId = UUID.randomUUID().toString();
        UIUtils.setActivityTheme(this, mSettings);
        getOnBackPressedDispatcher().addCallback(this, mBackCallback);

        try {
            if (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT == mSettings.getScreenOrietation()) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE == mSettings.getScreenOrietation()) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
            }
        } catch (Exception ignored) {
            //avoid android 8.0 bug
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        UIUtils.applySystemBarInsets(this);
        if (mAppBarLayout != null) {
            if (UIUtils.isWhiteTheme(this, mSettings)) {
                mAppBarLayout.setStateListAnimator(null);
                mAppBarLayout.setElevation(Utils.dpToPx(2));
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        if (HiApplication.isFontSet())
            super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase));
        else
            super.attachBaseContext(newBase);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void updateAppBarScrollFlag() {
        setAppBarCollapsible(mSettings.isAppBarCollapsible());
    }

    protected void setAppBarCollapsible(boolean collapsible) {
        AppBarLayout.LayoutParams params =
                (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        if (collapsible) {
            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                    | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                    | AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP);
        } else {
            params.setScrollFlags(0);
        }
        mToolbar.setLayoutParams(params);
    }

    public FloatingActionButton getMainFab() {
        return mMainFab;
    }

    public FloatingActionButton getNotificationFab() {
        return mNotiificationFab;
    }

    public EmojiPopup.Builder getEmojiBuilder() {
        return EmojiPopup.Builder.fromRootView(mRootView);
    }

    public View getRootView() {
        return mRootView;
    }

    public View getToolbar() {
        return mToolbar;
    }

    public void expandAppBar() {
        if (mAppBarLayout != null && mSettings.isAppBarCollapsible()) {
            mAppBarLayout.setExpanded(true, true);
        }
    }

    private final OnBackPressedCallback mBackCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            handleBackPressed();
        }
    };

    /**
     * Back handling for this activity, dispatched through {@link androidx.activity.OnBackPressedDispatcher}
     * so it keeps working when the system no longer calls {@code onBackPressed()} (target SDK 33+ with
     * predictive back, enforced from target SDK 36).
     */
    protected void handleBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_frame_container);
        if (fragment instanceof BaseFragment) {
            if (!((BaseFragment) fragment).onBackPressed()) {
                finish();
            }
        } else {
            dispatchDefaultBack();
        }
    }

    /**
     * Runs the default back behavior (fragment back stack, then finishing the activity),
     * skipping this activity's own callback.
     */
    protected void dispatchDefaultBack() {
        mBackCallback.setEnabled(false);
        try {
            getOnBackPressedDispatcher().onBackPressed();
        } finally {
            mBackCallback.setEnabled(true);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_right);
    }

    public void finishWithNoSlide() {
        super.finish();
        overridePendingTransition(0, R.anim.activity_close_exit);
    }

    public void finishWithDefault() {
        super.finish();
    }

}
