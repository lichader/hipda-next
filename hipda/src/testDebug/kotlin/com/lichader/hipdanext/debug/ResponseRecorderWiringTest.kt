package com.lichader.hipdanext.debug

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.di.SingletonEntryPoint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** In debug builds the app's HTTP client carries the recorder, ahead of the other interceptors. */
@RunWith(AndroidJUnit4::class)
class ResponseRecorderWiringTest {

    @Test
    fun debugClientRecordsResponses() {
        val interceptors = SingletonEntryPoint.get().okHttpHelper().client.interceptors
        assertTrue(interceptors.toString(), interceptors.first() is ResponseRecorder)
    }
}
