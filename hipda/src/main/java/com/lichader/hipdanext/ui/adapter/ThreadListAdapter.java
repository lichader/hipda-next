package com.lichader.hipdanext.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.RequestManager;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.bean.ThreadBean;
import com.lichader.hipdanext.ui.widget.ThreadItemLayout;

import androidx.recyclerview.widget.RecyclerView;

public class ThreadListAdapter extends BaseRvAdapter<ThreadBean> {

    private RequestManager mGlide;

    private final HiSettingsHelper mSettings;

    public ThreadListAdapter(RequestManager glide, HiSettingsHelper settings, RecyclerItemClickListener listener) {
        mSettings = settings;
        mGlide = glide;
        mItemClickListener = listener;
    }

    @Override
    public ViewHolderImpl onCreateViewHolderImpl(ViewGroup parent, int viewType) {
        return new ViewHolderImpl(new ThreadItemLayout(parent.getContext(), mSettings, mGlide));
    }

    @Override
    public void onBindViewHolderImpl(RecyclerView.ViewHolder viewHolder, int position) {
        ThreadBean thread = getItem(position);
        if (viewHolder instanceof ViewHolderImpl)
            ((ViewHolderImpl) viewHolder).mItemLayout.setData(thread);
    }

    private static class ViewHolderImpl extends RecyclerView.ViewHolder {
        ThreadItemLayout mItemLayout;

        ViewHolderImpl(View itemView) {
            super(itemView);
            mItemLayout = (ThreadItemLayout) itemView;
        }
    }

}
