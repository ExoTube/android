package com.example.exotube.share

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exotube.R
import com.example.exotube.domain.model.MediaFormat
import com.example.exotube.ui.components.BottomSheetDownload
import com.example.exotube.ui.theme.ExoTubeTheme

/**
 * Destino del menú "Compartir". Es transparente (ver Theme.ExoTube.Translucent), así que el
 * usuario sigue viendo TikTok/YouTube detrás del Bottom Sheet.
 */
class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels { ShareViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // También se ejecuta al rotar; el ViewModel ignora un enlace que ya está procesando.
        viewModel.onSharedText(intent.sharedText())

        setContent {
            ExoTubeTheme {
                ShareRoute(viewModel = viewModel, onClose = ::finish)
            }
        }
    }
}

/** Texto de un Intent ACTION_SEND de tipo text/…; null si el Intent es de otro tipo. */
private fun Intent.sharedText(): String? {
    if (action != Intent.ACTION_SEND || type?.startsWith("text/") != true) return null
    // getCharSequenceExtra y no getStringExtra: algunas apps envían texto con formato (Spanned).
    return getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
}

/** Conecta el ViewModel con la UI "tonta" (BottomSheetDownload), que solo recibe estado y eventos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareRoute(viewModel: ShareViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val startDownload = rememberDownloadPermissionGate(
        onReady = viewModel::onFormatSelected,
        onStorageDenied = {
            Toast.makeText(context, R.string.error_storage_permission, Toast.LENGTH_LONG).show()
        },
    )

    // Efecto de un solo disparo: al iniciar la descarga avisamos, bajamos la hoja y cerramos.
    LaunchedEffect(uiState) {
        val started = uiState as? ShareUiState.DownloadStarted ?: return@LaunchedEffect
        val message = context.getString(R.string.download_started, started.format.label)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        sheetState.hide()
        onClose()
    }

    BottomSheetDownload(
        uiState = uiState,
        onFormatSelected = startDownload,
        onRetry = viewModel::onRetry,
        onDismiss = onClose,
        sheetState = sheetState,
    )
}

/**
 * Devuelve una función que, antes de llamar a [onReady], pide los permisos que falten:
 *  - Android 13+: notificaciones (opcional: sin él la descarga funciona, pero sin progreso visible).
 *  - Android 8-9: almacenamiento (obligatorio para guardar en Movies/Music).
 */
@Composable
private fun rememberDownloadPermissionGate(
    onReady: (MediaFormat) -> Unit,
    onStorageDenied: () -> Unit,
): (MediaFormat) -> Unit {
    val context = LocalContext.current
    // Formato elegido mientras se muestra el diálogo del sistema.
    var pendingFormat by remember { mutableStateOf<MediaFormat?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val format = pendingFormat ?: return@rememberLauncherForActivityResult
        pendingFormat = null
        if (results[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false) onStorageDenied() else onReady(format)
    }

    return { format ->
        val missing = requiredDownloadPermissions().filterNot { context.hasPermission(it) }
        if (missing.isEmpty()) {
            onReady(format)
        } else {
            pendingFormat = format
            launcher.launch(missing.toTypedArray())
        }
    }
}

private fun requiredDownloadPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
