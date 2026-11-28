package com.lichader.hipdanext.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.bean.DetailListBean
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.cache.ThreadDetailCache
import com.lichader.hipdanext.data.thread.ThreadRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.job.ThreadUpdatedEvent
import com.lichader.hipdanext.utils.HiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject

/**
 * A thread's pages: loads them and keeps those loaded, so a recreated screen shows them again.
 * Which page to load, and where, is still the screen's choice.
 */
@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    private val repository: ThreadRepository,
    private val settings: HiSettingsHelper,
    private val errors: ForumErrorFormatter,
) : ViewModel() {

    /** A load's result; [fetchType] and [loadingPosition] are the screen's, as given to [load]. */
    sealed interface Event {
        val fetchType: Int
        val loadingPosition: Int

        /** [authorId] is the author's uid when the load was asked to find it. */
        class Loaded(val data: DetailListBean, val authorId: String?, override val fetchType: Int, override val loadingPosition: Int) : Event

        class Failed(val message: String, override val fetchType: Int, override val loadingPosition: Int) : Event

        /** The session expired and logging in again failed. */
        class NotLoggedIn(override val fetchType: Int, override val loadingPosition: Int) : Event
    }

    /** The pages loaded so far. */
    val cache = ThreadDetailCache()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    /**
     * Loads the page holding post [gotoPostId] if given, else page [page] of thread [tid]
     * (`ThreadDetailFragment.LAST_PAGE` for the last one), only [authorId]'s posts if given.
     * [FIND_AUTHOR_ID] finds the thread author's uid first.
     */
    fun load(tid: String?, authorId: String?, gotoPostId: String?, page: Int, fetchType: Int, loadingPosition: Int) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            var author = authorId
            val event = try {
                val result = when {
                    !gotoPostId.isNullOrEmpty() -> repository.pageOfPost(gotoPostId, tid)
                    page == ThreadDetailFragment.LAST_PAGE -> repository.lastPage(tid!!)
                    else -> {
                        if (author == FIND_AUTHOR_ID) author = findAuthor(tid!!)
                        repository.page(tid!!, page, author)
                    }
                }
                val data = ThreadDetailBeans.of(result, settings)
                if (data.count == 0) Event.Failed(LOAD_FAILED, fetchType, loadingPosition) else Event.Loaded(data, author, fetchType, loadingPosition)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForumException.NotLoggedIn) {
                Event.NotLoggedIn(fetchType, loadingPosition)
            } catch (e: ForumException.Parse) {
                Event.Failed(LOAD_FAILED, fetchType, loadingPosition)
            } catch (e: Exception) {
                Event.Failed(errors.format(e), fetchType, loadingPosition)
            }
            // As before, so the loading indicators don't flash.
            delay(MIN_LOAD_TIME_MS - (System.currentTimeMillis() - start))
            if (event is Event.Loaded) cache.put(event.data)
            _events.send(event)
            if (event is Event.Loaded && author == null) announceUpdate(event.data)
        }
    }

    /** The thread author's uid, or empty if it can't be read, as before. */
    private suspend fun findAuthor(tid: String): String = try {
        repository.authorId(tid)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ""
    }

    /** Tells the thread list the thread's title and reply count, read from its last page. */
    private fun announceUpdate(data: DetailListBean) {
        if (data.page != data.lastPage || !HiUtils.isForumValid(data.fid) || data.tid == null) return
        EventBus.getDefault().postSticky(ThreadUpdatedEvent().apply {
            mFid = data.fid
            mTid = data.tid
            mTitle = data.title
            mReplyCount = data.all[data.count - 1].floor - 1
        })
    }

    companion object {
        /** Asks [load] to find the thread author's uid. */
        const val FIND_AUTHOR_ID = "-1"

        private const val MIN_LOAD_TIME_MS = 150L
        private const val LOAD_FAILED = "页面加载失败"
    }
}
