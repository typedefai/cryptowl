package com.typedefai.cryptowl

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat

private data class LanguageOption(
    val tag: String?,
    @StringRes val labelRes: Int,
)

private val languageOptions = listOf(
    LanguageOption(null, R.string.language_system),
    LanguageOption("en", R.string.language_en),
    LanguageOption("zh-CN", R.string.language_zh_cn),
    LanguageOption("zh-TW", R.string.language_zh_tw),
)

/** Icon button that opens the in-app language picker. */
@Composable
fun LanguageButton(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    IconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(
            Icons.Rounded.Language,
            contentDescription = stringResource(R.string.language_option),
        )
    }
    if (showDialog) {
        LanguageDialog(onDismiss = { showDialog = false })
    }
}

/** App-language picker: Follow system / English / 简体中文 / 繁體中文. */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_option)) },
        text = {
            Column {
                languageOptions.forEach { option ->
                    val selected = current == (option.tag ?: "")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (option.tag == null) {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                } else {
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
                                }
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}