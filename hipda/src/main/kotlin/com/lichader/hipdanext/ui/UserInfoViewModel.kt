package com.lichader.hipdanext.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.data.user.UserInfo
import com.lichader.hipdanext.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The user info screen: loads a user's profile, and adds them to or removes them from the
 * blacklist. The list of the user's threads still runs through `SimpleListJob` in the fragment
 * until simple lists and search migrate (W6).
 */
@HiltViewModel
class UserInfoViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val users: UserRepository,
    private val blacklist: BlacklistRepository,
    private val errors: ForumErrorFormatter,
) : ViewModel() {

    /** The user's uid, from the fragment's arguments. */
    val uid: String = savedState.get<String>(UserinfoFragment.ARG_UID).orEmpty()

    data class UiState(
        /** From the arguments until the profile loads, then the profile's spelling. */
        val username: String,
        val avatarUrl: String? = null,
        val online: Boolean = false,
        /** The profile text, or a loading or error message. */
        val detail: String = LOADING_TEXT,
        /** True once the profile has loaded; the screen's menu appears then. */
        val loaded: Boolean = false,
        val blacklisted: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(username = savedState.get<String>(UserinfoFragment.ARG_USERNAME).orEmpty()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** Messages to show once, such as the result of a blacklist change. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val info = users.info(uid)
                val username = info.profile.username.ifEmpty { _state.value.username }
                _state.update {
                    it.copy(
                        username = username,
                        avatarUrl = info.profile.avatarUrl,
                        online = info.profile.online,
                        detail = profileText(info),
                        loaded = true,
                        blacklisted = blacklist.isBlacklisted(username),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(detail = "获取信息失败 : " + errors.format(e)) }
            }
        }
    }

    fun addToBlacklist() = changeBlacklist(add = true)

    fun removeFromBlacklist() = changeBlacklist(add = false)

    private fun changeBlacklist(add: Boolean) {
        val username = _state.value.username
        viewModelScope.launch {
            val message = try {
                if (add) blacklist.add(username) else blacklist.remove(username)
                _state.update { it.copy(blacklisted = blacklist.isBlacklisted(username)) }
                if (add) "已经将用户 <$username> 添加至黑名单" else "已经将用户 <$username> 从黑名单移除"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.format(e)
            }
            _messages.send(message)
        }
    }

    companion object {
        const val LOADING_TEXT = "正在获取信息..."

        /** The profile as the screen shows it; the same text as before the migration. */
        fun profileText(info: UserInfo): String = buildString {
            if (!info.signature.isNullOrEmpty()) append("签名: ").append(info.signature).append("\n\n")
            for (section in info.profile.sections) {
                append(section.title).append("\n\n")
                section.lines.forEach { append(it).append('\n') }
                append('\n')
            }
        }
    }
}
