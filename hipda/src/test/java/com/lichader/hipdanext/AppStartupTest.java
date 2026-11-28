package com.lichader.hipdanext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.lichader.hipdanext.data.favorites.FavoritesCache;
import com.lichader.hipdanext.forum.lists.SavedThreads;
import com.lichader.hipdanext.async.LoginHelper;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.db.History;
import com.lichader.hipdanext.db.HistoryDao;
import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.okhttp.RetryIntercepter;
import com.lichader.hipdanext.service.NotiWorker;
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
        SingletonEntryPoint hilt = SingletonEntryPoint.get();
        assertSame(hilt.okHttpHelper(), hilt.okHttpHelper());
        assertSame(hilt.cachedDns(), hilt.cachedDns());
        assertSame(hilt.settings(), hilt.settings());
        assertSame(hilt.favoritesCache(), hilt.favoritesCache());
    }

    @Test
    public void asyncHelpersComeFromHilt() {
        SingletonEntryPoint hilt = SingletonEntryPoint.get();
        assertSame(hilt.loginHelper(), hilt.loginHelper());
        assertSame(hilt.notiHelper(), hilt.notiHelper());
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
    public void notiWorkerIsCreatedByHilt() {
        Context app = ApplicationProvider.getApplicationContext();
        NotiWorker worker = TestListenableWorkerBuilder.from(app, NotiWorker.class)
                .setWorkerFactory(((HiApplication) app).getWorkManagerConfiguration().getWorkerFactory())
                .build();
        // Logged out, so the worker checks nothing and succeeds offline.
        SingletonEntryPoint.get().loginHelper().logout();
        assertEquals(ListenableWorker.Result.success(), worker.doWork());
    }

    @Test
    public void historyIsSavedThroughHilt() {
        HistoryDao history = SingletonEntryPoint.get().historyDao();
        assertSame(history, SingletonEntryPoint.get().historyDao());
        history.saveHistoryInBackground("424242", "2", "smoke", "1", "someone", "");
        shadowOf(Looper.getMainLooper()).idle();
        boolean found = false;
        for (History h : history.getHistories()) {
            if ("424242".equals(h.getTid())) found = true;
        }
        assertTrue("saved thread must be in the history", found);
    }

    @Test
    public void favoritesCacheRoundTrip() {
        FavoritesCache favorites = SingletonEntryPoint.get().favoritesCache();
        favorites.add(SavedThreads.FAVORITES, "12345");
        assertTrue(favorites.contains(SavedThreads.FAVORITES, "12345"));
        favorites.remove(SavedThreads.FAVORITES, "12345");
        assertFalse(favorites.contains(SavedThreads.FAVORITES, "12345"));
    }

    @Test
    public void settingsKeepTextSizeAdjustment() {
        HiSettingsHelper settings = SingletonEntryPoint.get().settings();
        int base = settings.getPostTextSize() - settings.getPostTextSizeAdj();
        int old = settings.getPostTextSizeAdj();
        settings.setPostTextSizeAdj(old + 2);
        assertEquals(base + old + 2, settings.getPostTextSize());
        settings.setPostTextSizeAdj(old);
    }

    @Test
    public void httpClientIsConfigured() {
        OkHttpClient client = SingletonEntryPoint.get().okHttpHelper().getClient();
        assertSame(SingletonEntryPoint.get().cachedDns(), client.dns());
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
        OkHttpHelper http = SingletonEntryPoint.get().okHttpHelper();
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
        HiSettingsHelper settings = SingletonEntryPoint.get().settings();
        String old = settings.getStringValue(HiSettingsHelper.PERF_TAILTEXT, "");
        settings.setStringValue(HiSettingsHelper.PERF_TAILTEXT, "smoke");
        assertEquals("smoke", settings.getStringValue(HiSettingsHelper.PERF_TAILTEXT, ""));
        settings.setStringValue(HiSettingsHelper.PERF_TAILTEXT, old);
    }
}
