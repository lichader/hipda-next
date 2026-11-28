package com.lichader.hipdanext.debug

import android.content.Context
import com.lichader.hipdanext.di.ExtraInterceptors
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import java.io.File

/** Debug builds only: adds the [ResponseRecorder] to the app's HTTP client. */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {

    @Provides
    @IntoSet
    @ExtraInterceptors
    fun provideResponseRecorder(@ApplicationContext context: Context): Interceptor =
        ResponseRecorder(File(context.filesDir, ResponseRecorder.DIR_NAME))
}
