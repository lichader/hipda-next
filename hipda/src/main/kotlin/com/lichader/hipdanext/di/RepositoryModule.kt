package com.lichader.hipdanext.di

import com.lichader.hipdanext.data.account.AccountRepository
import com.lichader.hipdanext.data.account.DefaultAccountRepository
import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.data.blacklist.DefaultBlacklistRepository
import com.lichader.hipdanext.data.lists.DefaultSimpleListRepository
import com.lichader.hipdanext.data.lists.SimpleListRepository
import com.lichader.hipdanext.data.threads.DefaultThreadListRepository
import com.lichader.hipdanext.data.threads.ThreadListRepository
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
    abstract fun bindAccountRepository(impl: DefaultAccountRepository): AccountRepository

    @Binds
    abstract fun bindBlacklistRepository(impl: DefaultBlacklistRepository): BlacklistRepository

    @Binds
    abstract fun bindSimpleListRepository(impl: DefaultSimpleListRepository): SimpleListRepository

    @Binds
    abstract fun bindThreadListRepository(impl: DefaultThreadListRepository): ThreadListRepository

    @Binds
    abstract fun bindUserRepository(impl: DefaultUserRepository): UserRepository
}
