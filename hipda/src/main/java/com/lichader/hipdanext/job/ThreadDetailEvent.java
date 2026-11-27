package com.lichader.hipdanext.job;

import com.lichader.hipdanext.bean.DetailListBean;

public class ThreadDetailEvent extends BaseEvent {
    public DetailListBean mData;
    public int mFectchType;
    public int mPage;
    public int mLoadingPosition;
    public String mAuthorId;
}
