package com.lichader.hipdanext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lichader.hipdanext.async.FavoriteHelper;
import com.lichader.hipdanext.async.LoginHelper;
import com.lichader.hipdanext.bean.PostBean;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.okhttp.CachedDns;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.okhttp.RetryIntercepter;
import com.lichader.hipdanext.ui.HiApplication;
import com.lichader.hipdanext.utils.HiUtils;

import org.junit.Test;

import dagger.hilt.internal.GeneratedComponentManagerHolder;
import org.junit.runner.RunWith;

import java.util.Collections;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/**
 * Smoke tests for application start-up and the app-wide services it wires together.
 * They guard the move from hand-written singletons to dependency injection.
 */
@RunWith(AndroidJUnit4.class)
public class AppStartupTest {

    @Test
    public void applicationStartsAndExposesContext() {
        Context app = ApplicationProvider.getApplicationContext();
        assertTrue(app instanceof HiApplication);
        assertNotNull(HiApplication.getAppContext());
        assertNotNull(HiUtils.BaseUrl);
        assertTrue(HiUtils.BaseUrl.endsWith("/forum/"));
    }

    @Test
    public void applicationHostsHiltComponent() {
        Context app = ApplicationProvider.getApplicationContext();
        assertTrue(app instanceof GeneratedComponentManagerHolder);
        assertNotNull(((GeneratedComponentManagerHolder) app).componentManager().generatedComponent());
    }

    @Test
    public void coreServicesAreSingletons() {
        assertSame(OkHttpHelper.getInstance(), OkHttpHelper.getInstance());
        assertSame(CachedDns.getInstance(), CachedDns.getInstance());
        assertSame(HiSettingsHelper.getInstance(), HiSettingsHelper.getInstance());
        assertSame(FavoriteHelper.getInstance(), FavoriteHelper.getInstance());
    }

    @Test
    public void networkServicesComeFromHilt() {
        SingletonEntryPoint hilt = SingletonEntryPoint.get();
        assertSame(hilt.okHttpHelper(), OkHttpHelper.getInstance());
        assertSame(hilt.cachedDns(), CachedDns.getInstance());
        assertSame(hilt.okHttpHelper(), hilt.okHttpHelper());
    }

    @Test
    public void settingsAndFavoritesComeFromHilt() {
        SingletonEntryPoint hilt = SingletonEntryPoint.get();
        assertSame(hilt.settings(), HiSettingsHelper.getInstance());
        assertSame(hilt.favorites(), FavoriteHelper.getInstance());
    }

    @Test
    public void asyncHelpersComeFromHilt() {
        SingletonEntryPoint hilt = SingletonEntryPoint.get();
        assertSame(hilt.loginHelper(), hilt.loginHelper());
        assertSame(hilt.notiHelper(), hilt.notiHelper());
        PostBean post = new PostBean();
        post.setTid("1");
        assertNotNull(hilt.postHelperFactory().create(0, null, post));
    }

    @Test
    public void logoutClearsLoginState() {
        SingletonEntryPoint hilt = SingletonEntryPoint.get();
        LoginHelper login = hilt.loginHelper();
        hilt.settings().setUsername("someone");
        hilt.notiHelper().getCurrentNotification().setSmsCount(3);
        login.logout();
        assertFalse(login.isLoggedIn());
        assertEquals("", hilt.settings().getUsername());
        assertEquals(0, hilt.notiHelper().getCurrentNotification().getSmsCount());
    }

    @Test
    public void favoritesCacheRoundTrip() {
        FavoriteHelper favorites = FavoriteHelper.getInstance();
        favorites.addToCahce(FavoriteHelper.TYPE_FAVORITE, "12345");
        assertTrue(favorites.isInFavorite("12345"));
        favorites.removeFromCahce(FavoriteHelper.TYPE_FAVORITE, "12345");
        assertFalse(favorites.isInFavorite("12345"));
    }

    @Test
    public void settingsKeepTextSizeAdjustment() {
        HiSettingsHelper settings = HiSettingsHelper.getInstance();
        int base = settings.getPostTextSize() - settings.getPostTextSizeAdj();
        int old = settings.getPostTextSizeAdj();
        settings.setPostTextSizeAdj(old + 2);
        assertEquals(base + old + 2, settings.getPostTextSize());
        settings.setPostTextSizeAdj(old);
    }

    @Test
    public void httpClientIsConfigured() {
        OkHttpClient client = OkHttpHelper.getInstance().getClient();
        assertSame(CachedDns.getInstance(), client.dns());
        assertNotNull(client.cache());
        assertEquals(OkHttpHelper.CONNECT_TIMEOUT_SECS * 1000, client.connectTimeoutMillis());
        assertEquals(OkHttpHelper.READ_TIMEOUT_SECS * 1000, client.readTimeoutMillis());
        assertEquals(OkHttpHelper.WRITE_TIMEOUT_SECS * 1000, client.writeTimeoutMillis());
        boolean hasRetry = false;
        for (Interceptor i : client.interceptors()) {
            if (i instanceof RetryIntercepter) hasRetry = true;
        }
        assertTrue("RetryIntercepter must be installed", hasRetry);
    }

    @Test
    public void authCookieIsStoredAndCleared() {
        OkHttpHelper http = OkHttpHelper.getInstance();
        http.clearCookies();
        assertFalse(http.hasAuthCookie());

        HttpUrl url = HttpUrl.get("https://www.4d4y.com/forum/index.php");
        Cookie auth = new Cookie.Builder()
                .name("cdb_auth").value("token123").domain("www.4d4y.com").path("/").build();
        http.getClient().cookieJar().saveFromResponse(url, Collections.singletonList(auth));

        assertTrue(http.hasAuthCookie());
        assertEquals("token123", http.getAuthCookie());

        http.clearCookies();
        assertFalse(http.hasAuthCookie());
    }

    @Test
    public void settingsPersistToSharedPreferences() {
        HiSettingsHelper settings = HiSettingsHelper.getInstance();
        String old = settings.getStringValue(HiSettingsHelper.PERF_TAILTEXT, "");
        settings.setStringValue(HiSettingsHelper.PERF_TAILTEXT, "smoke");
        assertEquals("smoke", settings.getStringValue(HiSettingsHelper.PERF_TAILTEXT, ""));
        settings.setStringValue(HiSettingsHelper.PERF_TAILTEXT, old);
    }
}
