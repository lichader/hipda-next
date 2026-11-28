package com.lichader.hipdanext.di;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.lichader.hipdanext.okhttp.CachedDns;
import com.lichader.hipdanext.okhttp.LoggingInterceptor;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.okhttp.RetryIntercepter;
import com.lichader.hipdanext.utils.Logger;

import net.gotev.cookiestore.SharedPreferencesCookieStore;
import net.gotev.cookiestore.okhttp.JavaNetCookieJar;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/** HTTP client and cookie storage for forum requests. */
@Module
@InstallIn(SingletonComponent.class)
public final class NetworkModule {

    private static final long HTTP_CACHE_SIZE = 10 * 1024 * 1024;

    private NetworkModule() {
    }

    @Provides
    @Singleton
    static CookieStore provideCookieStore(@ApplicationContext Context context) {
        return new SharedPreferencesCookieStore(context, "cookie");
    }

    @Provides
    @Singleton
    static OkHttpClient provideOkHttpClient(@ApplicationContext Context context,
                                            CachedDns dns,
                                            CookieStore cookieStore,
                                            @ExtraInterceptors Set<Interceptor> extraInterceptors) {
        CookieManager cookieManager = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
        Cache cache = new Cache(Glide.getPhotoCacheDir(context, OkHttpHelper.CACHE_DIR_NAME), HTTP_CACHE_SIZE);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(OkHttpHelper.CONNECT_TIMEOUT_SECS, TimeUnit.SECONDS)
                .readTimeout(OkHttpHelper.READ_TIMEOUT_SECS, TimeUnit.SECONDS)
                .writeTimeout(OkHttpHelper.WRITE_TIMEOUT_SECS, TimeUnit.SECONDS)
                .dns(dns)
                .cache(cache)
                .cookieJar(new JavaNetCookieJar(cookieManager));

        // Debug builds add the response recorder here (debug source set); release adds none.
        // First, so it sees the final response after the retries below.
        for (Interceptor interceptor : extraInterceptors) {
            builder.addInterceptor(interceptor);
        }
        if (Logger.isDebug()) {
            builder.addInterceptor(new LoggingInterceptor());
        }
        builder.addInterceptor(new RetryIntercepter());
        return builder.build();
    }
}
