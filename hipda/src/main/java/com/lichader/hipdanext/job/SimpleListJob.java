package com.lichader.hipdanext.job;

import android.content.Context;

import com.lichader.hipdanext.async.LoginHelper;
import com.lichader.hipdanext.bean.SimpleListBean;
import com.lichader.hipdanext.okhttp.NetworkError;
import com.lichader.hipdanext.okhttp.OkHttpHelper;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiParser;
import com.lichader.hipdanext.utils.HiUtils;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


public class SimpleListJob extends BaseJob {

    public static final int TYPE_MYREPLY = 0;
    public static final int TYPE_MYPOST = 1;
    public static final int TYPE_SEARCH = 2;
    public static final int TYPE_SMS = 3;
    public static final int TYPE_THREAD_NOTIFY = 4;
    public static final int TYPE_SMS_DETAIL = 5;
    public static final int TYPE_FAVORITES = 6;
    public static final int TYPE_SEARCH_USER_THREADS = 7;
    public static final int TYPE_ATTENTION = 8;
    public static final int TYPE_HISTORIES = 9;
    public static final int TYPE_NEW_POSTS = 10;

    private Context mCtx;
    private int mType;
    private int mPage = 1;
    private String mExtra = "";

    private SimpleListEvent mEvent;

    public SimpleListJob(Context context, String sessionId, int type, int page, String extra) {
        super(sessionId);
        mCtx = context;
        mType = type;
        mPage = page;
        mExtra = extra;

        mEvent = new SimpleListEvent();
        mEvent.mSessionId = mSessionId;
        mEvent.mPage = page;
        mEvent.mType = mType;
        mEvent.mExtra = mExtra;
    }

    @Override
    public void onAdded() {
        mEvent.mStatus = Constants.STATUS_IN_PROGRESS;
        EventBus.getDefault().postSticky(mEvent);
    }

    @Override
    public void onRun() throws Throwable {
        SimpleListBean data = null;

        int eventStatus = Constants.STATUS_SUCCESS;
        String eventMessage = "";
        String eventDetail = "";
        String formhash = "";

        try {
            Document doc = Jsoup.parse(fetchSimpleList(mType));
            boolean loggedin = LoginHelper.checkLoggedin(doc);
            if (!loggedin) {
                int status = mServices.loginHelper().login();
                if (status == Constants.STATUS_FAIL_ABORT) {
                    eventStatus = Constants.STATUS_FAIL_RELOGIN;
                    eventMessage = "请重新登录";
                } else if (status == Constants.STATUS_SUCCESS) {
                    doc = Jsoup.parse(fetchSimpleList(mType));
                    loggedin = LoginHelper.checkLoggedin(doc);
                }
            }
            if (loggedin) {
                data = HiParser.parseSimpleList(mCtx, mServices.notiHelper(), mType, doc);
                formhash = HiParser.parseFormhash(doc);
                eventStatus = Constants.STATUS_SUCCESS;
            }
        } catch (Exception e) {
            NetworkError message = OkHttpHelper.getErrorMessage(e);
            eventStatus = Constants.STATUS_FAIL;
            eventMessage = message.getMessage();
            eventDetail = message.getDetail();
        }

        mEvent.mData = data;
        mEvent.mStatus = eventStatus;
        mEvent.mMessage = eventMessage;
        mEvent.mDetail = eventDetail;
        mEvent.mFormhash = formhash;
        EventBus.getDefault().postSticky(mEvent);
    }

    private String fetchSimpleList(int type) throws Exception {
        String url = null;
        switch (type) {
            case TYPE_SMS_DETAIL:
                url = HiUtils.SMSDetailUrl + mExtra;
                break;
            default:
                break;
        }
        return mOkHttpHelper.get(url, mSessionId);
    }
}
