package com.lichader.hipdanext.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lichader.hipdanext.R
import com.lichader.hipdanext.async.SmsPostListener
import com.lichader.hipdanext.bean.SearchBean
import com.lichader.hipdanext.bean.SimpleListItemBean
import com.lichader.hipdanext.glide.GlideHelper
import com.lichader.hipdanext.ui.adapter.RecyclerItemClickListener
import com.lichader.hipdanext.ui.adapter.SimpleListAdapter
import com.lichader.hipdanext.ui.widget.HiProgressDialog
import com.lichader.hipdanext.ui.widget.OnSingleClickListener
import com.lichader.hipdanext.ui.widget.SimpleDivider
import com.lichader.hipdanext.ui.widget.XFooterView
import com.lichader.hipdanext.ui.widget.XRecyclerView
import com.lichader.hipdanext.utils.Constants
import com.lichader.hipdanext.utils.UIUtils
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * A user's profile, with menu actions to message them and to add them to or remove them from the
 * blacklist. The profile and the blacklist go through [UserInfoViewModel].
 *
 * The "搜索帖子" list of the user's threads goes through a [SimpleListViewModel]. Sending a
 * message goes through `SmsSender`.
 */
@AndroidEntryPoint
class UserinfoFragment : BaseFragment(), SmsPostListener {

    private val viewModel: UserInfoViewModel by viewModels()
    private val threadsViewModel: SimpleListViewModel by viewModels()

    private lateinit var avatarView: ImageView
    private lateinit var usernameView: TextView
    private lateinit var onlineView: TextView
    private lateinit var detailView: TextView
    private lateinit var recyclerView: XRecyclerView
    private lateinit var button: Button

    private lateinit var simpleListAdapter: SimpleListAdapter
    private val simpleListItems = ArrayList<SimpleListItemBean>()

    // Whether the user's threads are shown instead of the profile.
    private var showThreads = false

    private var smsPostProgressDialog: HiProgressDialog? = null
    private var shownAvatarUrl: String? = null
    private var shownBlacklisted: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The menu appears once the profile has loaded.
        setHasOptionsMenu(false)
        threadsViewModel.init(SimpleListTypes.TYPE_SEARCH_USER_THREADS)
        val itemClickListener = RecyclerItemClickListener(requireActivity(), mSettings, OnItemClickListener())
        simpleListAdapter = SimpleListAdapter(this, mSettings, SimpleListTypes.TYPE_SEARCH_USER_THREADS, itemClickListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setActionBarTitle("用户信息")
        val view = inflater.inflate(R.layout.fragment_user_info, container, false)
        view.isClickable = false

        avatarView = view.findViewById(R.id.userinfo_avatar)
        if (mSettings.isLoadAvatar) {
            avatarView.visibility = View.VISIBLE
            avatarView.setOnClickListener(object : OnSingleClickListener() {
                override fun onSingleClick(v: View) {
                    val url = viewModel.state.value.avatarUrl
                    if (!url.isNullOrEmpty()) {
                        GlideHelper.clearAvatarCache(url)
                        GlideHelper.loadAvatar(this@UserinfoFragment, avatarView, url, mSettings)
                        UIUtils.toast("头像已经刷新")
                    } else {
                        UIUtils.toast("用户未设置头像")
                    }
                }
            })
        } else {
            avatarView.visibility = View.GONE
        }

        usernameView = view.findViewById(R.id.userinfo_username)
        usernameView.textSize = (mSettings.postTextSize + 2).toFloat()

        onlineView = view.findViewById(R.id.user_online)
        onlineView.visibility = View.INVISIBLE

        detailView = view.findViewById(R.id.userinfo_detail)
        detailView.textSize = mSettings.postTextSize.toFloat()

        // Keep taps from reaching the screen underneath.
        view.setOnTouchListener { _, _ -> true }

        recyclerView = view.findViewById(R.id.rv_search_threads)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.addItemDecoration(SimpleDivider(activity))

        button = view.findViewById(R.id.btn_search_threads)
        button.setOnClickListener(object : OnSingleClickListener() {
            override fun onSingleClick(v: View) {
                showThreads = !showThreads
                if (showThreads) {
                    button.text = "显示信息"
                    detailView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    if (threadsViewModel.state.value.items.isEmpty()) {
                        button.isEnabled = false
                        loadThreads()
                    }
                } else {
                    button.text = "搜索帖子"
                    recyclerView.visibility = View.GONE
                    detailView.visibility = View.VISIBLE
                }
            }
        })
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            detailView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
        recyclerView.adapter = simpleListAdapter
        recyclerView.addOnScrollListener(OnScrollListener())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.messages.collect(UIUtils::toast) }
                launch { threadsViewModel.state.collect(::renderThreads) }
                launch { threadsViewModel.events.collect(::onThreadsEvent) }
            }
        }
    }

    private fun render(state: UserInfoViewModel.UiState) {
        usernameView.text = state.username
        detailView.text = state.detail
        onlineView.visibility = if (state.online) View.VISIBLE else View.INVISIBLE
        if (!state.loaded) return

        if (mSettings.isLoadAvatar) {
            avatarView.visibility = View.VISIBLE
            if (shownAvatarUrl != state.avatarUrl) {
                shownAvatarUrl = state.avatarUrl
                GlideHelper.loadAvatar(this, avatarView, state.avatarUrl, mSettings)
            }
        } else {
            avatarView.visibility = View.GONE
        }
        setHasOptionsMenu(true)
        if (shownBlacklisted != state.blacklisted) {
            shownBlacklisted = state.blacklisted
            requireActivity().invalidateOptionsMenu()
        }
    }

    private fun renderThreads(state: SimpleListViewModel.UiState) {
        simpleListItems.clear()
        simpleListItems.addAll(ListBeans.of(state.items))
        simpleListAdapter.setDatas(simpleListItems)
    }

    private fun onThreadsEvent(event: SimpleListViewModel.Event) {
        button.isEnabled = true
        // As before, a failure or a page without threads shows one message.
        if (event !is SimpleListViewModel.Event.Loaded || event.empty) UIUtils.toast("帖子加载失败")
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_userinfo, menu)
        menu.findItem(R.id.action_send_sms).icon = IconicsDrawable(requireActivity(), GoogleMaterial.Icon.gmd_insert_comment)
            .actionBar()
            .color(UIUtils.getToolbarTextColor(requireActivity(), mSettings))
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val blacklisted = viewModel.state.value.blacklisted
        menu.findItem(R.id.action_blacklist).isVisible = !blacklisted
        menu.findItem(R.id.action_remove_blacklist).isVisible = blacklisted
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        // Handled by the activity.
        android.R.id.home -> false
        R.id.action_send_sms -> {
            showSendSmsDialog(viewModel.uid, viewModel.state.value.username, this)
            true
        }
        R.id.action_blacklist -> {
            viewModel.addToBlacklist()
            true
        }
        R.id.action_remove_blacklist -> {
            viewModel.removeFromBlacklist()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadThreads() {
        threadsViewModel.setSearch(SearchBean().apply { setUid(viewModel.uid) })
        threadsViewModel.refresh()
    }

    private inner class OnScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val visibleItemCount = layoutManager.childCount
            val totalItemCount = layoutManager.itemCount
            val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
            if (visibleItemCount + firstVisibleItem >= totalItemCount - 5 && !threadsViewModel.isLoading) {
                this@UserinfoFragment.recyclerView.setFooterState(
                    if (threadsViewModel.loadMore()) XFooterView.STATE_LOADING else XFooterView.STATE_END,
                )
            }
        }
    }

    private inner class OnItemClickListener : RecyclerItemClickListener.OnItemClickListener {
        override fun onItemClick(view: View, position: Int) {
            if (position < 0 || position >= simpleListAdapter.itemCount) return
            val item = simpleListAdapter.getItem(position)
            FragmentUtils.showThreadActivity(activity, false, item.tid, item.title, -1, -1, null, -1)
        }

        override fun onLongItemClick(view: View, position: Int) {
            if (position < 0 || position >= simpleListAdapter.itemCount) return
            val item = simpleListAdapter.getItem(position)
            FragmentUtils.showThreadActivity(
                activity, false, item.tid, item.title,
                ThreadDetailFragment.LAST_PAGE, ThreadDetailFragment.LAST_FLOOR_OF_PAGE, null, -1,
            )
        }

        override fun onDoubleTap(view: View, position: Int) {}
    }

    override fun onSmsPrePost() {
        smsPostProgressDialog = HiProgressDialog.show(activity, "正在发送...")
    }

    override fun onSmsPostDone(status: Int, message: String, dialog: AlertDialog?) {
        if (status == Constants.STATUS_SUCCESS) {
            smsPostProgressDialog?.dismiss(message)
            dialog?.dismiss()
        } else {
            smsPostProgressDialog?.dismissError(message)
        }
    }

    companion object {
        const val ARG_USERNAME = "USERNAME"
        const val ARG_UID = "UID"
    }
}
