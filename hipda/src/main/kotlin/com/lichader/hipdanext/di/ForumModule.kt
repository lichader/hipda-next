package com.lichader.hipdanext.di

import android.content.Context
import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.data.SettingsForumConfig
import com.lichader.hipdanext.forum.ForumConfig
import com.lichader.hipdanext.forum.HiPdaEndpoints
import com.lichader.hipdanext.forum.blacklist.BlacklistApi
import com.lichader.hipdanext.forum.blacklist.HiPdaBlacklistApi
import com.lichader.hipdanext.forum.http.ForumTransport
import com.lichader.hipdanext.forum.session.FormhashCache
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

    @Provides
    @Singleton
    fun provideBlacklistApi(
        transport: ForumTransport,
        endpoints: HiPdaEndpoints,
        formhash: FormhashCache,
    ): BlacklistApi = HiPdaBlacklistApi(transport, endpoints, formhash)

    @Provides
    @Singleton
    fun provideUserApi(
        transport: ForumTransport,
        endpoints: HiPdaEndpoints,
        formhash: FormhashCache,
    ): UserApi = HiPdaUserApi(transport, endpoints, formhash)

    @Provides
    @Singleton
    fun provideErrorFormatter(@ApplicationContext context: Context): ForumErrorFormatter =
        ForumErrorFormatter { Connectivity.isConnected(context) }
}
