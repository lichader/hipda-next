package com.lichader.hipdanext.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.async.FavoriteHelper;
import com.lichader.hipdanext.async.NetworkReadyEvent;
import com.lichader.hipdanext.async.PostSmsAsyncTask;
import com.lichader.hipdanext.bean.SimpleListItemBean;
import com.lichader.hipdanext.job.SimpleListJob;
import com.lichader.hipdanext.job.SmsRefreshEvent;
import com.lichader.hipdanext.ui.adapter.RecyclerItemClickListener;
import com.lichader.hipdanext.ui.adapter.SimpleListAdapter;
import com.lichader.hipdanext.ui.widget.ContentLoadingView;
import com.lichader.hipdanext.ui.widget.HiProgressDialog;
import com.lichader.hipdanext.ui.widget.SimpleDivider;
import com.lichader.hipdanext.ui.widget.SimplePopupMenu;
import com.lichader.hipdanext.ui.widget.XFooterView;
import com.lichader.hipdanext.ui.widget.XRecyclerView;
import com.lichader.hipdanext.utils.ColorHelper;
import com.lichader.hipdanext.utils.Constants;
import com.lichader.hipdanext.utils.HiUtils;
import com.lichader.hipdanext.utils.UIUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class SimpleListFragment extends BaseFragment
        implements SwipeRefreshLayout.OnRefreshListener, PostSmsAsyncTask.SmsPostListener {

    @Inject
    FavoriteHelper mFavoriteHelper;

    public static final String ARG_TYPE = "type";

    private int mType;
    private SimpleListViewModel mViewModel;

    private XRecyclerView mRecyclerView;
    private SimpleListAdapter mSimpleListAdapter;
    private List<SimpleListItemBean> mSimpleListItemBeans = new ArrayList<>();
    private SwipeRefreshLayout mSwipeLayout;
    private ContentLoadingView mLoadingView;
    private HiProgressDialog mSmsPostProgressDialog;

    private int mFirstVisibleItem = 0;

    private MenuItem mFavoritesMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (getArguments().containsKey(ARG_TYPE)) {
            mType = getArguments().getInt(ARG_TYPE);
        }
        // The view model keeps the type across configuration changes: favorites can switch to attention.
        mViewModel = new ViewModelProvider(this).get(SimpleListViewModel.class);
        mViewModel.init(mType);
        mType = mViewModel.getType();

        RecyclerItemClickListener itemClickListener = new RecyclerItemClickListener(getActivity(), mSettings, new OnItemClickListener());
        mSimpleListAdapter = new SimpleListAdapter(this, mSettings, mType, itemClickListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        EventBus.getDefault().unregister(this);
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_simple_list, container, false);
        mRecyclerView = (XRecyclerView) view.findViewById(R.id.rv_threads);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.addItemDecoration(new SimpleDivider(getActivity()));

        mRecyclerView.addOnScrollListener(new OnScrollListener());

        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setColorSchemeColors(ColorHelper.getSwipeColor(getActivity()));
        mSwipeLayout.setProgressBackgroundColorSchemeColor(ColorHelper.getSwipeBackgroundColor(getActivity()));
        mSwipeLayout.setEnabled(false);

        mLoadingView = (ContentLoadingView) view.findViewById(R.id.content_loading);
        mLoadingView.setErrorStateListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mViewModel.isLoading()) {
                    mLoadingView.setState(ContentLoadingView.LOAD_NOW);
                    refresh();
                }
            }
        });
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FlowObservers.collect(getViewLifecycleOwner(), mViewModel.getState(), this::render);
        FlowObservers.collect(getViewLifecycleOwner(), mViewModel.getEvents(), this::onViewModelEvent);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mRecyclerView.setAdapter(mSimpleListAdapter);

        switch (mType) {
            case SimpleListJob.TYPE_MYREPLY:
            case SimpleListJob.TYPE_MYPOST:
            case SimpleListJob.TYPE_SMS:
            case SimpleListJob.TYPE_THREAD_NOTIFY:
            case SimpleListJob.TYPE_FAVORITES:
            case SimpleListJob.TYPE_ATTENTION:
            case SimpleListJob.TYPE_NEW_POSTS:
            case SimpleListJob.TYPE_HISTORIES:
                // The view model's list, which outlives the view: mSimpleListItemBeans is empty until it renders.
                if (mViewModel.getState().getValue().getItems().isEmpty()) {
                    mRecyclerView.setFooterState(XFooterView.STATE_HIDDEN);
                    mLoadingView.setState(ContentLoadingView.LOADING);
                    mViewModel.start();
                } else {
                    mLoadingView.setState(ContentLoadingView.CONTENT);
                }
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();

        switch (mType) {
            case SimpleListJob.TYPE_MYREPLY:
                setActionBarTitle(R.string.title_drawer_myreply);
                break;
            case SimpleListJob.TYPE_MYPOST:
                setActionBarTitle(R.string.title_drawer_mypost);
                break;
            case SimpleListJob.TYPE_SMS:
                setActionBarTitle(R.string.title_drawer_sms);
                inflater.inflate(R.menu.menu_sms_list, menu);
                menu.findItem(R.id.action_send_sms).setIcon(new IconicsDrawable(getActivity(),
                        GoogleMaterial.Icon.gmd_insert_comment).actionBar()
                        .color(UIUtils.getToolbarTextColor(getActivity(), mSettings)));
                break;
            case SimpleListJob.TYPE_THREAD_NOTIFY:
                setActionBarTitle(R.string.title_drawer_notify);
                break;
            case SimpleListJob.TYPE_FAVORITES:
                setActionBarTitle(R.string.title_my_favorites);
                inflater.inflate(R.menu.menu_favorites, menu);
                mFavoritesMenuItem = menu.getItem(0);
                mFavoritesMenuItem.setTitle(R.string.action_attention);
                break;
            case SimpleListJob.TYPE_HISTORIES:
                setActionBarTitle(R.string.title_drawer_histories);
                break;
            case SimpleListJob.TYPE_ATTENTION:
                setActionBarTitle(R.string.title_my_attention);
                inflater.inflate(R.menu.menu_favorites, menu);
                mFavoritesMenuItem = menu.getItem(0);
                mFavoritesMenuItem.setTitle(R.string.action_favorites);
                break;
            case SimpleListJob.TYPE_NEW_POSTS:
                setActionBarTitle(R.string.title_drawer_new_posts);
                break;

            default:
                break;
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // Implemented in activity
            return false;
        }
        if (itemId == R.id.action_refresh) {
            refresh();
            return true;
        }
        if (itemId == R.id.action_favories) {
            mLoadingView.setState(ContentLoadingView.LOAD_NOW);
            if (mFavoritesMenuItem.getTitle().toString().equals(getString(R.string.action_attention))) {
                mFavoritesMenuItem.setTitle(R.string.action_favorites);
                mType = SimpleListJob.TYPE_ATTENTION;
                setActionBarTitle(R.string.title_my_attention);
            } else {
                mFavoritesMenuItem.setTitle(R.string.action_attention);
                mType = SimpleListJob.TYPE_FAVORITES;
                setActionBarTitle(R.string.title_my_favorites);
            }
            mRecyclerView.setFooterState(XFooterView.STATE_HIDDEN);
            mViewModel.switchType(mType);
            return true;
        }
        if (itemId == R.id.action_send_sms) {
            showSendSmsDialog("", "", this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refresh() {
        mViewModel.refresh();
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    public void scrollToTop() {
        stopScroll();
        mRecyclerView.scrollToPosition(0);
    }

    public void stopScroll() {
        mRecyclerView.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0));
    }

    private class OnScrollListener extends RecyclerView.OnScrollListener {
        int visibleItemCount, totalItemCount;

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (dy > 0) {
                LinearLayoutManager mLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                visibleItemCount = mLayoutManager.getChildCount();
                totalItemCount = mLayoutManager.getItemCount();
                mFirstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();

                if ((visibleItemCount + mFirstVisibleItem) >= totalItemCount - 5) {
                    if (!mViewModel.isLoading()) {
                        if (mViewModel.loadMore()) {
                            mRecyclerView.setFooterState(XFooterView.STATE_LOADING);
                        } else {
                            mRecyclerView.setFooterState(XFooterView.STATE_END);
                        }
                    }
                }
            }
        }
    }

    private class OnItemClickListener implements RecyclerItemClickListener.OnItemClickListener {

        @Override
        public void onItemClick(View view, int position) {
            if (position < 0 || position >= mSimpleListAdapter.getItemCount()) {
                return;
            }
            SimpleListItemBean item = mSimpleListAdapter.getItem(position);
            if (item == null)
                return;
            if (mType == SimpleListJob.TYPE_SMS) {
                FragmentUtils.showSmsActivity(getActivity(), false, item.getUid(), item.getAuthor());
            } else {
                if (HiUtils.isValidId(item.getTid()) || HiUtils.isValidId(item.getPid())) {
                    FragmentUtils.showThreadActivity(getActivity(), false, item.getTid(), item.getTitle(), -1, -1, item.getPid(), -1);
                } else if (HiUtils.isValidId(item.getUid())) {
                    FragmentUtils.showUserInfoActivity(getActivity(), false, item.getUid(), item.getAuthor());
                }
            }
        }

        @Override
        public void onLongItemClick(View view, int position) {
            if (position < 0 || position >= mSimpleListAdapter.getItemCount()) {
                return;
            }
            SimpleListItemBean item = mSimpleListAdapter.getItem(position);
            if (item == null)
                return;
            if (mType == SimpleListJob.TYPE_SMS) {
            } else if (mType == SimpleListJob.TYPE_FAVORITES) {
                showFavoriteActionDialog(item);
            } else if (mType == SimpleListJob.TYPE_ATTENTION) {
                showAttentionActionDialog(item);
            } else {
                if (HiUtils.isValidId(item.getTid()) || HiUtils.isValidId(item.getPid())) {
                    showLastPage(item);
                } else if (HiUtils.isValidId(item.getUid())) {
                    FragmentUtils.showUserInfoActivity(getActivity(), false, item.getUid(), item.getAuthor());
                }
            }
        }

        @Override
        public void onDoubleTap(View view, int position) {
        }
    }

    private void showFavoriteActionDialog(final SimpleListItemBean item) {
        final SimplePopupMenu popupMenu = new SimplePopupMenu(getActivity());
        popupMenu.add("cancel", "取消收藏", new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mFavoriteHelper.deleteFavorite(getActivity(), mViewModel.getState().getValue().getFormhash(), FavoriteHelper.TYPE_FAVORITE, item.getTid());
                mViewModel.remove(item.getTid());
            }
        });
        popupMenu.add("last_page", "转到最新回复", new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showLastPage(item);
            }
        });
        popupMenu.show();
    }

    private void showAttentionActionDialog(final SimpleListItemBean item) {
        SimplePopupMenu popupMenu = new SimplePopupMenu(getActivity());
        popupMenu.add("cancel", "取消关注", new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mFavoriteHelper.deleteFavorite(getActivity(), mViewModel.getState().getValue().getFormhash(), FavoriteHelper.TYPE_ATTENTION, item.getTid());
                mViewModel.remove(item.getTid());
            }
        });
        popupMenu.add("last_page", "转到最新回复",
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        showLastPage(item);
                    }
                });
        popupMenu.show();
    }

    private void showLastPage(SimpleListItemBean item) {
        String postId = "";
        int page = -1;
        int floor = -1;
        if (HiUtils.isValidId(item.getPid())) {
            postId = item.getPid();
        } else {
            page = ThreadDetailFragment.LAST_PAGE;
            floor = ThreadDetailFragment.LAST_FLOOR_OF_PAGE;
        }
        FragmentUtils.showThreadActivity(getActivity(), false, item.getTid(), item.getTitle(), page, floor, postId, -1);
    }

    @Override
    public void onSmsPrePost() {
        mSmsPostProgressDialog = HiProgressDialog.show(getActivity(), "正在发送...");
    }

    @Override
    public void onSmsPostDone(int status, final String message, AlertDialog dialog) {
        if (status == Constants.STATUS_SUCCESS) {
            mSmsPostProgressDialog.dismiss(message);
            if (dialog != null)
                dialog.dismiss();
            onRefresh();
        } else {
            mSmsPostProgressDialog.dismissError(message);
        }
    }

    /** Shows the view model's items in the legacy adapter. */
    private void render(SimpleListViewModel.UiState state) {
        mSimpleListItemBeans.clear();
        mSimpleListItemBeans.addAll(ListBeans.of(state.getItems()));
        mSimpleListAdapter.setDatas(mSimpleListItemBeans);
    }

    private void onViewModelEvent(SimpleListViewModel.Event event) {
        mSwipeLayout.setEnabled(true);
        mSwipeLayout.setRefreshing(false);
        mRecyclerView.setFooterState(XFooterView.STATE_HIDDEN);
        if (event instanceof SimpleListViewModel.Event.Loaded) {
            mLoadingView.setState(((SimpleListViewModel.Event.Loaded) event).getEmpty()
                    ? ContentLoadingView.NO_DATA : ContentLoadingView.CONTENT);
        } else if (event instanceof SimpleListViewModel.Event.Failed) {
            if (mSimpleListItemBeans.size() == 0)
                mLoadingView.setState(ContentLoadingView.ERROR);
            else
                mLoadingView.setState(ContentLoadingView.CONTENT);
            UIUtils.errorSnack(getView(), ((SimpleListViewModel.Event.Failed) event).getMessage(), "");
        } else if (event instanceof SimpleListViewModel.Event.NotLoggedIn) {
            mLoadingView.setState(ContentLoadingView.NOT_LOGIN);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEvent(SmsRefreshEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        if (mType == SimpleListJob.TYPE_SMS)
            onRefresh();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(NetworkReadyEvent event) {
        if (!mViewModel.isLoading() && mSimpleListItemBeans.size() == 0)
            refresh();
    }

}
