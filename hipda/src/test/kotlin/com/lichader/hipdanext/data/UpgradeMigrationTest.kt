package com.lichader.hipdanext.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lichader.hipdanext.di.SingletonEntryPoint
import com.lichader.hipdanext.utils.HiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pins the behavior moved unchanged from `UpdateHelper.updateApp()`. */
@RunWith(AndroidJUnit4::class)
class UpgradeMigrationTest {

    private val settings = SingletonEntryPoint.get().settings()
    private val migration = UpgradeMigration(settings)

    @Test
    fun upgradeFromBefore5003MovesToTheNewDomain() {
        settings.setInstalledVersion("5.0.02")
        settings.setForumServer("https://www.hi-pda.com")
        settings.setImageHost("https://img.hi-pda.com")

        assertTrue(migration.migrate("5.0.05"))

        assertEquals(HiUtils.ForumServer, settings.forumServer)
        assertEquals(HiUtils.ImageHost, settings.imageHost)
        assertEquals("5.0.05", settings.installedVersion)
    }

    @Test
    fun upgradeFrom5003KeepsTheServerSettings() {
        settings.setInstalledVersion("5.0.03")
        settings.setForumServer("https://forum.invalid")

        assertTrue(migration.migrate("5.0.05"))

        assertEquals("https://forum.invalid", settings.forumServer)
    }

    @Test
    fun sameVersionIsNotAnUpgrade() {
        settings.setInstalledVersion("5.0.05")
        assertFalse(migration.migrate("5.0.05"))
    }

    @Test
    fun firstInstallIsNotAnUpgradeButIsRecorded() {
        settings.setInstalledVersion("")

        assertFalse(migration.migrate("5.0.05"))

        assertEquals("5.0.05", settings.installedVersion)
    }

    @Test
    fun anUpgradeFromALaterNumberingKeepsTheServerSettings() {
        settings.setInstalledVersion("5.1.0")
        settings.setForumServer("https://forum.invalid")

        assertTrue(migration.migrate("5.1.1"))

        assertEquals("https://forum.invalid", settings.forumServer)
    }

    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(UpgradeMigration.isNewer("5.1.0", "5.0.05"))
        assertTrue(UpgradeMigration.isNewer("5.0.10", "5.0.05"))
        assertTrue(UpgradeMigration.isNewer("6", "5.9.99"))
        assertFalse(UpgradeMigration.isNewer("5.0.03", "5.1.0"))
        assertFalse(UpgradeMigration.isNewer("5.1", "5.1.0"))
        assertFalse(UpgradeMigration.isNewer("5.1.0-beta", "5.0.05"))
    }

    @Test
    fun versionsCompareInTheHashDotHashDotHashHashFormat() {
        assertTrue(UpgradeMigration.isNewer("5.0.05", "5.0.03"))
        assertFalse(UpgradeMigration.isNewer("5.0.03", "5.0.05"))
        assertFalse(UpgradeMigration.isNewer("5.0.05", "5.0.05"))
        assertFalse(UpgradeMigration.isNewer("5.0.05", null))
        assertFalse(UpgradeMigration.isNewer("5.0.05", "beta"))
    }
}
