package com.lichader.hipdanext.async

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.db.ContentDBHelper
import com.lichader.hipdanext.db.ContentDao
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.forum.site.SiteApi
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.HiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskHelperTest {

    private val entry = SingletonEntryPoint.get()
    private val settings = entry.settings()
    private val site = FakeSiteApi()
    private val blacklist = FakeBlacklistRepository()
    private val tasks = TaskHelper(
        settings, entry.loginHelper(), blacklist, site, ForumErrorFormatter { true },
        ContentDao(ContentDBHelper(ApplicationProvider.getApplicationContext()), settings), entry.historyDao(),
        CoroutineScope(Dispatchers.Unconfined),
    )

    @Before
    fun setUp() {
        settings.setStringValue(HiSettingsHelper.PERF_IMAGE_HOST, "")
        settings.setLongValue(HiSettingsHelper.PERF_IMAGE_HOST_UPDATE_TIME, 0)
    }

    @Test
    fun theImageHostIsReadFromTheSiteAndTheServerReset() {
        settings.setForumServer("https://forum.invalid")

        tasks.updateImageHost()

        assertEquals(1, site.calls)
        assertEquals(HiUtils.ForumServer, settings.forumServer)
        assertEquals("https://img.invalid", settings.imageHost)
    }

    @Test
    fun aRecentImageHostIsNotReadAgainUnlessAsked() {
        tasks.updateImageHost()
        tasks.updateImageHost()

        assertEquals(1, site.calls)
    }

    @Test
    fun theDailyTaskRunsOnceADayAndNeedsALoginToSync() {
        settings.setStringValue(HiSettingsHelper.PERF_LAST_TASK_TIME, "0")
        settings.setUsername("")

        tasks.runDailyTask(false)
        val ranAt = settings.getStringValue(HiSettingsHelper.PERF_LAST_TASK_TIME, "0").toLong()
        tasks.runDailyTask(false)

        assertEquals(true, ranAt > 0)
        assertEquals(ranAt, settings.getStringValue(HiSettingsHelper.PERF_LAST_TASK_TIME, "0").toLong())
        assertEquals(0, blacklist.syncs)
    }

    private class FakeSiteApi : SiteApi {
        var calls = 0
        override suspend fun imageHost(): String {
            calls++
            return "https://img.invalid"
        }
    }

    private class FakeBlacklistRepository : BlacklistRepository {
        var syncs = 0
        override suspend fun sync(): List<String> {
            syncs++
            return emptyList()
        }
        override fun isBlacklisted(username: String) = false
        override suspend fun add(username: String) {}
        override suspend fun remove(username: String) {}
    }
}
