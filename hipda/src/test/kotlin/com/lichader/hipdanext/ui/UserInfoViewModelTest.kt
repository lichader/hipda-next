package com.lichader.hipdanext.ui

import androidx.lifecycle.SavedStateHandle
import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.data.user.UserInfo
import com.lichader.hipdanext.data.user.UserRepository
import com.lichader.hipdanext.forum.ForumException
import com.lichader.hipdanext.forum.user.ProfileSection
import com.lichader.hipdanext.forum.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserInfoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val users = FakeUserRepository()
    private val blacklist = FakeBlacklistRepository()
    private val messages = mutableListOf<String>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.createViewModel(): UserInfoViewModel {
        val args = SavedStateHandle(mapOf(UserinfoFragment.ARG_UID to "100002", UserinfoFragment.ARG_USERNAME to "USER_1"))
        val viewModel = UserInfoViewModel(args, users, blacklist, ForumErrorFormatter { true })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.messages.toList(messages) }
        return viewModel
    }

    @Test
    fun showsTheArgumentsWhileLoading() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertEquals("USER_1", viewModel.state.value.username)
        assertEquals(UserInfoViewModel.LOADING_TEXT, viewModel.state.value.detail)
        assertFalse(viewModel.state.value.loaded)
    }

    @Test
    fun showsTheLoadedProfile() = runTest(dispatcher) {
        blacklist.usernames += "user_1"
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("100002"), users.requested)
        assertEquals("user_1", state.username)
        assertTrue(state.online)
        assertEquals("https://img.invalid/a.jpg", state.avatarUrl)
        assertEquals("用户组: 高级会员\n\n注册日期: 2000-1-1\n上次访问: 隐身\n\n积分: 0\n\n\n", state.detail)
        assertTrue(state.loaded)
        assertTrue(state.blacklisted)
    }

    @Test
    fun knownSignatureComesFirst() = runTest(dispatcher) {
        users.signature = "& TBW"
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.detail.startsWith("签名: & TBW\n\n用户组: 高级会员"))
    }

    @Test
    fun failureIsShownInPlaceOfTheProfile() = runTest(dispatcher) {
        users.error = ForumException.Server("对不起，您还未登录")
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("获取信息失败 : 对不起，您还未登录", viewModel.state.value.detail)
        assertFalse(viewModel.state.value.loaded)
    }

    @Test
    fun addingToTheBlacklistUsesTheProfilesUsername() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addToBlacklist()
        advanceUntilIdle()

        assertEquals(listOf("add user_1"), blacklist.calls)
        assertTrue(viewModel.state.value.blacklisted)
        assertEquals(listOf("已经将用户 <user_1> 添加至黑名单"), messages)
    }

    @Test
    fun removingFromTheBlacklist() = runTest(dispatcher) {
        blacklist.usernames += "user_1"
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.removeFromBlacklist()
        advanceUntilIdle()

        assertEquals(listOf("remove user_1"), blacklist.calls)
        assertFalse(viewModel.state.value.blacklisted)
        assertEquals(listOf("已经将用户 <user_1> 从黑名单移除"), messages)
    }

    @Test
    fun failedBlacklistChangeKeepsTheStateAndSaysWhy() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        blacklist.error = ForumException.Server("用户名不存在")

        viewModel.addToBlacklist()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.blacklisted)
        assertEquals(listOf("用户名不存在"), messages)
    }

    private class FakeUserRepository : UserRepository {
        var signature: String? = null
        var error: Exception? = null
        val requested = mutableListOf<String>()

        override suspend fun info(uid: String): UserInfo {
            requested += uid
            error?.let { throw it }
            val profile = UserProfile(
                uid = uid,
                username = "user_1",
                online = true,
                avatarUrl = "https://img.invalid/a.jpg",
                sections = listOf(
                    ProfileSection("用户组: 高级会员", listOf("注册日期: 2000-1-1", "上次访问: 隐身")),
                    ProfileSection("积分: 0", emptyList()),
                ),
            )
            return UserInfo(profile, signature)
        }
    }

    private class FakeBlacklistRepository : BlacklistRepository {
        val usernames = mutableSetOf<String>()
        val calls = mutableListOf<String>()
        var error: Exception? = null

        override fun isBlacklisted(username: String) = username in usernames

        override suspend fun sync(): List<String> = usernames.toList()

        override suspend fun add(username: String) {
            error?.let { throw it }
            calls += "add $username"
            usernames += username
        }

        override suspend fun remove(username: String) {
            error?.let { throw it }
            calls += "remove $username"
            usernames -= username
        }
    }
}
