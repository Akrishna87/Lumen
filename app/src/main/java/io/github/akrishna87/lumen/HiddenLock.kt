package io.github.akrishna87.lumen

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Asks for the fingerprint, face or screen lock before the Hidden album opens, like Face ID on iPhone. */
object HiddenLock {
    /**
     * Whether the phone has a screen lock at all. Without one there's nothing to ask for, so Hidden
     * opens straight away (as on an iPhone with no passcode). This deliberately doesn't use the
     * biometric error codes: "sensor unavailable" on a phone with a PIN must still ask for the PIN.
     */
    fun isSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun unlock(activity: FragmentActivity, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            },
        )
        // No negative button: with the screen lock allowed, Android supplies "Use PIN" itself.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hidden Album")
            .setSubtitle("Hidden photos stay out of Lumen's Library and albums.")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}
