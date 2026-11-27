package com.lichader.hipdanext.job;

import com.lichader.hipdanext.bean.SimpleListBean;

public class SimpleListEvent extends BaseEvent {
    public SimpleListBean mData;
    public int mType;
    public String mExtra;
    public int mPage;
    public String mFormhash;
}
