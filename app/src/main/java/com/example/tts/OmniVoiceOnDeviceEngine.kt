package com.example.tts

import android.os.Build
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import java.nio.file.Paths
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

/**
 * Local OmniVoice ONNX engine. Port of onnx-community/OmniVoice-Onnx/inference.py:
 * text tokenizer -> audio_embeddings_encoder -> Qwen3 decoder -> 8 codebook heads
 * -> confidence-based iterative unmasking -> Higgs waveform decoder.
 *
 * No HTTP inference, Python or desktop machine. All models are read from the
 * user-downloaded, SHA256-checked files in private app storage.
 *
 * Only B=1 automatic voice is implemented. Cloning needs the optional Higgs
 * reference-audio encoder graph pipeline and is not enabled here.
 */
class OmniVoiceOnDeviceEngine(private val root: File, threads: Int = 2) : Closeable {
  private val environment = OrtEnvironment.getEnvironment()
  private val options = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(threads.coerceIn(1, 4))
    setInterOpNumThreads(1)
    // Avoid high transient peak memory from whole-graph optimizations on phones.
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
  }

  private fun file(path: String): String {
    val target = File(root, path)
    require(target.isFile) { "OmniVoice missing: $path" }
    return target.absolutePath
  }

  private val embeddingDelegate = lazy { environment.createSession(file("int4/audio_embeddings_encoder.onnx"), options) }
  private val llmDelegate = lazy { environment.createSession(file("int4/llm_decoder.onnx"), options) }
  private val headsDelegate = lazy { environment.createSession(file("int4/audio_heads_decoder.onnx"), options) }
  private val higgsDelegate = lazy { environment.createSession(file("audio_tokenizer/fp16/higgs_decoder.onnx"), options) }
  private val tokenizerDelegate = lazy {
    check(Build.VERSION.SDK_INT >= 26) { "OmniVoice requires Android 8.0 or newer" }
    HuggingFaceTokenizer.newInstance(Paths.get(file("int4/tokenizer.json")))
  }

  private val embedding get() = embeddingDelegate.value
  private val llm get() = llmDelegate.value
  private val heads get() = headsDelegate.value
  private val higgs get() = higgsDelegate.value
  private val tokenizer get() = tokenizerDelegate.value

  private fun longs(values: LongArray, vararg shape: Long) =
    OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape)

  private fun floats(values: FloatArray, vararg shape: Long) =
    OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), shape)

  private fun boolean(values: ByteArray, vararg shape: Long) =
    OnnxTensor.createTensor(environment,
      ByteBuffer.wrap(values), shape, OnnxJavaType.BOOL)

  private fun halfBits(number: Float): Short {
    val bits = java.lang.Float.floatToIntBits(number)
    val sign = (bits ushr 16) and 0x8000
    val exponent = ((bits ushr 23) and 0xff) - 127 + 15
    val mantissa = bits and 0x7fffff
    return when {
      exponent <= 0 -> sign.toShort()
      exponent >= 31 -> (sign or 0x7c00).toShort()
      else -> (sign or (exponent shl 10) or (mantissa ushr 13)).toShort()
    }
  }

  private fun floatTensor(values: FloatArray, shape: LongArray, type: OnnxJavaType): OnnxTensor =
    when (type) {
      OnnxJavaType.FLOAT16 -> {
        val buffer = ShortBuffer.wrap(ShortArray(values.size) { halfBits(values[it]) })
        OnnxTensor.createTensor(environment, buffer, shape, OnnxJavaType.FLOAT16)
      }
      else -> floats(values, *shape)
    }

  private fun expectedFloatType(session: OrtSession, name: String): OnnxJavaType =
    (session.inputInfo[name]?.info as? TensorInfo)?.type ?: OnnxJavaType.FLOAT

  private fun runFloat(
    session: OrtSession, output: String, inputs: Map<String, OnnxTensor>
  ): FloatArray {
    try {
      session.run(inputs).use { result ->
        val tensor = result[output].orElseThrow {
          IllegalStateException("OmniVoice ONNX missing output: $output")
        } as OnnxTensor
        val data = tensor.floatBuffer ?: error("Unsupported ONNX output type: $output")
        val array = FloatArray(data.remaining())
        data.get(array)
        return array
      }
    } finally {
      inputs.values.forEach { it.close() }
    }
  }

  private fun runBackbone(ids: LongArray, audioMask: ByteArray, sequence: Int): FloatArray {
    val embeddings = runFloat(embedding, "inputs_embeds", mapOf(
      "input_ids" to longs(ids, 1, 8, sequence.toLong()),
      "audio_mask" to boolean(audioMask, 1, sequence.toLong())
    ))

    val llmInputs = linkedMapOf<String, OnnxTensor>()
    val floatType = expectedFloatType(llm, "inputs_embeds")
    llmInputs["inputs_embeds"] = floatTensor(embeddings,
      longArrayOf(1, sequence.toLong(), 1024), floatType)
    if ("attention_mask" in llm.inputNames) {
      llmInputs["attention_mask"] = longs(LongArray(sequence) { 1L }, 1, sequence.toLong())
    }
    if ("position_ids" in llm.inputNames) {
      llmInputs["position_ids"] = longs(LongArray(sequence) { it.toLong() },
        1, sequence.toLong())
    }
    // GenAI-exported Qwen accepts empty key/value tensors on every full-sequence
    // decoding step; KV cache is intentionally not reused across mask refinements.
    llm.inputNames.filter { "past" in it }.forEach { name ->
      llmInputs[name] = floatTensor(FloatArray(0), longArrayOf(1, 8, 0, 128), floatType)
    }
    val hidden = runFloat(llm, "hidden_states", llmInputs)
    return runFloat(heads, "logits", mapOf("hidden_states" to
      floatTensor(hidden, longArrayOf(1, sequence.toLong(), 1024),
        expectedFloatType(heads, "hidden_states"))))
  }

  /**
   * Decode one text segment without a reference voice, matching upstream greedy
   * confidence-based unmasking. Speaker selection is not yet supported.
   */
  suspend fun synthesize(
    text: String,
    frames: Int = (text.length * 2.0).toInt().coerceIn(60, 256),
    steps: Int = 32
  ): SpeechAudio {
    require(steps in 4..32)
    require(frames in 24..400)
    val ids = tokenizer.encode(text).ids
    require(ids.isNotEmpty()) { "No text tokens" }
    require(ids.size <= 192) { "OmniVoice text too long; split the reading chunk" }
    val textLength = ids.size
    val sequence = textLength + frames
    val codes = LongArray(8 * sequence) { offset ->
      if (offset % sequence < textLength) ids[offset % sequence] else 1024L
    }
    val mask = ByteArray(sequence) { if (it >= textLength) 1 else 0 }
    val weights = floatArrayOf(8f, 8f, 6f, 6f, 4f, 4f, 2f, 2f)
    val confidence = FloatArray(frames)
    val best = IntArray(8 * frames)
    var stillMasked = frames

    for (step in 0 until steps) {
      currentCoroutineContext().ensureActive()
      if (stillMasked == 0) break
      val logits = runBackbone(codes, mask, sequence)
      require(logits.size == 8 * sequence * 1025) {
        "Unexpected OmniVoice audio-head shape; check ONNX converter version"
      }
      java.util.Arrays.fill(confidence, 0f)
      for (cb in 0 until 8) {
        for (pos in 0 until frames) {
          if (codes[cb * sequence + textLength + pos] != 1024L) continue
          val offset = (cb * sequence + textLength + pos) * 1025
          var maximum = -Float.MAX_VALUE
          var argMax = 0
          for (token in 0 until 1024) {
            val logit = logits[offset + token]
            if (logit > maximum) { maximum = logit; argMax = token }
          }
          var sum = 0.0
          for (token in 0 until 1024) {
            sum += exp((logits[offset + token] - maximum).toDouble())
          }
          best[cb * frames + pos] = argMax
          confidence[pos] += (weights[cb] / 40f) * (1.0 / sum).toFloat()
        }
      }
      val remainingPositions = (0 until frames).filter {
        codes[textLength + it] == 1024L
      }
      val fillCount = max(1, ceil(remainingPositions.size.toDouble() / (steps - step)).toInt())
      remainingPositions.sortedByDescending { confidence[it] }
        .take(fillCount).forEach { pos ->
          for (cb in 0 until 8) {
            codes[cb * sequence + textLength + pos] = best[cb * frames + pos].toLong()
          }
        }
      stillMasked -= fillCount
    }
    check(stillMasked == 0) { "OmniVoice decoder has unfilled codebook frames" }
    currentCoroutineContext().ensureActive()
    val audioCodes = LongArray(8 * frames) { idx ->
      codes[(idx / frames) * sequence + textLength + idx % frames]
    }
    val waveform = runFloat(higgs, "waveform_24k", mapOf("codes" to
      longs(audioCodes, 8, 1, frames.toLong())))
    require(waveform.isNotEmpty()) { "OmniVoice produced no audio" }
    return SpeechAudio(waveform, 24_000)
  }

  override fun close() {
    if (tokenizerDelegate.isInitialized()) runCatching { tokenizer.close() }
    if (higgsDelegate.isInitialized()) runCatching { higgs.close() }
    if (headsDelegate.isInitialized()) runCatching { heads.close() }
    if (llmDelegate.isInitialized()) runCatching { llm.close() }
    if (embeddingDelegate.isInitialized()) runCatching { embedding.close() }
    options.close()
  }

}
