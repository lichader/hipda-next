package com.lichader.hipdanext.di

import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.data.blacklist.DefaultBlacklistRepository
import com.lichader.hipdanext.data.user.DefaultUserRepository
import com.lichader.hipdanext.data.user.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBlacklistRepository(impl: DefaultBlacklistRepository): BlacklistRepository

    @Binds
    abstract fun bindUserRepository(impl: DefaultUserRepository): UserRepository
}
