package com.lichader.hipdanext.okhttp;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.ui.HiApplication;
import com.lichader.hipdanext.utils.Connectivity;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Logger;
import com.lichader.hipdanext.utils.Utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * helper class for okhttp
 * Created by GreenSkinMonster on 2015-10-22.
 */
@Singleton
public class OkHttpHelper {

    public final static int CONNECT_TIMEOUT_SECS = 10;
    public final static int READ_TIMEOUT_SECS = 10;
    public final static int WRITE_TIMEOUT_SECS = 30;

    public final static int FORCE_NETWORK = 1;
    public final static int FORCE_CACHE = 2;
    public final static int PREFER_CACHE = 3;

    public final static String CACHE_DIR_NAME = "okhttp";

    public static final String ERROR_CODE_PREFIX = "Unexpected code ";

    private final OkHttpClient mClient;
    private final CookieStore mCookiestore;
    private final HiSettingsHelper mSettings;

    private final Handler handler;

    private final static CacheControl PREFER_CACHE_CTL = new CacheControl.Builder()
            .maxStale(3 * 60, TimeUnit.SECONDS)
            .build();

    @Inject
    public OkHttpHelper(OkHttpClient client, CookieStore cookieStore, HiSettingsHelper settings) {
        mClient = client;
        mCookiestore = cookieStore;
        mSettings = settings;
        handler = new Handler(Looper.getMainLooper());
    }

    public OkHttpClient getClient() {
        return mClient;
    }

    private Request buildGetRequest(String url, Object tag, CacheControl cacheControl) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", HiUtils.getUserAgent());

        if (cacheControl != null)
            builder.cacheControl(cacheControl);

        if (tag != null)
            builder.tag(tag);

        return builder.build();
    }

    private Request buildPostFormRequest(String url, ParamsMap params, Object tag)
            throws UnsupportedEncodingException {

        FormBody.Builder builder = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                for (String value : entry.getValue()) {
                    builder.addEncoded(entry.getKey(),
                            URLEncoder.encode(value, mSettings.getEncode()));
                }
            }
        }

        RequestBody requestBody = builder.build();
        Request.Builder reqBuilder = new Request.Builder();
        reqBuilder.url(url)
                .header("User-Agent", HiUtils.getUserAgent())
                .post(requestBody)
                .cacheControl(CacheControl.FORCE_NETWORK);

        if (tag != null)
            reqBuilder.tag(tag);

        return reqBuilder.build();
    }

    public String get(String url) throws IOException {
        return get(url, null);
    }

    public Response getAsResponse(String url) throws IOException {
        Request request = buildGetRequest(url, null, CacheControl.FORCE_NETWORK);

        Call call = mClient.newCall(request);
        return call.execute();
    }

    public String get(String url, String tag) throws IOException {
        return get(url, tag, FORCE_NETWORK);
    }

    public String get(String url, String tag, int cacheType) throws IOException {
        Request request = buildGetRequest(url, tag, getCacheControl(cacheType));

        Call call = mClient.newCall(request);
        Response response = call.execute();

        return getResponseBody(response);
    }

    public void asyncGet(String url, ResultCallback callback) {
        asyncGet(url, FORCE_NETWORK, callback, null);
    }

    public void asyncGet(String url, int cacheType, ResultCallback callback) {
        asyncGet(url, cacheType, callback, null);
    }

    public void asyncGet(String url, int cacheType, ResultCallback callback, Object tag) {
        if (callback == null) callback = DEFAULT_CALLBACK;
        final ResultCallback rspCallBack = callback;

        Request request = buildGetRequest(url, tag, getCacheControl(cacheType));
        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleFailureCallback(call.request(), e, rspCallBack);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String body = getResponseBody(response);
                    handleSuccessCallback(body, rspCallBack);
                } catch (IOException e) {
                    handleFailureCallback(response.request(), e, rspCallBack);
                }
            }
        });
    }

    public String post(String url, ParamsMap params) throws IOException {
        Request request = buildPostFormRequest(url, params, null);
        Response response = mClient.newCall(request).execute();
        return getResponseBody(response);
    }

    public Response postAsResponse(String url, ParamsMap params) throws IOException {
        Request request = buildPostFormRequest(url, params, null);
        return mClient.newCall(request).execute();
    }

    public void asyncPost(String url, ParamsMap params, ResultCallback callback)
            throws UnsupportedEncodingException {
        if (callback == null) callback = DEFAULT_CALLBACK;
        final ResultCallback rspCallBack = callback;

        Request request = buildPostFormRequest(url, params, null);
        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleFailureCallback(call.request(), e, rspCallBack);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String body = getResponseBody(response);
                    handleSuccessCallback(body, rspCallBack);
                } catch (IOException e) {
                    handleFailureCallback(response.request(), e, rspCallBack);
                }
            }
        });
    }


    public String getResponseBody(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException(ERROR_CODE_PREFIX + response.code() + ", " + response.message());
        }

        String encoding = mSettings.getEncode();
        String contextType = response.headers().get("Content-Type");
        if (!TextUtils.isEmpty(contextType)) {
            if (contextType.toUpperCase().contains("UTF")) {
                encoding = "UTF-8";
            } else if (contextType.toUpperCase().contains("GBK")) {
                encoding = "GBK";
            }
        }
        return new String(response.body().bytes(), encoding);
    }

    private void handleFailureCallback(final Request request, final Exception e, final ResultCallback callback) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Logger.e(e.getClass().getName() + "\n" + e.getMessage());
                callback.onError(request, e);
            }
        });
    }

    private void handleSuccessCallback(final String response, final ResultCallback callback) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onResponse(response);
            }
        });
    }

    public interface ResultCallback {

        void onError(Request request, Exception e);

        void onResponse(String response);

    }

    private final ResultCallback DEFAULT_CALLBACK = new ResultCallback() {
        @Override
        public void onError(Request request, Exception e) {
        }

        @Override
        public void onResponse(String response) {
        }
    };

    public static NetworkError getErrorMessage(Exception e) {
        int errCode = 0;
        String msg = e.getClass().getSimpleName();
        if (HiApplication.getAppContext() != null
                && !Connectivity.isConnected(HiApplication.getAppContext())) {
            msg = "请检查网络连接";
        } else if (e instanceof UnknownHostException) {
            msg = "请检查网络连接.";
        } else if (e instanceof SocketTimeoutException) {
            msg = "请求超时";
        } else if (e instanceof IOException) {
            String emsg = e.getMessage();
            if (emsg != null && emsg.contains(ERROR_CODE_PREFIX)) {
                errCode = Utils.parseInt(Utils.getMiddleString(emsg, ERROR_CODE_PREFIX, ",").trim());
                if (errCode > 0)
                    msg = "错误代码 (" + errCode + ")";
            }
        }
        String errorMsg = e.getClass().getName()
                + "\n" + e.getMessage();
        // A static helper with many callers, so it looks the settings up itself.
        if (SingletonEntryPoint.get().settings().isErrorReportMode())
            errorMsg += "\n" + Utils.getStackTrace(e);
        return new NetworkError(errCode, msg, errorMsg);
    }

    public void clearCookies() {
        if (mCookiestore != null)
            mCookiestore.removeAll();
    }

    public boolean hasAuthCookie() {
        List<HttpCookie> cookies = mCookiestore.getCookies();
        for (HttpCookie cookie : cookies) {
            if ("cdb_auth".equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    public String getAuthCookie() {
        List<HttpCookie> cookies = mCookiestore.getCookies();
        for (HttpCookie cookie : cookies) {
            if ("cdb_auth".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private CacheControl getCacheControl(int cacheType) {
        if (cacheType == FORCE_NETWORK) {
            return CacheControl.FORCE_NETWORK;
        } else if (cacheType == FORCE_CACHE) {
            return CacheControl.FORCE_CACHE;
        } else if (cacheType == PREFER_CACHE) {
            return PREFER_CACHE_CTL;
        }
        return null;
    }

    public void cancel(Object tag) {
        for (Call call : mClient.dispatcher().queuedCalls()) {
            if (tag.equals(call.request().tag())) call.cancel();
        }
        for (Call call : mClient.dispatcher().runningCalls()) {
            if (tag.equals(call.request().tag())) call.cancel();
        }
    }

}
