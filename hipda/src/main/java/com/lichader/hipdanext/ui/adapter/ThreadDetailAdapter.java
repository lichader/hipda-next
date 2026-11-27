package com.lichader.hipdanext.ui.adapter;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.bean.ContentAbs;
import com.lichader.hipdanext.bean.ContentAttach;
import com.lichader.hipdanext.bean.ContentGoToFloor;
import com.lichader.hipdanext.bean.ContentImg;
import com.lichader.hipdanext.bean.ContentQuote;
import com.lichader.hipdanext.bean.ContentText;
import com.lichader.hipdanext.bean.DetailBean;
import com.lichader.hipdanext.bean.DetailListBean;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.bean.PollBean;
import com.lichader.hipdanext.bean.PollOptionBean;
import com.lichader.hipdanext.cache.ImageContainer;
import com.lichader.hipdanext.glide.GlideHelper;
import com.lichader.hipdanext.job.JobMgr;
import com.lichader.hipdanext.ui.ThreadDetailFragment;
import com.lichader.hipdanext.ui.widget.OnSingleClickListener;
import com.lichader.hipdanext.ui.widget.TextViewWithEmoticon;
import com.lichader.hipdanext.ui.widget.ThreadImageLayout;
import com.lichader.hipdanext.utils.ColorHelper;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.Logger;
import com.lichader.hipdanext.utils.UIUtils;
import com.lichader.hipdanext.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Created by GreenSkinMonster on 2016-11-08.
 */

public class ThreadDetailAdapter extends BaseRvAdapter<DetailBean> {

    private final Context mCtx;
    private final LayoutInflater mInflater;
    private final Button.OnClickListener mGoToFloorListener;
    private final View.OnClickListener mAvatarListener;
    private final View.OnClickListener mWarningListener;
    private final View.OnClickListener mVotePollListener;
    private final ThreadDetailFragment mDetailFragment;
    private final int mBackgroundResource;
    private final int mBackgroundColor;

    final private SparseArray<DetailListBean> mThreadPages = new SparseArray<>();
    private int mDataSize = 0;

    private WeakHashMap<CompoundButton, Object> mPollOptionsHolder;
    private WeakReference<TextView> mVoteButtonHolder;

    private final HiSettingsHelper mSettings;
    private final JobMgr mJobMgr;

    public ThreadDetailAdapter(ThreadDetailFragment detailFragment,
                               HiSettingsHelper settings,
                               JobMgr jobMgr,
                               RecyclerItemClickListener itemClickListener,
                               Button.OnClickListener gotoFloorListener,
                               View.OnClickListener avatarListener,
                               View.OnClickListener warningListener,
                               View.OnClickListener votePollListener) {
        mSettings = settings;
        mJobMgr = jobMgr;
        mCtx = detailFragment.getActivity();
        mInflater = LayoutInflater.from(mCtx);
        mItemClickListener = itemClickListener;
        mGoToFloorListener = gotoFloorListener;
        mAvatarListener = avatarListener;
        mWarningListener = warningListener;
        mVotePollListener = votePollListener;
        mDetailFragment = detailFragment;

        int[] attrs = new int[]{R.attr.selectableItemBackground};
        TypedArray typedArray = mCtx.obtainStyledAttributes(attrs);
        mBackgroundResource = typedArray.getResourceId(0, 0);
        typedArray.recycle();

        mBackgroundColor = UIUtils.isInLightThemeMode(mCtx)
                ? R.color.md_green_50 : R.color.md_blue_grey_900;
    }

    @Override
    public int getItemCount() {
        return mDataSize + getHeaderCount() + getFooterCount();
    }

    public int getDataCount() {
        return mDataSize;
    }

    private void cacheDataSize() {
        int size = 0;
        for (int i = 0; i < mThreadPages.size(); i++) {
            DetailListBean detailBeans = mThreadPages.get(mThreadPages.keyAt(i));
            size += detailBeans.getCount();
        }
        mDataSize = size;
    }

    @Override
    public void setDatas(List<DetailBean> datas) {
        throw new RuntimeException("Don't set datas here");
    }

    public synchronized void addDatas(DetailListBean detailListBean) {
        final int page = detailListBean.getPage();
        if (mThreadPages.size() == 0) {
            mThreadPages.put(page, detailListBean);
            cacheDataSize();
            notifyItemRangeInserted(getHeaderCount(), detailListBean.getCount());
            Logger.v("page range " + 0 + " - " + 0 + ", insert new page " + page);
            return;
        }
        int firstPage = mThreadPages.keyAt(0);
        int lastPage = mThreadPages.keyAt(mThreadPages.size() - 1);
        if (page == firstPage - 1) {
            mThreadPages.put(page, detailListBean);
            cacheDataSize();
            notifyItemRangeInserted(getHeaderCount(), detailListBean.getCount());
            Logger.v("page range " + firstPage + " - " + lastPage + ", insert new page " + page);
        } else if (page == lastPage + 1) {
            final int startPos = getItemCount() - getFooterCount();
            mThreadPages.put(page, detailListBean);
            cacheDataSize();
            notifyItemRangeInserted(startPos, detailListBean.getCount());
            Logger.v("page range " + firstPage + " - " + lastPage + ", append new page " + page);
        } else if (page >= firstPage && page <= lastPage) {
            if (mThreadPages.get(page) == detailListBean) {
                Logger.v("page range " + firstPage + " - " + lastPage + ", same skip exist page " + page);
            } else {
                mThreadPages.put(page, detailListBean);
                cacheDataSize();
                int pos = getHeaderCount();
                for (int i = 0; i < mThreadPages.size(); i++) {
                    int tpage = mThreadPages.keyAt(i);
                    if (tpage < page) {
                        pos += mThreadPages.get(tpage).getCount();
                    } else {
                        break;
                    }
                }
                final int startPos = pos;
                notifyItemRangeChanged(startPos, detailListBean.getCount());
                Logger.v("page range " + firstPage + " - " + lastPage + ", update exist page " + page);
            }
        } else {
            mThreadPages.clear();
            mThreadPages.put(page, detailListBean);
            cacheDataSize();
            notifyDataSetChanged();
            Logger.v("page range " + firstPage + " - " + lastPage + ", not continoius page " + page + ", CLEAR ALL");
        }
    }

    @Override
    public List<DetailBean> getDatas() {
        throw new RuntimeException("Don't set datas here");
    }

    @Override
    public DetailBean getItem(int position) {
        int pos = position - getHeaderCount();
        if (pos < 0)
            return null;
        for (int i = 0; i < mThreadPages.size(); i++) {
            DetailListBean detailBeans = mThreadPages.get(mThreadPages.keyAt(i));
            if (pos < detailBeans.getCount()) {
                return detailBeans.getAll().get(pos);
            }
            pos -= detailBeans.getCount();
            if (pos < 0)
                return null;
        }
        return null;
    }

    public int getPositionByFloor(int floor) {
        for (int i = 0; i < getItemCount(); i++) {
            DetailBean bean = getItem(i);
            if (bean != null && bean.getFloor() == floor) {
                return i;
            }
        }
        return -1;
    }

    public int getPositionByPostId(String postId) {
        for (int i = 0; i < getItemCount(); i++) {
            DetailBean bean = getItem(i);
            if (bean != null && bean.getPostId().equals(postId)) {
                return i;
            }
        }
        return -1;
    }

    public void clear() {
        removeFooterView();
        removeHeaderView();
        mThreadPages.clear();
        cacheDataSize();
        notifyDataSetChanged();
    }

    @Override
    public ViewHolderImpl onCreateViewHolderImpl(ViewGroup parent, int position) {
        return new ViewHolderImpl(mInflater.inflate(R.layout.item_thread_detail, parent, false));
    }

    @Override
    public void onBindViewHolderImpl(RecyclerView.ViewHolder viewHolder, final int position) {
        ViewHolderImpl holder;
        if (viewHolder instanceof ViewHolderImpl)
            holder = (ViewHolderImpl) viewHolder;
        else return;

        viewHolder.itemView.setTag(position);
        viewHolder.itemView.setOnTouchListener(mItemClickListener);

        final DetailBean detail = getItem(position);

        if (detail.isHighlightMode()) {
            viewHolder.itemView.setBackgroundColor(ContextCompat.getColor(mCtx, mBackgroundColor));
        } else {
            viewHolder.itemView.setBackgroundResource(mBackgroundResource);
        }

        holder.author.setText(detail.getAuthor());
        holder.time.setText(Utils.shortyTime(detail.getTimePost()));

        if (detail.isSelectMode()) {
            holder.floor.setText("X");
            holder.floor.setTag(null);
            holder.floor.setTextColor(ContextCompat.getColor(mCtx, R.color.md_amber_900));
            holder.floor.setClickable(false);
            holder.floor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    detail.setSelectMode(false);
                    notifyItemChanged(position);
                }
            });
        } else if (detail.isWarned()) {
            holder.floor.setText(detail.getFloor() + "");
            holder.floor.setTag(detail.getUid());
            holder.floor.setTextColor(ContextCompat.getColor(mCtx, R.color.md_amber_900));
            holder.floor.setClickable(true);
            holder.floor.setOnClickListener(mWarningListener);
        } else {
            holder.floor.setText(detail.getFloor() + "");
            holder.floor.setTag(null);
            holder.floor.setTextColor(ColorHelper.getTextColorSecondary(mCtx));
            holder.floor.setClickable(false);
            holder.floor.setOnClickListener(null);
        }

        boolean trimBr = false;
        String postStaus = detail.getPostStatus();
        if (postStaus != null && postStaus.length() > 0) {
            holder.postStatus.setText(Utils.shortyTime(postStaus));
            holder.postStatus.setVisibility(View.VISIBLE);
            trimBr = true;
        } else {
            holder.postStatus.setVisibility(View.GONE);
        }

        if (mSettings.isLoadAvatar()) {
            holder.avatar.setVisibility(View.VISIBLE);
            loadAvatar(detail.getAvatarUrl(), holder.avatar);
        } else {
            holder.avatar.setVisibility(View.GONE);
        }
        holder.avatar.setTag(R.id.avatar_tag_uid, detail.getUid());
        holder.avatar.setTag(R.id.avatar_tag_username, detail.getAuthor());
        holder.avatar.setOnClickListener(mAvatarListener);

        holder.author.setTag(R.id.avatar_tag_uid, detail.getUid());
        holder.author.setTag(R.id.avatar_tag_username, detail.getAuthor());
        holder.author.setOnClickListener(mAvatarListener);

        LinearLayout contentView = holder.contentView;
        contentView.removeAllViews();
        contentView.bringToFront();

        if (detail.isSelectMode()) {
            AppCompatTextView tv = (AppCompatTextView) mInflater.inflate(R.layout.item_textview, null, false);
            tv.setText(detail.getContents().getCopyText());
            tv.setTextSize(mSettings.getPostTextSize());
            tv.setPadding(8, 8, 8, 8);
            UIUtils.setLineSpacing(tv, mSettings);
            tv.setTextIsSelectable(true);

            contentView.addView(tv);
        } else {
            for (int i = 0; i < detail.getContents().getSize(); i++) {
                ContentAbs content = detail.getContents().get(i);
                if (content instanceof ContentText) {
                    TextViewWithEmoticon tv = (TextViewWithEmoticon) mInflater.inflate(R.layout.item_textview_withemoticon, null, false);
                    tv.setFragment(mDetailFragment);
                    tv.setTextSize(mSettings.getPostTextSize());
                    tv.setPadding(8, 8, 8, 8);

                    String cnt = content.getContent();
                    if (trimBr)
                        cnt = Utils.removeLeadingBlank(cnt);
                    if (!TextUtils.isEmpty(cnt)) {
                        tv.setText(cnt);
                        tv.setFocusable(false);
                        contentView.addView(tv);
                    }
                } else if (content instanceof ContentImg) {
                    final ContentImg contentImg = ((ContentImg) content);

                    String policy = mSettings.getCurrectImagePolicy();
                    String thumbUrl = contentImg.getThumbUrl();
                    String fullUrl = contentImg.getContent();
                    boolean mIsThumb;
                    if (HiSettingsHelper.IMAGE_POLICY_ORIGINAL.equals(policy)
                            || TextUtils.isEmpty(thumbUrl)
                            || fullUrl.equals(thumbUrl)
                            || ImageContainer.getImageInfo(fullUrl).isSuccess()) {
                        mIsThumb = false;
                    } else {
                        mIsThumb = true;
                    }

                    ThreadImageLayout threadImageLayout = new ThreadImageLayout(mDetailFragment.getActivity(), mSettings, mJobMgr, contentImg, mDetailFragment.getImagesInPage(detail.getPage()), mIsThumb);

                    contentView.addView(threadImageLayout);
                } else if (content instanceof ContentAttach) {
                    TextViewWithEmoticon tv = new TextViewWithEmoticon(mCtx);
                    tv.setFragment(mDetailFragment);
                    tv.setTextSize(mSettings.getPostTextSize());
                    tv.setText(content.getContent());
                    tv.setFocusable(false);
                    contentView.addView(tv);
                } else if (content instanceof ContentQuote && !((ContentQuote) content).isReplyQuote()) {

                    LinearLayout quoteLayout = (LinearLayout) mInflater.inflate(R.layout.item_quote_text_simple, null, false);
                    TextViewWithEmoticon tv = quoteLayout.findViewById(R.id.quote_content);
                    tv.setFragment(mDetailFragment);

                    tv.setTextSize(mSettings.getPostTextSize() - 1);
                    tv.setAutoLinkMask(Linkify.WEB_URLS);
                    tv.setText(Utils.removeLeadingBlank(content.getContent()));
                    tv.setFocusable(false);    // make convertView long clickable.

                    contentView.addView(quoteLayout);
                    trimBr = true;
                } else if (content instanceof ContentGoToFloor || content instanceof ContentQuote) {

                    String author = "";
                    String time = "";
                    String note = "";
                    String text = "";

                    int floor = -1;
                    if (content instanceof ContentGoToFloor) {
                        //floor is not accurate if some user deleted post
                        //use floor to get page, then get cache by postid
                        ContentGoToFloor goToFloor = (ContentGoToFloor) content;
                        author = goToFloor.getAuthor();
                        floor = goToFloor.getFloor();
                        String postId = goToFloor.getPostId();
                        DetailBean detailBean = mDetailFragment.getCachedPost(postId);
                        if (detailBean != null) {
                            text = detailBean.getContents().getContent();
                            floor = detailBean.getFloor();
                        }
                        note = floor + "#";
                    } else {
                        ContentQuote contentQuote = (ContentQuote) content;
                        DetailBean detailBean = null;
                        String postId = contentQuote.getPostId();
                        if (HiUtils.isValidId(postId)) {
                            detailBean = mDetailFragment.getCachedPost(postId);
                        }
                        if (detailBean != null) {
                            author = contentQuote.getAuthor();
                            text = detailBean.getContents().getContent();
                            floor = detailBean.getFloor();
                            note = floor + "#";
                        } else {
                            author = contentQuote.getAuthor();
                            if (!TextUtils.isEmpty(contentQuote.getTo()))
                                note = "to: " + contentQuote.getTo();
                            time = contentQuote.getTime();
                            text = contentQuote.getText();
                        }
                    }

                    text = Utils.removeLeadingBlank(text);

                    ConstraintLayout quoteLayout = (ConstraintLayout) mInflater.inflate(R.layout.item_quote_text, null, false);

                    TextView tvAuthor = quoteLayout.findViewById(R.id.quote_author);
                    TextView tvNote = quoteLayout.findViewById(R.id.quote_note);
                    TextViewWithEmoticon tvContent = quoteLayout.findViewById(R.id.quote_content);
                    TextView tvTime = quoteLayout.findViewById(R.id.quote_post_time);

                    tvContent.setFragment(mDetailFragment);
                    tvContent.setTrim(true);

                    tvAuthor.setText(Utils.nullToText(author));
                    tvNote.setText(Utils.nullToText(note));
                    tvContent.setText(Utils.nullToText(text));
                    tvTime.setText(Utils.nullToText(time));

                    tvAuthor.setTextSize(mSettings.getPostTextSize() - 2);
                    tvNote.setTextSize(mSettings.getPostTextSize() - 2);
                    tvContent.setTextSize(mSettings.getPostTextSize() - 1);
                    tvTime.setTextSize(mSettings.getPostTextSize() - 4);

                    if (floor > 0) {
                        tvNote.setTag(floor);
                        tvNote.setOnClickListener(mGoToFloorListener);
                        tvNote.setFocusable(false);
                        tvNote.setClickable(true);
                        tvAuthor.setTag(floor);
                        tvAuthor.setOnClickListener(mGoToFloorListener);
                        tvAuthor.setFocusable(false);
                        tvAuthor.setClickable(true);
                    }

                    contentView.addView(quoteLayout);
                    trimBr = true;
                }
            }
        }

        renderPollLayout(holder, detail);
    }

    private void renderPollLayout(ViewHolderImpl holder, DetailBean detail) {
        if (detail.getPoll() != null) {
            holder.pollView.setVisibility(View.VISIBLE);
            holder.pollView.removeAllViews();

            mPollOptionsHolder = new WeakHashMap<>();
            int pxOf4Dp = Utils.dpToPx(4);
            int layoutFullWidth = UIUtils.getScreenWidth(mCtx)
                    - 2 * (int) mCtx.getResources().getDimension(R.dimen.thread_detail_padding)
                    - 2 * pxOf4Dp;

            final PollBean pollBean = detail.getPoll();
            TextViewWithEmoticon tvPoll = new TextViewWithEmoticon(mCtx);
            tvPoll.setText(pollBean.getTitle());
            tvPoll.setPadding(pxOf4Dp, 2 * pxOf4Dp, pxOf4Dp, 2 * pxOf4Dp);
            tvPoll.setTextSize(mSettings.getPostTextSize());
            holder.pollView.addView(tvPoll);

            View.OnClickListener onOptionButtonCheckedListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPollOptionsHolder != null && mVoteButtonHolder != null) {
                        int count = 0;
                        for (CompoundButton optionButton : mPollOptionsHolder.keySet()) {
                            if (optionButton instanceof RadioButton && !optionButton.equals(v)) {
                                optionButton.setChecked(false);
                            }
                            if (optionButton != null && optionButton.isChecked())
                                count++;
                        }
                        if (mVoteButtonHolder.get() != null) {
                            TextView voteButton = mVoteButtonHolder.get();
                            if (count >= 1 && count <= pollBean.getMaxAnswer()) {
                                voteButton.setEnabled(true);
                                voteButton.getBackground().setColorFilter(null);
                                voteButton.setTextColor(Color.WHITE);
                            } else {
                                voteButton.setEnabled(false);
                                voteButton.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
                                voteButton.setTextColor(Color.GRAY);
                            }
                        }
                    }
                }
            };

            List<PollOptionBean> options = pollBean.getPollOptions();
            if (options != null && options.size() > 1) {
                boolean voteable = !TextUtils.isEmpty(options.get(0).getOptionId());
                for (PollOptionBean option : options) {
                    ViewGroup optionLayout = (ViewGroup) mInflater.inflate(R.layout.item_poll_option, null, false);
                    final LayerDrawable progressBg = (LayerDrawable) optionLayout.getBackground();

                    CheckBox checkBox = optionLayout.findViewById(R.id.cb_option);
                    RadioButton radioButton = optionLayout.findViewById(R.id.rb_option);
                    TextView tvText = optionLayout.findViewById(R.id.tv_text);
                    TextView tvRates = optionLayout.findViewById(R.id.tv_rates);

                    if (voteable) {
                        voteable = !TextUtils.isEmpty(option.getOptionId());
                        if (pollBean.getMaxAnswer() > 1) {
                            checkBox.setVisibility(View.VISIBLE);
                            radioButton.setVisibility(View.GONE);
                            tvText.setVisibility(View.GONE);
                            checkBox.setText(option.getText());
                            checkBox.setTextSize(mSettings.getPostTextSize());
                            checkBox.setTextColor(ColorHelper.getTextColorPrimary(mCtx));
                            checkBox.setTag(option.getOptionId());
                            checkBox.setOnClickListener(onOptionButtonCheckedListener);
                            tvRates.setOnClickListener(new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View v) {
                                    checkBox.performClick();
                                }
                            });
                            mPollOptionsHolder.put(checkBox, null);
                        } else {
                            checkBox.setVisibility(View.GONE);
                            radioButton.setVisibility(View.VISIBLE);
                            tvText.setVisibility(View.GONE);
                            radioButton.setText(option.getText());
                            radioButton.setTextSize(mSettings.getPostTextSize());
                            radioButton.setTextColor(ColorHelper.getTextColorPrimary(mCtx));
                            radioButton.setTag(option.getOptionId());
                            radioButton.setOnClickListener(onOptionButtonCheckedListener);
                            tvRates.setOnClickListener(new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View v) {
                                    radioButton.performClick();
                                }
                            });
                            mPollOptionsHolder.put(radioButton, null);
                        }
                    } else {
                        checkBox.setVisibility(View.GONE);
                        radioButton.setVisibility(View.GONE);
                        tvRates.setOnClickListener(null);
                        tvText.setText(option.getText());
                        tvText.setTextSize(mSettings.getPostTextSize());
                    }

                    tvRates.setText(option.getRates());
                    tvRates.setTextSize(mSettings.getPostTextSize() - 4);
                    int indexOfP = option.getRates().indexOf('%');
                    if (indexOfP > 0) {
                        float percent = Utils.parseFloat(option.getRates().substring(0, indexOfP)) / 100f;
                        int perLevel = (int) (10000 * percent);
                        progressBg.getDrawable(0).setLevel(perLevel);
                        progressBg.getDrawable(1).setLevel(10000);
                    } else {
                        progressBg.getDrawable(0).setLevel(0);
                        progressBg.getDrawable(1).setLevel(10000);
                    }
                    holder.pollView.addView(optionLayout);
                }

                if (!TextUtils.isEmpty(pollBean.getFooter())) {
                    AppCompatTextView footer = new AppCompatTextView(mCtx);
                    footer.setPadding(pxOf4Dp, 2 * pxOf4Dp, pxOf4Dp, 2 * pxOf4Dp);
                    footer.setText(pollBean.getFooter());
                    footer.setTextSize(mSettings.getPostTextSize());
                    footer.setTypeface(null, Typeface.BOLD);
                    footer.setGravity(Gravity.CENTER);
                    holder.pollView.addView(footer);
                }

                if (voteable) {
                    TextView button = new AppCompatTextView(mCtx);
                    button.setText("投票");
                    button.setTextSize(mSettings.getPostTextSize() + 1);
                    button.setBackground(ContextCompat.getDrawable(mCtx, R.drawable.vote_button_background));
                    button.setEnabled(false);
                    button.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
                    LinearLayout.LayoutParams layoutParams
                            = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    layoutParams.setMargins(pxOf4Dp, 4 * pxOf4Dp, pxOf4Dp, pxOf4Dp);
                    button.setLayoutParams(layoutParams);
                    button.setPadding(4 * pxOf4Dp, 2 * pxOf4Dp, 4 * pxOf4Dp, 2 * pxOf4Dp);
                    button.setOnClickListener(new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            List<String> values = new ArrayList<>();
                            for (CompoundButton button : mPollOptionsHolder.keySet()) {
                                if (button.isChecked()) {
                                    values.add(button.getTag().toString());
                                }
                            }
                            if (values.size() == 0) {
                                UIUtils.toast("请选择选项");
                            } else if (values.size() > pollBean.getMaxAnswer()) {
                                UIUtils.toast("最多可选 " + pollBean.getMaxAnswer() + " 个选项，已选 " + values.size() + " 项");
                            } else {
                                v.setTag(values);
                                mVotePollListener.onClick(v);
                            }
                        }
                    });
                    mVoteButtonHolder = new WeakReference<>(button);
                    holder.pollView.addView(button);
                }
            }
        } else {
            holder.pollView.setVisibility(View.GONE);
        }
    }

    private void loadAvatar(final String avatarUrl, final ImageView imageView) {
        GlideHelper.loadAvatar(mDetailFragment, imageView, avatarUrl, mSettings);
    }

    private static class ViewHolderImpl extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView author;
        TextView floor;
        TextView postStatus;
        TextView time;
        LinearLayout contentView;
        LinearLayout pollView;

        ViewHolderImpl(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.iv_avatar);
            author = itemView.findViewById(R.id.tv_username);
            time = itemView.findViewById(R.id.time);
            floor = itemView.findViewById(R.id.floor);
            postStatus = itemView.findViewById(R.id.post_status);
            contentView = itemView.findViewById(R.id.content_layout);
            pollView = itemView.findViewById(R.id.poll_layout);
        }
    }

}
