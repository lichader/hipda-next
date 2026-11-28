package com.lichader.hipdanext.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Interceptor
import javax.inject.Qualifier

/**
 * Interceptors that only some build variants add to the app's HTTP client, such as the
 * debug-only response recorder. Release builds add none.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExtraInterceptors

@Module
@InstallIn(SingletonComponent::class)
abstract class InterceptorModule {

    /** Declares the set, so it is empty when no variant adds to it. */
    @Multibinds
    @ExtraInterceptors
    abstract fun extraInterceptors(): Set<Interceptor>
}
