package com.example.exotube.update

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.exotube.R
import com.example.exotube.domain.model.AppUpdate
import com.example.exotube.ui.theme.ExoTubeTheme
import java.io.File

/**
 * Todo lo que el usuario ve de las actualizaciones: el aviso de que hay una nueva y, después de
 * instalarla, lo que trae.
 *
 * Se dibuja encima de la app y no ocupa sitio en ninguna pestaña: son dos cosas que aparecen
 * solas de vez en cuando y se cierran.
 */
@Composable
fun UpdateRoute(viewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // En cuanto el APK está listo, se abre el instalador de Android solo: el usuario ya dijo que
    // sí una vez, no tiene sentido pedirle un segundo toque.
    LaunchedEffect(state.download) {
        val ready = state.download as? UpdateDownload.Ready ?: return@LaunchedEffect
        context.startInstall(viewModel, ready.apk)
    }

    if (state.showWhatsNew) {
        WhatsNewSheet(onDismiss = viewModel::whatsNewSeen)
    }

    state.available?.let { update ->
        UpdateAvailableSheet(
            update = update,
            download = state.download,
            onDownload = { viewModel.download(update) },
            onInstall = { apk -> context.startInstall(viewModel, apk) },
            onDismiss = viewModel::dismiss,
        )
    }
}

/**
 * Abre el instalador de Android, o los ajustes si todavía no tiene permiso para instalar.
 *
 * Ninguna app puede instalarse a sí misma a escondidas, y está bien que sea así: si pudiera,
 * cualquiera podría reemplazarte una app por otra cosa sin que te enteraras.
 */
private fun Context.startInstall(viewModel: UpdateViewModel, apk: File) {
    val intent = if (viewModel.canInstall()) {
        viewModel.installIntent(apk)
    } else {
        viewModel.permissionSettingsIntent()
    }
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateAvailableSheet(
    update: AppUpdate,
    download: UpdateDownload,
    onDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        SheetBody(
            title = stringResource(R.string.update_available_title, update.versionName),
            subtitle = stringResource(
                R.string.update_available_size,
                Formatter.formatShortFileSize(context, update.sizeBytes),
            ),
            notes = update.notes.ifBlank { stringResource(R.string.update_no_notes) },
        ) {
            when (download) {
                is UpdateDownload.Running -> DownloadProgress(download.percent)
                is UpdateDownload.Ready -> Button(
                    onClick = { onInstall(download.apk) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_install))
                }
                else -> Column {
                    if (download is UpdateDownload.Failed) {
                        Text(
                            text = stringResource(R.string.update_download_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.update_download))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_later))
                    }
                }
            }
        }
    }
}

/** Lo que trae la versión recién instalada. Va dentro del APK, así que se ve sin internet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatsNewSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        SheetBody(
            title = stringResource(R.string.whats_new_title, com.example.exotube.BuildConfig.VERSION_NAME),
            subtitle = stringResource(R.string.whats_new_subtitle),
            notes = stringResource(R.string.whats_new_notes),
        ) {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.whats_new_continue))
            }
        }
    }
}

/** El cuerpo común de las dos hojas: título, línea pequeña, texto largo y botones abajo. */
@Composable
private fun SheetBody(
    title: String,
    subtitle: String,
    notes: String,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium,
            // Las notas pueden ser largas: que se puedan leer enteras sin tapar los botones.
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(24.dp))
        actions()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DownloadProgress(percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (percent >= 0) {
                    stringResource(R.string.update_downloading_percent, percent)
                } else {
                    stringResource(R.string.update_downloading_start)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (percent >= 0) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview
@Composable
private fun UpdateAvailableSheetPreview() {
    ExoTubeTheme {
        SheetBody(
            title = "ExoTube 1.2 ya está aquí",
            subtitle = "68 MB · se instala encima, sin perder tus descargas",
            notes = "Videoclips en Explorar\nPantalla completa\nÁlbumes\nRecortar audio",
        ) {
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Descargar") }
        }
    }
}
