package com.typedefai.cryptowl

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.typedefai.cryptowl.crypto.ProtectedValue

/**
 * Full-screen lock. On first composition it auto-prompts for fingerprint when
 * the vault has a biokey copy; the master-password field is always available
 * as the fallback (and the only path on cold start after lock-screen changes).
 */
@Composable
fun LockScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    val unlocking by viewModel.unlocking.collectAsState()
    val error by viewModel.unlockError.collectAsState()
    val biometricAvailable by viewModel.biometricUnlockAvailable.collectAsState()
    val bioCipher by viewModel.bioUnlockCipher.collectAsState()

    val promptTitle = stringResource(R.string.unlock_biometric_title)
    val promptSubtitle = stringResource(R.string.unlock_biometric_subtitle)
    val cancelButton = stringResource(R.string.action_cancel)

    var password by rememberSaveable { mutableStateOf("") }
    var autoPrompted by remember { mutableStateOf(false) }

    // Auto-prompt once when a biometric unlock is possible.
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && !autoPrompted) {
            autoPrompted = true
            viewModel.requestBiometricUnlock()
        }
    }

    // Show the system prompt once the cipher is ready.
    LaunchedEffect(bioCipher) {
        val cipher = bioCipher ?: return@LaunchedEffect
        if (activity != null) {
            authenticateWithBiometric(
                context = context,
                activity = activity,
                cipher = cipher,
                title = promptTitle,
                subtitle = promptSubtitle,
                negativeButtonText = cancelButton,
                onAuthenticated = viewModel::unlockWithBiometric,
                onCancelled = viewModel::cancelBiometricUnlock,
            )
        } else {
            viewModel.cancelBiometricUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.unlock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearUnlockError()
            },
            label = { Text(stringResource(R.string.password_label)) },
            singleLine = true,
            enabled = !unlocking,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (password.isNotEmpty()) {
                    viewModel.unlockVault(ProtectedValue.fromString(password))
                }
            },
            enabled = password.isNotEmpty() && !unlocking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (unlocking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.unlock_action))
            }
        }

        if (biometricAvailable && !unlocking) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.requestBiometricUnlock() }) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.unlock_biometric_action))
            }
        }
    }
}
