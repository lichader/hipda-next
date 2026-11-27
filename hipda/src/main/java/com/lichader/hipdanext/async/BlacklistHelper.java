package com.lichader.hipdanext.async;

import android.text.TextUtils;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.okhttp.ParamsMap;
import com.lichader.hipdanext.utils.HiParser;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Logger;
import com.lichader.hipdanext.utils.UIUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Request;

/**
 * Created by GreenSkinMonster on 2017-07-14.
 */
@Singleton
public class BlacklistHelper {

    private final HiSettingsHelper mSettings;
    private final OkHttpHelper mOkHttpHelper;

    @Inject
    public BlacklistHelper(HiSettingsHelper settings, OkHttpHelper okHttpHelper) {
        mSettings = settings;
        mOkHttpHelper = okHttpHelper;
    }

    public void addBlacklist(String formhash, final String username) {
        ParamsMap params = new ParamsMap();
        params.put("formhash", formhash);
        params.put("user", username);
        try {
            mOkHttpHelper.asyncPost(HiUtils.AddBlackUrl, params, new OkHttpHelper.ResultCallback() {
                @Override
                public void onError(Request request, Exception e) {
                    UIUtils.toast(OkHttpHelper.getErrorMessage(e).getMessage());
                }

                @Override
                public void onResponse(String response) {
                    try {
                        Document doc = Jsoup.parse(response);
                        Elements errors = doc.select("div.alert_error");
                        if (errors.size() > 0) {
                            Element el = errors.first();
                            el.select("a").remove();
                            UIUtils.toast(el.text());
                        } else {
                            mSettings.addToBlacklist(username);
                            UIUtils.toast("已经将用户 <" + username + "> 添加至黑名单");
                        }
                    } catch (Exception e) {
                        UIUtils.toast(OkHttpHelper.getErrorMessage(e).getMessage());
                    }
                }
            });
        } catch (Exception e) {
            UIUtils.toast(OkHttpHelper.getErrorMessage(e).getMessage());
        }
    }

    public void delBlacklist(final String formhash, final String username) {
        delBlacklist(formhash, username, new OkHttpHelper.ResultCallback() {
            @Override
            public void onError(Request request, Exception e) {
                UIUtils.toast(OkHttpHelper.getErrorMessage(e).getMessage());
            }

            @Override
            public void onResponse(String response) {
                try {
                    Document doc = Jsoup.parse(response);
                    String errorMsg = HiParser.parseErrorMessage(doc);
                    if (!TextUtils.isEmpty(errorMsg)) {
                        UIUtils.toast(errorMsg);
                    } else {
                        mSettings.removeFromBlacklist(username);
                        UIUtils.toast("已经将用户 <" + username + "> 从黑名单移除");
                    }
                } catch (Exception e) {
                    UIUtils.toast(OkHttpHelper.getErrorMessage(e).getMessage());
                }
            }
        });
    }

    public void delBlacklist(final String formhash, final String username, OkHttpHelper.ResultCallback callback) {
        ParamsMap params = new ParamsMap();
        params.put("formhash", formhash);
        params.put("user", username);
        try {
            mOkHttpHelper.asyncPost(HiUtils.DelBlackUrl, params, callback);
        } catch (Exception e) {
            callback.onError(null, e);
        }
    }

    public void getBlacklists(OkHttpHelper.ResultCallback callback) {
        mOkHttpHelper.asyncGet(HiUtils.ViewBlackUrl, callback);
    }

    public void syncBlacklists() {
        getBlacklists(new OkHttpHelper.ResultCallback() {
            @Override
            public void onError(Request request, Exception e) {
            }

            @Override
            public void onResponse(String response) {
                try {
                    Document doc = Jsoup.parse(response);
                    String errorMsg = HiParser.parseErrorMessage(doc);
                    if (TextUtils.isEmpty(errorMsg)) {
                        List<String> list = HiParser.parseBlacklist(doc);
                        Collections.sort(list);
                        mSettings.setBlacklists(list);
                        mSettings.setBlacklistSyncTime();
                    }
                } catch (Exception e) {
                    Logger.e(e);
                }
            }
        });
    }

    public String addBlacklist2(String formhash, String username) throws Exception {
        ParamsMap params = new ParamsMap();
        params.put("formhash", formhash);
        params.put("user", username);
        String response = mOkHttpHelper.post(HiUtils.AddBlackUrl, params);
        Document doc = Jsoup.parse(response);
        Elements errors = doc.select("div.alert_error");
        if (errors.size() > 0) {
            Element el = errors.first();
            el.select("a").remove();
            return el.text();
        } else {
            mSettings.addToBlacklist(username);
        }
        return "";
    }

}
