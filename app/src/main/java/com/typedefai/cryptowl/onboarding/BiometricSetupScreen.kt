package com.typedefai.cryptowl.onboarding

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.typedefai.cryptowl.R
import com.typedefai.cryptowl.MainViewModel

/**
 * Optional fingerprint setup (separate flow, skippable): wraps the VaultKey
 * with the BioKey via a fresh BiometricPrompt, then adds `vault_key:biokey`
 * to vault.meta.
 */
@Composable
fun BiometricSetupScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val ready by viewModel.biometricReady.collectAsState()
    val cipher by viewModel.biometricCipher.collectAsState()
    val error by viewModel.biometricError.collectAsState()
    val created by viewModel.onboardingDone.collectAsState()
    val promptTitle = stringResource(R.string.biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.biometric_prompt_subtitle)

    // Enrollment finished (or was skipped): leave the onboarding graph.
    LaunchedEffect(created) {
        if (created) onDone()
    }

    LaunchedEffect(ready, cipher) {
        if (ready && cipher != null && activity != null) {
            runBiometricPrompt(
                context,
                activity,
                cipher!!,
                viewModel,
                promptTitle,
                promptSubtitle,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = null,
            modifier = Modifier.height(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.biometric_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.biometric_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.prepareBiometric() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.biometric_enable))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                viewModel.skipBiometric()
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.biometric_skip))
        }
    }
}

private fun runBiometricPrompt(
    context: Context,
    activity: FragmentActivity,
    cipher: javax.crypto.Cipher,
    viewModel: MainViewModel,
    title: String,
    subtitle: String,
) {
    if (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        != BiometricManager.BIOMETRIC_SUCCESS
    ) {
        viewModel.cancelBiometricPrompt()
        return
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let { viewModel.completeBiometric(it) }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancellations are user intent; the screen stays for a retry.
                viewModel.cancelBiometricPrompt()
            }
        },
    )
    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}
