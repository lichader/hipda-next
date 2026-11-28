package com.lichader.hipdanext.di

import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.data.blacklist.DefaultBlacklistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBlacklistRepository(impl: DefaultBlacklistRepository): BlacklistRepository
}
