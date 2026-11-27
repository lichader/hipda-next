package com.lichader.hipdanext.job;

import android.content.Context;
import android.text.TextUtils;

import com.lichader.hipdanext.async.LoginHelper;
import com.lichader.hipdanext.bean.ThreadListBean;
import com.lichader.hipdanext.okhttp.NetworkError;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiParserThreadList;
import com.lichader.hipdanext.utils.HiUtils;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class ThreadListJob extends BaseJob {

    private Context mCtx;
    private int mForumId;
    private int mPage;

    private ThreadListEvent mEvent;

    public ThreadListJob(Context context, String sessionId, int forumId, int page) {
        super(sessionId);
        mCtx = context;
        mForumId = forumId;
        mPage = page;

        mEvent = new ThreadListEvent();
        mEvent.mSessionId = mSessionId;
        mEvent.mForumId = forumId;
        mEvent.mPage = page;
    }

    @Override
    public void onAdded() {
        mEvent.mStatus = Constants.STATUS_IN_PROGRESS;
        EventBus.getDefault().postSticky(mEvent);
    }

    @Override
    public void onRun() throws Throwable {
        ThreadListBean data = null;
        int eventStatus = Constants.STATUS_SUCCESS;
        String eventMessage = "";
        String eventDetail = "";

        try {
            Document doc = Jsoup.parse(fetchForumList());
            boolean loggedin = LoginHelper.checkLoggedin(doc);
            if (!loggedin) {
                int status = mServices.loginHelper().login();
                if (status == Constants.STATUS_FAIL_ABORT) {
                    eventStatus = Constants.STATUS_FAIL_RELOGIN;
                    eventMessage = "请重新登录";
                } else if (status == Constants.STATUS_SUCCESS) {
                    doc = Jsoup.parse(fetchForumList());
                    loggedin = LoginHelper.checkLoggedin(doc);
                }
            }
            if (loggedin) {
                data = HiParserThreadList.parse(mCtx, mSettings, mServices.notiHelper(), doc);
                if (!data.isParsed()) {
                    eventStatus = Constants.STATUS_FAIL_ABORT;
                    eventMessage = "页面加载失败";
                } else {
                    eventStatus = Constants.STATUS_SUCCESS;
                }
            }
        } catch (Exception e) {
            NetworkError networkError = OkHttpHelper.getErrorMessage(e);
            eventStatus = Constants.STATUS_FAIL;
            eventMessage = networkError.getMessage();
            eventDetail = networkError.getDetail();
        }

        mEvent.mData = data;
        mEvent.mStatus = eventStatus;
        mEvent.mMessage = eventMessage;
        mEvent.mDetail = eventDetail;
        EventBus.getDefault().postSticky(mEvent);
    }

    private String fetchForumList() throws Exception {
        String mUrl = HiUtils.ThreadListUrl + mForumId + "&page=" + mPage;
        if (mForumId == HiUtils.FID_BS && TextUtils.isDigitsOnly(mSettings.getBSTypeId())) {
            mUrl += "&filter=type&typeid=" + mSettings.getBSTypeId();
        }
        if (mSettings.isSortByPostTime(mForumId)) {
            mUrl += "&orderby=dateline";
        }
        return mOkHttpHelper.get(mUrl, mSessionId);
    }
}
