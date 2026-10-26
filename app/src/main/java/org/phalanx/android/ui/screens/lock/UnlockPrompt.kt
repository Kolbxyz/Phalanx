package org.phalanx.android.ui.screens.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.phalanx.android.R

/** True when the phone has a PIN, pattern or password, which app lock falls back to and needs. */
internal fun canLockApp(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java).isDeviceSecure

/**
 * Asks the system to confirm the user: fingerprint or face where enrolled, else the phone's own
 * PIN, pattern or password. Returns the function that shows the prompt; [onResult] gets true once
 * the user is confirmed, false when they backed out.
 */
@Composable
internal fun rememberUnlockPrompt(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val report by rememberUpdatedState(onResult)
    val title = stringResource(R.string.lock_prompt_title)
    // Before Android 10, BiometricPrompt can't fall back to the PIN: the keyguard's own
    // confirmation screen does it all.
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        report(it.resultCode == Activity.RESULT_OK)
    }
    return remember(context, title) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                prompt(context, title) { report(it) }
            } else {
                val intent = context.getSystemService(KeyguardManager::class.java)
                    .createConfirmDeviceCredentialIntent(title, null)
                if (intent == null) report(false) else legacy.launch(intent)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun prompt(context: Context, title: String, onResult: (Boolean) -> Unit) {
    val builder = BiometricPrompt.Builder(context).setTitle(title)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
    } else {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
    }
    builder.build().authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

            // A finger not recognised is only one try: the prompt stays up for the next.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        }
    )
}
