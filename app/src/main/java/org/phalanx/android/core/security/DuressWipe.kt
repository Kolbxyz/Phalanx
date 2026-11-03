package org.phalanx.android.core.security

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull
import org.phalanx.android.data.repository.AccountRepository

/**
 * Erases Phalanx from the phone, as if it had just been installed: the account is signed out on
 * the server when it can be reached in time, then every file, preference, Keystore key, alarm and
 * job of the app is deleted and its process ends. The account itself is untouched: signing in
 * again with the recovery key brings the encrypted history back.
 */
@Singleton
class DuressWipe @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val account: AccountRepository
) {
    suspend fun wipe() {
        // Revoked first, so a copy of the files taken before the wipe holds no session that still works.
        withTimeoutOrNull(SIGN_OUT_TIMEOUT_MS) { account.signOut() }
        context.getSystemService(NotificationManager::class.java).cancelAll()
        // Ends this process when it succeeds.
        if (context.getSystemService(ActivityManager::class.java).clearApplicationUserData()) return
        // The system refused: delete what the app wrote itself, then end the process all the same.
        listOf(context.filesDir, context.noBackupFilesDir, context.cacheDir, context.codeCacheDir)
            .plus(listOf("databases", "shared_prefs", "app_webview").map { context.dataDir.resolve(it) })
            .forEach { it.deleteRecursively() }
        Process.killProcess(Process.myPid())
    }

    private companion object {
        // Long enough for a sign-out on a working network, short enough not to keep anyone waiting.
        const val SIGN_OUT_TIMEOUT_MS = 4_000L
    }
}
