package com.lichader.hipdanext.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mikepenz.fontawesome_typeface_library.FontAwesome;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.vanniktech.emoji.EmojiEditText;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.bean.SimpleListItemBean;
import com.lichader.hipdanext.async.SmsPostListener;
import com.lichader.hipdanext.forum.lists.ListItem;
import com.lichader.hipdanext.job.SmsRefreshEvent;
import com.lichader.hipdanext.ui.adapter.RecyclerItemClickListener;
import com.lichader.hipdanext.ui.adapter.SmsAdapter;
import com.lichader.hipdanext.ui.widget.ContentLoadingView;
import com.lichader.hipdanext.ui.widget.CountdownButton;
import com.lichader.hipdanext.ui.widget.HiProgressDialog;
import com.lichader.hipdanext.ui.widget.OnSingleClickListener;
import com.lichader.hipdanext.ui.widget.SimplePopupMenu;
import com.lichader.hipdanext.ui.widget.XRecyclerView;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.HtmlCompat;
import com.lichader.hipdanext.utils.UIUtils;
import com.lichader.hipdanext.utils.Utils;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SmsFragment extends BaseFragment implements SmsPostListener {

    public static final String ARG_AUTHOR = "AUTHOR";
    public static final String ARG_UID = "UID";

    private String mAuthor;
    private String mUid;
    private SmsAdapter mSmsAdapter;
    private List<SimpleListItemBean> mSmsBeans = new ArrayList<>();
    private XRecyclerView mRecyclerView;

    private EmojiEditText mEtSms;
    private ImageButton mIbEmojiSwitch;
    private CountdownButton mCountdownButton;

    private ContentLoadingView mLoadingView;
    private ConversationViewModel mViewModel;
    private HiProgressDialog mClearProgress;
    private boolean mSending = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        if (getArguments().containsKey(ARG_AUTHOR)) {
            mAuthor = getArguments().getString(ARG_AUTHOR);
        }
        if (getArguments().containsKey(ARG_UID)) {
            mUid = getArguments().getString(ARG_UID);
        }
        mViewModel = new ViewModelProvider(this).get(ConversationViewModel.class);
        mViewModel.setUser(mUid);

        RecyclerItemClickListener itemClickListener = new RecyclerItemClickListener(getActivity(), mSettings, new OnItemClickListener());
        mSmsAdapter = new SmsAdapter(this, mSettings, new AvatarOnClickListener(), itemClickListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sms, container, false);
        mRecyclerView = (XRecyclerView) view.findViewById(R.id.rv_sms);
        mRecyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        linearLayoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(linearLayoutManager);

        mLoadingView = (ContentLoadingView) view.findViewById(R.id.content_loading);
        mLoadingView.setErrorStateListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLoadingView.setState(ContentLoadingView.LOAD_NOW);
                mViewModel.refresh();
            }
        });

        mCountdownButton = (CountdownButton) view.findViewById(R.id.countdown_button);
        mCountdownButton.setImageDrawable(new IconicsDrawable(getActivity(), GoogleMaterial.Icon.gmd_send).sizeDp(28).color(Color.GRAY));

        mEtSms = (EmojiEditText) view.findViewById(R.id.et_sms);
        mEtSms.setTextSize(mSettings.getPostTextSize());
        mCountdownButton.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {
                String replyText = mEtSms.getText().toString();
                if (replyText.length() > 0) {
                    sendSms(replyText);
                }
            }
        });

        mIbEmojiSwitch = (ImageButton) view.findViewById(R.id.ib_emoji_switch);
        setUpEmojiPopup(mEtSms, mIbEmojiSwitch);

        showSendButton();

        return view;
    }

    private void sendSms(String replyText) {
        mSending = true;
        mSmsSender.send(mUid, null, replyText, SmsFragment.this, null);
        mEtSms.setText("");
        mCountdownButton.setEnabled(false);
        SimpleListItemBean bean = new SimpleListItemBean();
        bean.setAuthor(mSettings.getUsername());
        bean.setUid(mSettings.getUid());
        bean.setNew(false);
        bean.setTime(Utils.formatDate(new Date(), "yyyy-M-d HH:mm"));
        bean.setAvatarUrl(HiUtils.getAvatarUrlByUid(mSettings.getUid()));
        bean.setInfo(replyText);
        bean.setStatus(Constants.STATUS_IN_PROGRESS);
        mSmsAdapter.getDatas().add(bean);
        mSmsAdapter.notifyItemInserted(mSmsAdapter.getItemCount() - 1);
    }

    private void removeFailedSms(int position) {
        if (position >= 0 && position < mSmsAdapter.getItemCount()) {
            mSmsAdapter.getDatas().remove(position);
            mSmsAdapter.notifyItemRemoved(position);
            mSmsAdapter.notifyItemRangeChanged(position, mSmsAdapter.getItemCount());
        }
    }

    private void markNewSmsFailed() {
        SimpleListItemBean bean = mSmsAdapter.getDatas().get(mSmsAdapter.getItemCount() - 1);
        if (bean.getStatus() == Constants.STATUS_IN_PROGRESS) {
            bean.setStatus(Constants.STATUS_FAIL);
            bean.setTime("发送失败");
            mSmsAdapter.notifyItemChanged(mSmsAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mRecyclerView.setAdapter(mSmsAdapter);

        // The view model's conversation, which outlives the view: mSmsBeans is empty until it renders.
        if (mViewModel.getMessages().getValue().isEmpty()) {
            mLoadingView.setState(ContentLoadingView.LOADING);
            mViewModel.refresh();
        } else {
            mLoadingView.setState(ContentLoadingView.CONTENT);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlowObservers.collect(getViewLifecycleOwner(), mViewModel.getMessages(), this::render);
        FlowObservers.collect(getViewLifecycleOwner(), mViewModel.getEvents(), this::onViewModelEvent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();

        inflater.inflate(R.menu.menu_sms_detail, menu);
        menu.findItem(R.id.action_clear_sms)
                .setIcon(new IconicsDrawable(getActivity(), FontAwesome.Icon.faw_trash).actionBar()
                        .color(UIUtils.getToolbarTextColor(getActivity(), mSettings)));

        setActionBarTitle(mAuthor);
        mEtSms.setHint(getString(R.string.txt_quick_hint) + " · " + mAuthor);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // Implemented in activity
            return false;
        }
        if (itemId == R.id.action_clear_sms) {
            showClearSmsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showClearSmsDialog() {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(getActivity());
        popDialog.setTitle("清空短消息？");
        popDialog.setMessage(HtmlCompat.fromHtml("确认清空所有与用户 <b>" + mAuthor + "</b> 的短消息？<br><br><font color=red>注意：此操作不可恢复。</font>"));
        popDialog.setPositiveButton("清空",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mClearProgress = HiProgressDialog.show(getActivity(), "正在处理...");
                        mViewModel.clear();

                    }
                });
        popDialog.setIcon(new IconicsDrawable(getActivity(), FontAwesome.Icon.faw_exclamation_circle).sizeDp(24).color(Color.RED));
        popDialog.setNegativeButton("取消", null);
        popDialog.create().show();
    }

    @Override
    public void onSmsPrePost() {
        mRecyclerView.scrollToBottom();
    }

    @Override
    public void onSmsPostDone(int status, final String message, AlertDialog dialog) {
        if (status == Constants.STATUS_SUCCESS) {
            mSending = false;

            showSendButton();

            mCountdownButton.setEnabled(true);
            //new sms has some delay, so this is a dirty hack
            new CountDownTimer(1000, 1000) {
                public void onTick(long millisUntilFinished) {
                }

                public void onFinish() {
                    mViewModel.refresh();
                }
            }.start();
        } else {
            mSending = false;
            mCountdownButton.setEnabled(true);
            markNewSmsFailed();
            UIUtils.toast(message);
        }
    }

    private void showSendButton() {
        mCountdownButton.setCountdown(mSmsSender.getWaitSecondsToSend());
    }

    private class AvatarOnClickListener extends OnSingleClickListener {
        @Override
        public void onSingleClick(View view) {
            String uid = (String) view.getTag(R.id.avatar_tag_uid);
            String username = (String) view.getTag(R.id.avatar_tag_username);

            FragmentUtils.showUserInfoActivity(getActivity(), false, uid, username);
        }
    }

    private class OnItemClickListener implements RecyclerItemClickListener.OnItemClickListener {

        @Override
        public void onItemClick(View view, int position) {
        }

        @Override
        public void onLongItemClick(View view, int position) {
            final SimpleListItemBean bean = mSmsAdapter.getItem(position);
            final int rvPosition = position;
            if (bean != null) {
                SimplePopupMenu popupMenu = new SimplePopupMenu(getActivity());
                popupMenu.add("copy", "复制内容", new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                        CharSequence content = Utils.fromHtmlAndStrip(bean.getInfo());
                        if (content.length() > 0) {
                            ClipData clip = ClipData.newPlainText("SMS CONTENT FROM HiPDA", content);
                            clipboard.setPrimaryClip(clip);
                            UIUtils.toast("内容已复制");
                        } else {
                            UIUtils.toast("内容为空");
                        }
                    }
                });
                if (!mSending && bean.getStatus() == Constants.STATUS_FAIL && mSmsSender.getWaitSecondsToSend() <= 0) {
                    popupMenu.add("resend", "重新发送", new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            if (!mSending && mSmsSender.getWaitSecondsToSend() <= 0) {
                                String content = bean.getInfo();
                                removeFailedSms(rvPosition);
                                sendSms(content);
                            } else {
                                UIUtils.toast("短消息发送时间限制");
                            }
                        }
                    });
                }
                popupMenu.show();
            }
        }

        @Override
        public void onDoubleTap(View view, int position) {
        }
    }

    @Override
    public void scrollToTop() {
        if (mSmsAdapter != null && mSmsAdapter.getItemCount() > 0)
            mRecyclerView.scrollToPosition(0);
    }

    /** Shows the view model's conversation in the legacy adapter, scrolled to the newest message. */
    private void render(List<ListItem> messages) {
        mSmsBeans.clear();
        mSmsBeans.addAll(ListBeans.of(messages));
        mSmsAdapter.setDatas(mSmsBeans);
        if (!messages.isEmpty())
            mRecyclerView.scrollToBottom();
    }

    private void onViewModelEvent(ConversationViewModel.Event event) {
        if (event instanceof ConversationViewModel.Event.Loaded) {
            mLoadingView.setState(((ConversationViewModel.Event.Loaded) event).getEmpty()
                    ? ContentLoadingView.NO_DATA : ContentLoadingView.CONTENT);
        } else if (event instanceof ConversationViewModel.Event.LoadFailed) {
            mLoadingView.setState(ContentLoadingView.ERROR);
            UIUtils.toast(((ConversationViewModel.Event.LoadFailed) event).getMessage());
        } else if (event instanceof ConversationViewModel.Event.NotLoggedIn) {
            mLoadingView.setState(ContentLoadingView.NOT_LOGIN);
        } else if (event instanceof ConversationViewModel.Event.Cleared) {
            ConversationViewModel.Event.Cleared cleared = (ConversationViewModel.Event.Cleared) event;
            if (mClearProgress != null) {
                if (cleared.getFailed())
                    mClearProgress.dismissError(cleared.getMessage());
                else
                    mClearProgress.dismiss(cleared.getMessage());
            }
            EventBus.getDefault().postSticky(new SmsRefreshEvent());
            ((BaseActivity) getActivity()).finishWithNoSlide();
        }
    }

}
