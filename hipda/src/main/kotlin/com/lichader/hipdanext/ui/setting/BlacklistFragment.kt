package com.lichader.hipdanext.ui.setting

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.lichader.hipdanext.R
import com.lichader.hipdanext.ui.BaseFragment
import com.lichader.hipdanext.ui.widget.ContentLoadingView
import com.lichader.hipdanext.ui.widget.OnSingleClickListener
import com.lichader.hipdanext.ui.widget.SimpleDivider
import com.lichader.hipdanext.utils.ColorHelper
import com.lichader.hipdanext.utils.UIUtils
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Shows the user's blacklist and removes users from it. All logic is in [BlacklistViewModel]. */
@AndroidEntryPoint
class BlacklistFragment : BaseFragment() {

    private val viewModel: BlacklistViewModel by viewModels()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BaseFragment retains every fragment, which crashes Hilt fragments on any
        // configuration change. This screen's state lives in its ViewModel, so it is not
        // retained.
        retainInstance = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_blacklist, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setActionBarTitle("黑名单")
        val context = requireContext()

        val removeIcon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_close).color(Color.GRAY).sizeDp(12)
        val adapter = BlacklistAdapter(removeIcon, viewModel::remove)

        val swipeLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipe_container).apply {
            setOnRefreshListener(viewModel::refresh)
            setColorSchemeColors(ColorHelper.getSwipeColor(context))
            setProgressBackgroundColorSchemeColor(ColorHelper.getSwipeBackgroundColor(context))
        }
        view.findViewById<RecyclerView>(R.id.recyclerview).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(SimpleDivider(context))
            this.adapter = adapter
        }
        val loadingView = view.findViewById<ContentLoadingView>(R.id.content_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        swipeLayout.isRefreshing = state.refreshing
                        adapter.submitList(state.usernames.map { Row(it, removing = it in state.removing) })
                        if (state.loaded) {
                            loadingView.setState(
                                if (state.usernames.isEmpty()) ContentLoadingView.NO_DATA else ContentLoadingView.CONTENT
                            )
                        }
                    }
                }
                launch { viewModel.messages.collect(UIUtils::toast) }
            }
        }
    }

    private data class Row(val username: String, val removing: Boolean)

    private class BlacklistAdapter(
        private val removeIcon: Drawable,
        private val onRemove: (String) -> Unit,
    ) : ListAdapter<Row, BlacklistAdapter.Holder>(RowDiff) {

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val username: TextView = itemView.findViewById(R.id.tv_username)
            val remove: ImageButton = itemView.findViewById(R.id.ib_remove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val holder = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_blacklist, parent, false))
            holder.remove.setImageDrawable(removeIcon)
            holder.remove.setOnClickListener(object : OnSingleClickListener() {
                override fun onSingleClick(v: View) {
                    onRemove(v.tag as String)
                }
            })
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = getItem(position)
            holder.username.text = row.username
            holder.remove.tag = row.username
            holder.remove.visibility = if (row.removing) View.INVISIBLE else View.VISIBLE
        }
    }

    private object RowDiff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.username == newItem.username
        override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
    }

    companion object {
        const val TAG_KEY = "BLACKLIST_KEY"
    }
}
