package com.example.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class KhmerModelState(
  val installed: Boolean = false,
  val downloading: Boolean = false,
  val progress: Int = 0,
  val sizeBytes: Long = 0L,
  val error: String? = null
)

/**
 * Stores the user-downloaded, Sherpa-compatible MMS Khmer ONNX export.
 * Model weights inherit Meta's CC BY-NC 4.0 license; they are never bundled in the APK.
 */
class KhmerModelManager(context: Context) {
  private val directory = File(context.filesDir, "tts/mms-khm")
  private val model = File(directory, "model.onnx")
  private val tokens = File(directory, "tokens.txt")
  private val mutex = Mutex()

  private val _state = MutableStateFlow(snapshot())
  val state = _state.asStateFlow()

  val modelPath: String get() = model.absolutePath
  val tokensPath: String get() = tokens.absolutePath
  val isInstalled: Boolean get() = validFiles()

  private fun validFiles(): Boolean =
    model.isFile && model.length() > 20_000_000L &&
      tokens.isFile && tokens.length() in 50L..100_000L

  private fun snapshot(error: String? = null) = KhmerModelState(
    installed = validFiles(),
    sizeBytes = (if (model.exists()) model.length() else 0) +
      (if (tokens.exists()) tokens.length() else 0),
    error = error
  )

  fun refresh() {
    if (!_state.value.downloading) _state.value = snapshot()
  }

  suspend fun download() = mutex.withLock {
    withContext(Dispatchers.IO) {
      if (validFiles()) {
        _state.value = snapshot()
        return@withContext
      }
      directory.mkdirs()
      val pendingModel = File(directory, "model.onnx.part")
      val pendingTokens = File(directory, "tokens.txt.part")
      val base = "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/khm/"
      try {
        _state.value = KhmerModelState(downloading = true)
        fetch(base + "model.onnx", pendingModel, true)
        if (pendingModel.length() < 20_000_000L) error("Model file is incomplete")
        fetch(base + "tokens.txt", pendingTokens, false)
        if (pendingTokens.length() !in 50L..100_000L) error("Token file is invalid")
        if (!pendingModel.renameTo(model)) error("Could not save downloaded model")
        if (!pendingTokens.renameTo(tokens)) {
          model.delete()
          error("Could not save model tokens")
        }
        _state.value = snapshot()
      } catch (e: Exception) {
        _state.value = snapshot(e.localizedMessage ?: "Download failed")
      } finally {
        pendingModel.delete()
        pendingTokens.delete()
      }
    }
  }

  private fun fetch(address: String, destination: File, reportProgress: Boolean) {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
      connectTimeout = 20_000
      readTimeout = 60_000
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", "NocturneNote/1.0")
    }
    try {
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        error("Download server returned HTTP " + connection.responseCode)
      }
      val expectedSize = connection.contentLengthLong
      connection.inputStream.use { input ->
        destination.outputStream().buffered().use { output ->
          val buffer = ByteArray(64 * 1024)
          var downloaded = 0L
          while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            if (reportProgress && expectedSize > 0) {
              _state.value = KhmerModelState(
                downloading = true,
                progress = ((downloaded * 100L) / expectedSize).coerceIn(0, 99).toInt(),
                sizeBytes = downloaded
              )
            }
          }
        }
      }
      if (expectedSize > 0 && destination.length() != expectedSize) {
        error("Incomplete download. Retry to download the model again")
      }
    } finally {
      connection.disconnect()
    }
  }

  suspend fun delete() = mutex.withLock {
    withContext(Dispatchers.IO) {
      model.delete()
      tokens.delete()
      _state.value = snapshot()
    }
  }
}
