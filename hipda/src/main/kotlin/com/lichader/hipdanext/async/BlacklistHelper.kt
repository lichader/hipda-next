package com.lichader.hipdanext.async

import com.lichader.hipdanext.data.blacklist.BlacklistRepository
import com.lichader.hipdanext.di.ApplicationScope
import com.lichader.hipdanext.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A bridge to [BlacklistRepository] for Java code, which cannot call `suspend` functions. Its
 * last caller is `TaskHelper`'s daily sync; delete it once that moves (notifications, W11).
 */
@Singleton
class BlacklistHelper @Inject constructor(
    private val repository: BlacklistRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** Refreshes the device's copy of the blacklist in the background; failures are only logged. */
    fun syncBlacklists() {
        scope.launch {
            try {
                repository.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e)
            }
        }
    }
}
