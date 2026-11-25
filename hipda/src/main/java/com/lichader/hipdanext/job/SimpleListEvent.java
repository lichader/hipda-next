package com.lichader.hipdanext.job;

import com.lichader.hipdanext.bean.SimpleListBean;

/**
 * Created by GreenSkinMonster on 2016-04-07.
 */
public class SimpleListEvent extends BaseEvent {
    public SimpleListBean mData;
    public int mType;
    public String mExtra;
    public int mPage;
    public String mFormhash;
}
