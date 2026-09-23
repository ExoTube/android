package com.example.exotube.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.exotube.BuildConfig
import com.example.exotube.ExoTubeApp
import com.example.exotube.data.settings.hasBeenUpdated
import com.example.exotube.data.update.ApkDownloadWorker
import com.example.exotube.data.update.ApkInstaller
import com.example.exotube.data.update.UpdateSettings
import com.example.exotube.domain.model.AppUpdate
import com.example.exotube.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Cómo va la descarga del APK. */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    /** [percent] es -1 mientras no se sabe cuánto pesa el archivo. */
    data class Running(val percent: Int) : UpdateDownload

    data class Ready(val apk: File) : UpdateDownload

    data object Failed : UpdateDownload
}

data class UpdateUiState(
    /** La versión nueva, si la hay y el usuario no la ha rechazado. */
    val available: AppUpdate? = null,
    val download: UpdateDownload = UpdateDownload.Idle,
    /** true la primera vez que se abre la app después de actualizarla. */
    val showWhatsNew: Boolean = false,
)

/**
 * Avisar de que hay versión nueva, descargarla y pasársela al instalador.
 *
 * La consulta se hace al abrir la app, además de la tarea diaria en segundo plano: así, cuando el
 * usuario toca la notificación, lo que ve ya está al día y no una respuesta guardada de ayer.
 */
class UpdateViewModel(
    private val repository: UpdateRepository,
    private val settings: UpdateSettings,
    private val installer: ApkInstaller,
    private val workManager: WorkManager,
    hasBeenUpdated: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            showWhatsNew = settings.consumeWhatsNew(BuildConfig.VERSION_CODE, hasBeenUpdated),
        ),
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        checkForUpdate()
        observeDownload()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val update = repository.findUpdate().getOrNull() ?: return@launch
            // Quien dijo "ahora no" a esta versión no vuelve a verla; a la siguiente, sí.
            if (settings.isDismissed(update.versionName)) return@launch
            _uiState.value = _uiState.value.copy(available = update)
        }
    }

    /**
     * La descarga la lleva un Worker, que sobrevive a que el usuario salga de la app. Aquí solo
     * se mira cómo va, de modo que al volver a entrar la barra sigue donde estaba.
     */
    private fun observeDownload() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ApkDownloadWorker.WORK_NAME).collect { infos ->
                val info = infos.lastOrNull() ?: return@collect
                _uiState.value = _uiState.value.copy(download = info.toDownloadState())
            }
        }
    }

    fun download(update: AppUpdate) {
        workManager.enqueueUniqueWork(
            ApkDownloadWorker.WORK_NAME,
            // REPLACE y no KEEP: si quedó un intento fallido, tocar el botón tiene que reintentar.
            ExistingWorkPolicy.REPLACE,
            ApkDownloadWorker.requestFor(update),
        )
    }

    /** "Ahora no": se cierra el aviso y no se vuelve a sacar con esta versión. */
    fun dismiss() {
        _uiState.value.available?.let { settings.dismiss(it.versionName) }
        _uiState.value = _uiState.value.copy(available = null)
    }

    fun whatsNewSeen() {
        _uiState.value = _uiState.value.copy(showWhatsNew = false)
    }

    /** false si el usuario todavía no autorizó a ExoTube a instalar aplicaciones. */
    fun canInstall(): Boolean = installer.canInstall()

    fun installIntent(apk: File) = installer.installIntent(apk)

    fun permissionSettingsIntent() = installer.permissionSettingsIntent()

    private fun WorkInfo.toDownloadState(): UpdateDownload = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING ->
            UpdateDownload.Running(progress.getInt(ApkDownloadWorker.KEY_PERCENT, ApkDownloadWorker.UNKNOWN_PERCENT))
        WorkInfo.State.SUCCEEDED ->
            outputData.getString(ApkDownloadWorker.KEY_FILE)
                ?.let { UpdateDownload.Ready(File(it)) }
                ?: UpdateDownload.Failed
        WorkInfo.State.FAILED -> UpdateDownload.Failed
        WorkInfo.State.BLOCKED -> UpdateDownload.Running(ApkDownloadWorker.UNKNOWN_PERCENT)
        WorkInfo.State.CANCELLED -> UpdateDownload.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExoTubeApp
                UpdateViewModel(
                    repository = app.container.updateRepository,
                    settings = app.container.updateSettings,
                    installer = app.container.apkInstaller,
                    workManager = WorkManager.getInstance(app),
                    hasBeenUpdated = app.hasBeenUpdated(),
                )
            }
        }
    }
}
