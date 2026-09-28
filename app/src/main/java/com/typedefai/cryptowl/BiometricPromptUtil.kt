package com.typedefai.cryptowl

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** True when strong biometrics are enrolled and usable on this device. */
fun canAuthenticateWithBiometric(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows the system BiometricPrompt for [cipher] (setup or unlock). The
 * authorized cipher is handed to [onAuthenticated]; cancellation and errors
 * (including an unavailable sensor) call [onCancelled].
 *
 * [negativeButtonText] is required by BiometricPrompt whenever
 * DEVICE_CREDENTIAL is not among the allowed authenticators.
 */
fun authenticateWithBiometric(
    context: Context,
    activity: FragmentActivity,
    cipher: Cipher,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    onAuthenticated: (Cipher) -> Unit,
    onCancelled: () -> Unit,
) {
    if (!canAuthenticateWithBiometric(context)) {
        onCancelled()
        return
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText(negativeButtonText)
        .build()
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authorized = result.cryptoObject?.cipher
                if (authorized != null) onAuthenticated(authorized) else onCancelled()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancellations are user intent; callers keep their fallback UI.
                onCancelled()
            }
        },
    )
    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}
