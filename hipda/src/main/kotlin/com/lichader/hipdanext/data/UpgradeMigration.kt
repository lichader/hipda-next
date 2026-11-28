package com.lichader.hipdanext.data

import com.lichader.hipdanext.bean.HiSettingsHelper
import com.lichader.hipdanext.ui.HiApplication
import com.lichader.hipdanext.utils.HiUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings stored settings up to date when the app starts after being installed or upgraded.
 * Moved unchanged from the removed `UpdateHelper.updateApp()`.
 */
@Singleton
class UpgradeMigration @Inject constructor(private val settings: HiSettingsHelper) {

    /**
     * Migrates the settings and records [currentVersion] as installed.
     *
     * @return true if this is the first start after an upgrade, so the release notes are shown
     */
    @JvmOverloads
    fun migrate(currentVersion: String = HiApplication.getAppVersion()): Boolean {
        settings.migrateEncrytSettings()

        val installedVersion: String? = settings.installedVersion
        if (currentVersion != installedVersion) {
            if (isNewer("5.0.03", installedVersion)) {
                // 5.0.03 moved the forum to the 4d4y domain.
                settings.setForumServer(HiUtils.ForumServer)
                settings.setImageHost(HiUtils.ImageHost)
                settings.setTailUrl(HiUtils.replaceOldDomain(settings.tailUrl))
            }
            settings.setInstalledVersion(currentVersion)
        }
        return isNewer(currentVersion, installedVersion)
    }

    companion object {
        /**
         * Whether [version] is newer than [than], for versions in the `#.#.##` format, by
         * comparing them with the dots removed. Other formats compare wrongly; see TD-46.
         */
        @JvmStatic
        fun isNewer(version: String, than: String?): Boolean {
            if (than.isNullOrEmpty()) return false
            return try {
                version.replace(".", "").toInt() > than.replace(".", "").toInt()
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
}
