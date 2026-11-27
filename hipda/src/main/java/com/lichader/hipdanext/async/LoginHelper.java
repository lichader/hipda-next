package com.lichader.hipdanext.async;

import android.content.Context;
import android.text.TextUtils;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.okhttp.ParamsMap;
import com.lichader.hipdanext.service.NotiHelper;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Logger;
import com.lichader.hipdanext.utils.Utils;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class LoginHelper {

    private final static int MAX_RETRY_TIMES = 3;

    private final Context mContext;
    private final HiSettingsHelper mSettings;
    private final OkHttpHelper mOkHttpHelper;
    private final FavoriteHelper mFavoriteHelper;
    private final NotiHelper mNotiHelper;

    @Inject
    public LoginHelper(@ApplicationContext Context context,
                       HiSettingsHelper settings,
                       OkHttpHelper okHttpHelper,
                       FavoriteHelper favoriteHelper,
                       NotiHelper notiHelper) {
        mContext = context;
        mSettings = settings;
        mOkHttpHelper = okHttpHelper;
        mFavoriteHelper = favoriteHelper;
        mNotiHelper = notiHelper;
    }

    /** The error of one login attempt; a login runs on any thread, so it is not kept in a field. */
    private static final class ErrorHolder {
        String message = "";
    }

    public int login() {
        return login(false);
    }

    public int login(boolean manual) {
        if (!manual &&
                (TextUtils.isEmpty(mSettings.getUsername())
                        || TextUtils.isEmpty(mSettings.getPassword())))
            return Constants.STATUS_FAIL_ABORT;

        int status = Constants.STATUS_FAIL;
        ErrorHolder error = new ErrorHolder();

        LoginEvent event1 = new LoginEvent();
        event1.mManual = manual;
        event1.mStatus = Constants.STATUS_IN_PROGRESS;
        EventBus.getDefault().postSticky(event1);

        String formhash = getFormhash(error);
        if (TextUtils.isEmpty(formhash))
            return status;

        if (mSettings.isLoginInfoValid()) {
            for (int i = 1; i <= MAX_RETRY_TIMES; i++) {
                status = doLogin(formhash, error);
                if (status == Constants.STATUS_FAIL_ABORT) {
                    break;
                } else if (status == Constants.STATUS_SUCCESS) {
                    break;
                }
            }
        } else {
            error.message = "登录信息不完整";
        }

        LoginEvent event2 = new LoginEvent();
        event2.mManual = manual;
        event2.mStatus = status;
        event2.mMessage = error.message;
        EventBus.getDefault().postSticky(event2);

        return status;
    }

    private String getFormhash(ErrorHolder error) {
        String rstStr = null;
        try {
            rstStr = mOkHttpHelper.get(HiUtils.LoginGetFormHash);

            if (!TextUtils.isEmpty(rstStr)) {
                Document doc = Jsoup.parse(rstStr);

                Elements elements = doc.select("input[name=formhash]");
                Element element = elements.first();

                if (element == null) {
                    Elements alartES = doc.select("div.alert_info");
                    if (alartES.size() > 0) {
                        error.message = alartES.first().text();
                    } else {
                        error.message = "无法获取登录凭据";
                    }
                    return "";
                }
                return element.attr("value");
            }
        } catch (Exception e) {
            error.message = OkHttpHelper.getErrorMessage(e).getMessage();
        }
        return rstStr;
    }

    private int doLogin(String formhash, ErrorHolder error) {
        try {
            ParamsMap params = new ParamsMap();
            params.put("m_formhash", formhash);
            params.put("referer", HiUtils.BaseUrl + "index.php");
            params.put("loginfield", "username");
            params.put("username", mSettings.getUsername());
            params.put("password", processedPassword());
            params.put("questionid", mSettings.getSecQuestion());
            params.put("answer", mSettings.getSecAnswer());
            params.put("cookietime", "2592000");

            String rspStr = mOkHttpHelper.post(HiUtils.LoginSubmit, params);
            Logger.v(rspStr);

            // response is in XML format
            if (rspStr.contains(mContext.getString(R.string.login_success))) {
                Logger.v("Login SUCCESS!");
                return Constants.STATUS_SUCCESS;
            } else if (rspStr.contains(mContext.getString(R.string.login_fail))) {
                Logger.v("Login FAIL");
                int msgIndex = rspStr.indexOf(mContext.getString(R.string.login_fail));
                int msgIndexEnd = rspStr.indexOf("次", msgIndex) + 1;
                if (msgIndexEnd > msgIndex) {
                    error.message = rspStr.substring(msgIndex, msgIndexEnd);
                } else {
                    error.message = "登录失败,请检查账户信息";
                }
                return Constants.STATUS_FAIL_ABORT;
            } else {
                error.message = "登录失败,未知错误";
                return Constants.STATUS_FAIL_ABORT;
            }
        } catch (Exception e) {
            error.message = "登录失败 : " + OkHttpHelper.getErrorMessage(e);
            return Constants.STATUS_FAIL;
        }
    }

    public static boolean checkLoggedin(Document doc) {
        Element umenu = doc.select("div#umenu").first();
        return umenu != null && umenu.html().contains("space.php");
    }

    public boolean isLoggedIn() {
        return !TextUtils.isEmpty(mSettings.getUsername())
                && mOkHttpHelper.hasAuthCookie();
    }

    public void logout() {
        mSettings.setUsername("");
        mSettings.setPassword("");
        mSettings.setSecQuestion("");
        mSettings.setSecAnswer("");
        mSettings.setUid("");
        mNotiHelper.clearNotification();
        mOkHttpHelper.clearCookies();
        mFavoriteHelper.clearAll();
        mSettings.setBlacklists(new ArrayList<String>());
    }

    private String processedPassword() {
        String pass = mSettings.getPassword();
        if (TextUtils.isEmpty(pass) || pass.length() == 32)
            return pass;
        try {
            return Utils.md5(pass.replace("\\", "\\\\")
                    .replace("'", "\'")
                    .replace("\"", "\\\""));
        } catch (Exception e) {
            return pass;
        }
    }

}
