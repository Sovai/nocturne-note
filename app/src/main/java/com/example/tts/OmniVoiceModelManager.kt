package com.example.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class OmniVoiceModelState(
  val published: Boolean = false,
  val installed: Boolean = false,
  val downloading: Boolean = false,
  val progressPercent: Int = 0,
  val totalBytes: Long = 0,
  val error: String? = null
)

/** Downloadable ONNX model cache. The model registry must contain verified
 * release assets; no untrusted arbitrary URL is accepted. This is a model
 * manager, NOT proof of the complete ONNX synthesis pipeline on Android.
 */
class OmniVoiceModelManager(context: Context) {
  private val directory = File(context.filesDir, "tts/omnivoice-onnx-int4")
  private val cachedCatalog = File(directory, "catalog.json")
  private val lock = Mutex()
  private val _state = MutableStateFlow(OmniVoiceModelState())
  val state = _state.asStateFlow()

  data class Asset(val relativePath: String, val url: String, val hash: String, val bytes: Long)

  private fun catalog(): List<Asset>? {
    val text = runCatching {
      val connection = (URL("https://raw.githubusercontent.com/Sovai/-nocturne-note-models/main/catalog.json")
        .openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 10_000
      }
      try {
        check(connection.responseCode == 200) { "Model catalog unavailable" }
        connection.inputStream.bufferedReader().use { it.readText() }
      } finally { connection.disconnect() }
    }.getOrElse {
      cachedCatalog.takeIf { it.isFile }?.readText()
        ?: throw IllegalStateException("Offline: no cached model catalog", it)
    }
    directory.mkdirs()
    cachedCatalog.writeText(text)
    val root = JSONObject(text)
    val models = root.getJSONArray("models")
    val model = (0 until models.length()).map { models.getJSONObject(it) }
      .firstOrNull { it.getString("id") == "omnivoice-onnx-int4" } ?: return null
    if (model.getString("status") != "available") return null
    val releases = model.getJSONArray("releases")
    if (releases.length() == 0) return null
    val files = releases.getJSONObject(releases.length()-1).getJSONObject("files")
    val assets = files.keys().asSequence().map { relative ->
      require(relative.matches(Regex("[a-zA-Z0-9_./-]+")) &&
        !relative.split('/').contains("..")) { "Unsafe model asset path" }
      val item = files.getJSONObject(relative)
      val url = item.getString("url")
      require(url.startsWith("https://github.com/Sovai/-nocturne-note-models/releases/download/"))
      val hash = item.getString("sha256").lowercase()
      require(hash.matches(Regex("[a-f0-9]{64}")))
      val size = item.getLong("size")
      require(size > 0 && size <= 2_100_000_000)
      Asset(relative, url, hash, size)
    }.toList()
    require(assets.isNotEmpty())
    return assets
  }

  suspend fun refresh() = withContext(Dispatchers.IO) {
    val files = runCatching { catalog() }.getOrNull()
    _state.value = _state.value.copy(published = files != null,
      installed = files?.all { asset -> isVerified(asset) } ?: false,
      totalBytes = files?.sumOf { it.bytes } ?: 0)
  }

  private fun isVerified(asset: Asset): Boolean {
    val file = File(directory, asset.relativePath)
    if (!file.isFile || file.length() != asset.bytes) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val bytes = ByteArray(1024*1024)
      while (true) { val n=input.read(bytes); if(n<0) break; digest.update(bytes,0,n) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == asset.hash
  }

  suspend fun download() = lock.withLock {
    withContext(Dispatchers.IO) {
      try {
        val files = catalog() ?: error("OmniVoice mobile release not published")
        directory.mkdirs()
        _state.value = OmniVoiceModelState(published = true, downloading = true,
          totalBytes = files.sumOf { it.bytes })
        var completed = 0L
        files.forEach { asset ->
          if (!isVerified(asset)) {
            val file = File(directory, asset.relativePath)
            file.parentFile?.mkdirs()
            val part = File(file.absolutePath + ".part")
            val partialBytes = part.length().takeIf { it < asset.bytes } ?: 0L
            val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
              connectTimeout = 20_000; readTimeout = 90_000
              instanceFollowRedirects = true
              if (partialBytes > 0) setRequestProperty("Range", "bytes=$partialBytes-")
            }
            try {
              val response = connection.responseCode
              check(response == 200 || response == 206)
              val append = response == 206 && partialBytes > 0
              val startingBytes = if (append) partialBytes else 0L
              connection.inputStream.use { input ->
                java.io.FileOutputStream(part, append).buffered().use { output ->
                  val buf = ByteArray(64 * 1024)
                  var size = startingBytes
                  while (true) {
                    val n = input.read(buf); if (n < 0) break
                    size += n
                    check(size <= asset.bytes) { "Model asset exceeds expected size" }
                    output.write(buf,0,n)
                    _state.value = _state.value.copy(progressPercent =
                      (((completed + size) * 100) / files.sumOf { it.bytes })
                        .coerceIn(0L,99L).toInt())
                  }
                }
              }
            } finally { connection.disconnect() }
            check(part.length() == asset.bytes)
            if (file.exists()) file.delete()
            check(part.renameTo(file))
            check(isVerified(asset)) { "SHA-256 validation failed: " + asset.relativePath }
          }
          completed += asset.bytes
        }
        _state.value = OmniVoiceModelState(published = true,
          installed = true, progressPercent = 100, totalBytes = completed)
      } catch (e: Exception) {
        _state.value = _state.value.copy(downloading = false,
          installed = false, error = e.message ?: "Download failed")
      }
    }
  }

  suspend fun delete() = lock.withLock {
    withContext(Dispatchers.IO) {
      directory.deleteRecursively()
      _state.value = _state.value.copy(installed = false, downloading = false,
        progressPercent = 0, error = null)
    }
  }
}
