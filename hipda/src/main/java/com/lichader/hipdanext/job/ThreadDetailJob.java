package com.lichader.hipdanext.job;

import android.content.Context;
import android.text.TextUtils;

import com.lichader.hipdanext.async.ThreadDetailLoader;
import com.lichader.hipdanext.async.ThreadLoad;
import com.lichader.hipdanext.bean.DetailListBean;
import com.lichader.hipdanext.ui.ThreadDetailFragment;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiUtils;

import org.greenrobot.eventbus.EventBus;

public class ThreadDetailJob extends BaseJob {

    public final static String FIND_AUTHOR_ID = "-1";
    private final static int MIN_JOB_TIME_MS = 150;

    private String mTid;
    private String mAuthorId;
    private String mGotoPostId;
    private int mPage;
    private int mFetchType;

    private ThreadDetailEvent mEvent;

    public ThreadDetailJob(Context context, String sessionId, String tid, String authorId, String gotoPostId, int page, int fetchType, int loadingPosition) {
        super(sessionId);
        mTid = tid;
        mAuthorId = authorId;
        mGotoPostId = gotoPostId;
        mPage = page;
        mFetchType = fetchType;

        mEvent = new ThreadDetailEvent();
        mEvent.mSessionId = mSessionId;
        mEvent.mFectchType = fetchType;
        mEvent.mPage = page;
        mEvent.mLoadingPosition = loadingPosition;
    }

    @Override
    public void onAdded() {
        mEvent.mStatus = Constants.STATUS_IN_PROGRESS;
        EventBus.getDefault().postSticky(mEvent);
    }

    @Override
    public void onRun() throws Throwable {
        long start = System.currentTimeMillis();
        ThreadDetailLoader loader = mServices.threadDetailLoader();
        ThreadLoad load;
        if (!TextUtils.isEmpty(mGotoPostId)) {
            load = loader.pageOfPost(mGotoPostId, mTid);
        } else if (mPage == ThreadDetailFragment.LAST_PAGE) {
            load = loader.lastPage(mTid);
        } else {
            if (FIND_AUTHOR_ID.equals(mAuthorId)) {
                mAuthorId = loader.authorId(mTid);
            }
            load = loader.page(mTid, mPage, mAuthorId);
        }
        DetailListBean data = load.getData();
        int eventStatus = load.getStatus();
        String eventMessage = load.getMessage();
        String eventDetail = "";

        long delta = System.currentTimeMillis() - start;
        if (delta < MIN_JOB_TIME_MS) {
            Thread.sleep(MIN_JOB_TIME_MS - delta);
        }

        mEvent.mData = data;
        mEvent.mStatus = eventStatus;
        mEvent.mMessage = eventMessage;
        mEvent.mDetail = eventDetail;
        mEvent.mAuthorId = mAuthorId;
        EventBus.getDefault().postSticky(mEvent);

        if (data != null && data.getPage() == data.getLastPage()
                && mAuthorId == null
                && HiUtils.isForumValid(data.getFid()) && data.getTid() != null) {
            ThreadUpdatedEvent tuEvent = new ThreadUpdatedEvent();
            tuEvent.mFid = data.getFid();
            tuEvent.mTid = data.getTid();
            tuEvent.mTitle = data.getTitle();
            tuEvent.mReplyCount = data.getAll().get(data.getCount() - 1).getFloor() - 1;
            EventBus.getDefault().postSticky(tuEvent);
        }
    }
}
