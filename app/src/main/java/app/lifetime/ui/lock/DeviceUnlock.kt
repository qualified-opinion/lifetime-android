package app.lifetime.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The phone's own unlock (fingerprint, face, or PIN/pattern/password) via BiometricPrompt. */
object DeviceUnlock {
    enum class Result { UNLOCKED, CANCELLED, NO_SCREEN_LOCK }

    // The only combination that includes the PIN fallback on every Android version from 8 up.
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun hasScreenLock(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun ask(activity: FragmentActivity, title: String, onResult: (Result) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(Result.UNLOCKED)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) Result.NO_SCREEN_LOCK
                    else Result.CANCELLED,
                )
            }
            // onAuthenticationFailed is one rejected finger or face; the prompt stays open.
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}

/** The activity behind a Compose context (LocalContext can be a wrapper). */
fun Context.findFragmentActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}
