package com.lichader.hipdanext.di;

import com.lichader.hipdanext.okhttp.CachedDns;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.ui.HiApplication;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/**
 * Access to app-wide Hilt bindings from code that Hilt cannot inject, such as the
 * legacy {@code getInstance()} methods kept while callers move to injection.
 */
@EntryPoint
@InstallIn(SingletonComponent.class)
public interface SingletonEntryPoint {

    OkHttpHelper okHttpHelper();

    CachedDns cachedDns();

    static SingletonEntryPoint get() {
        return EntryPointAccessors.fromApplication(HiApplication.getAppContext(), SingletonEntryPoint.class);
    }
}
