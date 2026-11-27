package com.lichader.hipdanext.di;

import com.lichader.hipdanext.async.FavoriteHelper;
import com.lichader.hipdanext.async.LoginHelper;
import com.lichader.hipdanext.async.PostHelper;
import com.lichader.hipdanext.async.UploadImgHelper;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.db.HistoryDao;
import com.lichader.hipdanext.okhttp.CachedDns;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.service.NotiHelper;
import com.lichader.hipdanext.ui.HiApplication;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/**
 * Access to app-wide Hilt bindings from code that Hilt cannot inject: jobs, views
 * inflated from layouts and static helpers. Prefer injection or passing a service in.
 */
@EntryPoint
@InstallIn(SingletonComponent.class)
public interface SingletonEntryPoint {

    OkHttpHelper okHttpHelper();

    CachedDns cachedDns();

    HiSettingsHelper settings();

    FavoriteHelper favorites();

    HistoryDao historyDao();

    LoginHelper loginHelper();

    NotiHelper notiHelper();

    PostHelper.Factory postHelperFactory();

    UploadImgHelper.Factory uploadImgHelperFactory();

    static SingletonEntryPoint get() {
        return EntryPointAccessors.fromApplication(HiApplication.getAppContext(), SingletonEntryPoint.class);
    }
}
