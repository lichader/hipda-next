package com.lichader.hipdanext.job;

import com.lichader.hipdanext.bean.PostBean;

public class PostEvent extends BaseEvent {
    public PostBean mPostResult;
    public int mMode;
    public boolean fromQuickReply;
}
