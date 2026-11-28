package com.lichader.hipdanext.di

import android.content.Context
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.SettingsForumConfig
import com.lichader.hipdanext.forum.ForumConfig
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.auth.AuthApi
import com.lichader.hipdanext.forum.auth.HiPdaAuthApi
import com.lichader.hipdanext.forum.blacklist.BlacklistApi
import com.lichader.hipdanext.forum.blacklist.HiPdaBlacklistApi
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.async.LoginHelper
import com.lichader.hipdanext.forum.session.FormhashCache
import com.lichader.hipdanext.forum.session.SessionGuard
import com.lichader.hipdanext.forum.lists.HiPdaListsApi
import com.lichader.hipdanext.forum.lists.ListsApi
import com.lichader.hipdanext.forum.session.SessionRenewer
import com.lichader.hipdanext.forum.threads.HiPdaThreadListApi
import com.lichader.hipdanext.forum.threads.ThreadListApi
import com.lichader.hipdanext.utils.Constants
import com.lichader.hipdanext.forum.user.HiPdaUserApi
import com.lichader.hipdanext.forum.user.UserApi
import com.lichader.hipdanext.ui.ForumErrorFormatter
import com.lichader.hipdanext.utils.Connectivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.net.CookieStore
import javax.inject.Singleton

/**
 * Wires the `:forum` site adapter. The module has no DI framework of its own, so its
 * classes are built here.
 */
@Module
@InstallIn(SingletonComponent::class)
object ForumModule {

    @Provides
    @Singleton
    fun provideForumConfig(settings: HiSettingsHelper, cookieStore: CookieStore): ForumConfig =
        SettingsForumConfig(settings, cookieStore)

    @Provides
    @Singleton
    fun provideEndpoints(config: ForumConfig): HiPdaEndpoints = HiPdaEndpoints(config)

    @Provides
    @Singleton
    fun provideTransport(client: OkHttpClient, config: ForumConfig): ForumTransport =
        ForumTransport(client, config)

    @Provides
    @Singleton
    fun provideFormhashCache(config: ForumConfig): FormhashCache = FormhashCache(config)

    /**
     * Logs in again through [LoginHelper], so a renewal looks to the UI like the jobs' relogin:
     * the same `LoginEvent`s reach `MainFrameActivity`.
     */
    @Provides
    @Singleton
    fun provideSessionRenewer(loginHelper: dagger.Lazy<LoginHelper>): SessionRenewer =
        SessionRenewer { loginHelper.get().loginAndAnnounce(manual = false) == Constants.STATUS_SUCCESS }

    @Provides
    @Singleton
    fun provideSessionGuard(transport: ForumTransport, renewer: SessionRenewer): SessionGuard =
        SessionGuard(transport, renewer)

    @Provides
    @Singleton
    fun provideBlacklistApi(
        transport: ForumTransport,
        endpoints: HiPdaEndpoints,
        formhash: FormhashCache,
        session: SessionGuard,
    ): BlacklistApi = HiPdaBlacklistApi(transport, endpoints, formhash, session)

    @Provides
    @Singleton
    fun provideAuthApi(transport: ForumTransport, endpoints: HiPdaEndpoints): AuthApi =
        HiPdaAuthApi(transport, endpoints)

    @Provides
    @Singleton
    fun provideListsApi(endpoints: HiPdaEndpoints, session: SessionGuard): ListsApi = HiPdaListsApi(endpoints, session)

    @Provides
    @Singleton
    fun provideThreadListApi(endpoints: HiPdaEndpoints, session: SessionGuard): ThreadListApi =
        HiPdaThreadListApi(endpoints, session)

    @Provides
    @Singleton
    fun provideUserApi(
        endpoints: HiPdaEndpoints,
        formhash: FormhashCache,
        session: SessionGuard,
    ): UserApi = HiPdaUserApi(endpoints, formhash, session)

    @Provides
    @Singleton
    fun provideErrorFormatter(@ApplicationContext context: Context): ForumErrorFormatter =
        ForumErrorFormatter { Connectivity.isConnected(context) }
}
