/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.example.studiosound.studiosound

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.android.example.studiosound.audio.PcmFile
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.moduleinstall.ModuleAvailabilityResponse
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.media.effect.studiosound.AudioFormat
import com.google.android.gms.media.effect.studiosound.ProcessingResult
import com.google.android.gms.media.effect.studiosound.StudioSound
import com.google.android.gms.media.effect.studiosound.StudioSoundCallback
import com.google.android.gms.media.effect.studiosound.StudioSoundClient
import com.google.android.gms.media.effect.studiosound.StudioSoundOptions
import com.google.android.gms.media.effect.studiosound.StudioSoundSession
import com.google.android.gms.media.effect.studiosound.StudioSoundSessionCallback
import com.google.android.gms.media.effect.studiosound.StudioSoundStatusCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Whether Studio Sound can be used on this device right now. */
sealed interface StudioSoundAvailability {
  data object Checking : StudioSoundAvailability

  /** Studio Sound can't run here; [reason] explains why (device model vs. Play services). */
  data class Unsupported(val reason: String) : StudioSoundAvailability

  data object ModuleNotInstalled : StudioSoundAvailability

  data class Installing(val stage: InstallStage, val progress: Int? = null) : StudioSoundAvailability

  data object Ready : StudioSoundAvailability

  data class Error(val message: String) : StudioSoundAvailability
}

enum class InstallStage { PENDING, DOWNLOADING, PAUSED, INSTALLING }

class StudioSoundException(
  message: String,
  val statusCode: Int? = null,
  cause: Throwable? = null,
) : Exception(message, cause)

/**
 * One isolated sound category returned by Studio Sound, stored as a PCM file. Studio Sound may
 * return the same type more than once (e.g. several SPEECH outputs); [label] disambiguates them.
 */
data class Stem(val id: String, val type: StemType, val label: String, val pcm: PcmFile)

data class SeparationProgress(val completedChunks: Int, val totalChunks: Int) {
  val fraction: Float get() = if (totalChunks == 0) 0f else completedChunks.toFloat() / totalChunks
}

data class SeparationResult(
  val stems: List<Stem>,
  val chunkCount: Int,
  val processingTimeMs: Long,
  val audioDurationMs: Long,
) {
  /** How many times faster than real time the separation ran. */
  val speedFactor: Float get() = if (processingTimeMs > 0) audioDurationMs.toFloat() / processingTimeMs else 0f
}

/**
 * Coroutine-friendly wrapper around the Google Play services Studio Sound API.
 *
 * - Tracks device support and the optional module's install state ([availability]) and installs
 *   the module on demand.
 * - [separate] streams a (possibly long) [PcmFile] through a [StudioSoundSession] chunk by chunk
 *   and writes every isolated stem to its own [PcmFile], so memory use stays flat.
 */
class StudioSoundEngine(context: Context) {

  private val appContext = context.applicationContext
  private val client: StudioSoundClient by lazy { StudioSound.getClient(appContext) }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val mutex = Mutex()

  /** Whether the running device model is natively listed in the SDK's built-in allowlist. */
  val isDeviceOnClientAllowlist: Boolean = isModelOnClientAllowlist()

  /** Whether the debug allowlist override can be toggled in the status dialog. */
  val canIgnoreDeviceAllowlist: Boolean = !isDeviceOnClientAllowlist

  private val _ignoreDeviceAllowlist = MutableStateFlow(false)
  val ignoreDeviceAllowlist: StateFlow<Boolean> = _ignoreDeviceAllowlist.asStateFlow()

  private val _availability = MutableStateFlow<StudioSoundAvailability>(StudioSoundAvailability.Checking)
  val availability: StateFlow<StudioSoundAvailability> = _availability.asStateFlow()

  val playServicesVersionName: String
    get() = playServicesVersion()

  /** Re-checks availability in the background (results are published to [availability]). */
  fun refresh() {
    scope.launch { checkAvailability() }
  }

  /**
   * Debug override: tries the module on a device that isn't on the client library's allowlist.
   * Google Play services may still refuse session creation with [StudioSoundStatusCodes.DEVICE_NOT_SUPPORTED].
   */
  fun setIgnoreDeviceAllowlist(enabled: Boolean) {
    if (!canIgnoreDeviceAllowlist) return
    _ignoreDeviceAllowlist.value = enabled
    setClientModelAllowlistOverride(enabled)
    refresh()
  }

  /** Starts installing the optional module in the background (progress goes to [availability]). */
  fun install() {
    scope.launch { installModule() }
  }

  suspend fun checkAvailability(): StudioSoundAvailability = mutex.withLock { checkLocked() }

  suspend fun installModule(): StudioSoundAvailability = mutex.withLock { installLocked() }

  /** Suspends until Studio Sound is ready, installing the module if needed. Throws if it can't be. */
  suspend fun awaitReady() {
    mutex.withLock {
      var state = _availability.value
      if (state !is StudioSoundAvailability.Ready) state = checkLocked()
      if (state is StudioSoundAvailability.ModuleNotInstalled) state = installLocked()
      when (state) {
        StudioSoundAvailability.Ready -> Unit
        is StudioSoundAvailability.Unsupported ->
          throw StudioSoundException(state.reason, StudioSoundStatusCodes.DEVICE_NOT_SUPPORTED)
        is StudioSoundAvailability.Error -> throw StudioSoundException(state.message)
        else -> throw StudioSoundException("Studio Sound isn't available right now.")
      }
    }
  }

  private suspend fun checkLocked(): StudioSoundAvailability {
    val previous = _availability.value
    _availability.value = StudioSoundAvailability.Checking
    if (!isDeviceOnClientAllowlist) {
      setClientModelAllowlistOverride(_ignoreDeviceAllowlist.value)
    }
    val result =
      try {
        when {
          !client.isDeviceSupported().await() -> StudioSoundAvailability.Unsupported(diagnoseUnsupported())
          client.isModuleInstalled().await() -> StudioSoundAvailability.Ready
          else -> StudioSoundAvailability.ModuleNotInstalled
        }
      } catch (e: CancellationException) {
        _availability.value = previous
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Availability check failed", e)
        StudioSoundAvailability.Error(describeStudioSoundError(e))
      }
    Log.i(TAG, "Studio Sound availability: $result (ignoreAllowlist=${_ignoreDeviceAllowlist.value})")
    _availability.value = result
    return result
  }

  /**
   * `isDeviceSupported()` is false either because the SDK's built-in model list doesn't include this
   * device, or because Google Play services doesn't offer the Studio Sound module here (yet). Asking
   * ModuleInstall directly tells the two apart.
   */
  private suspend fun diagnoseUnsupported(): String {
    val gms = playServicesVersion()
    val status =
      try {
        ModuleInstall.getClient(appContext).areModulesAvailable(client).await().availabilityStatus
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "ModuleInstall availability query failed", e)
        return "Google Play services ($gms) can't provide Studio Sound on this ${Build.MODEL}: " +
          "${describeStudioSoundError(e)}. Updating Google Play services may help."
      }
    Log.i(TAG, "isDeviceSupported=false; ModuleInstall status=$status; model=${Build.MODEL}; gms=$gms")
    return when (status) {
      ModuleAvailabilityResponse.AvailabilityStatus.STATUS_ALREADY_AVAILABLE,
      ModuleAvailabilityResponse.AvailabilityStatus.STATUS_READY_TO_DOWNLOAD -> unsupportedDeviceMessage()
      else ->
        "Google Play services ($gms) doesn't offer the Studio Sound module on this ${Build.MODEL} yet " +
          "(module status $status). Update Google Play services and try again."
    }
  }

  private fun playServicesVersion(): String {
    val code = runCatching { GoogleApiAvailability.getInstance().getApkVersion(appContext) }.getOrDefault(0)
    // Version codes look like 264131035 → v26.41.31.
    return if (code > 0) "v%d.%02d.%02d".format(code / 10_000_000, code / 100_000 % 100, code / 1_000 % 100) else "unknown version"
  }

  private suspend fun installLocked(): StudioSoundAvailability {
    // Install callbacks can arrive after the Task completes; ignore those so they can't overwrite
    // the final state.
    val finished = AtomicBoolean(false)
    val lastError = AtomicReference<String?>(null)
    fun publish(state: StudioSoundAvailability) {
      if (!finished.get()) _availability.value = state
    }

    publish(StudioSoundAvailability.Installing(InstallStage.PENDING))
    val callback = object : StudioSoundClient.InstallStatusCallback {
      override fun onDownloadPending() = publish(StudioSoundAvailability.Installing(InstallStage.PENDING))

      override fun onDownloadStart() = publish(StudioSoundAvailability.Installing(InstallStage.DOWNLOADING, 0))

      override fun onDownloadProgressUpdate(progress: Int) =
        publish(StudioSoundAvailability.Installing(InstallStage.DOWNLOADING, progress.coerceIn(0, 100)))

      override fun onDownloadPaused() {
        val progress = (_availability.value as? StudioSoundAvailability.Installing)?.progress
        publish(StudioSoundAvailability.Installing(InstallStage.PAUSED, progress))
      }

      override fun onDownloadComplete() = publish(StudioSoundAvailability.Installing(InstallStage.INSTALLING, 100))

      override fun onInstalled() {
        Log.i(TAG, "Studio Sound module installed")
      }

      override fun onCancelled() {
        lastError.set("The Studio Sound download was cancelled.")
      }

      override fun onError(description: String) {
        Log.w(TAG, "Module install error: $description")
        lastError.set(description)
      }
    }

    val result =
      try {
        if (client.installModule(callback).await()) {
          StudioSoundAvailability.Ready
        } else {
          StudioSoundAvailability.Error(lastError.get() ?: "The Studio Sound module couldn't be installed.")
        }
      } catch (e: CancellationException) {
        finished.set(true)
        _availability.value = StudioSoundAvailability.ModuleNotInstalled
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Module install failed", e)
        StudioSoundAvailability.Error(describeStudioSoundError(e))
      }
    finished.set(true)
    _availability.value = result
    return result
  }

  /**
   * Runs [input] through a Studio Sound session and writes each isolated stem into [outputDir].
   *
   * The session pipeline only allows one buffer in flight: dequeue → fill → queue → wait for
   * `onProcessed` → repeat. Queueing again before the previous chunk finishes is rejected by the
   * service (with `MAX_SESSIONS_REACHED`), so chunks are strictly serialized here.
   *
   * @param chunkDurationMs preferred chunk length. Each input buffer holds up to 2 minutes of audio;
   *   shorter chunks give finer-grained progress.
   */
  suspend fun separate(
    input: PcmFile,
    outputDir: File,
    chunkDurationMs: Long = DEFAULT_CHUNK_DURATION_MS,
    onProgress: (SeparationProgress) -> Unit = {},
  ): SeparationResult = withContext(Dispatchers.IO) {
    val totalFrames = input.frameCount
    if (totalFrames == 0L) throw StudioSoundException("The audio is empty.")
    outputDir.mkdirs()
    val startedAt = SystemClock.elapsedRealtime()

    // 1. Configure the session for this audio. Studio Sound requires float PCM, 1–2 channels and
    //    8–48 kHz; AudioDecoder already guarantees that.
    val format = AudioFormat.Builder()
      .setSampleRate(input.sampleRate)
      .setChannelCount(input.channelCount)
      .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
      .build()
    val options = StudioSoundOptions.Builder(format).build()

    val disconnected = CompletableDeferred<Int>()
    val session: StudioSoundSession =
      try {
        client.createSession(
          options,
          object : StudioSoundSessionCallback {
            override fun onSessionDisconnected(statusCode: Int) {
              Log.w(TAG, "Session disconnected: ${statusName(statusCode)}")
              disconnected.complete(statusCode)
            }

            override fun onSessionDestroyed() {
              Log.d(TAG, "Session destroyed")
            }
          },
        ).await()
      } catch (e: ApiException) {
        if (e.statusCode == StudioSoundStatusCodes.DEVICE_NOT_SUPPORTED) {
          _availability.value = StudioSoundAvailability.Unsupported(
            "Google Play services rejected ${Build.MODEL} (${Build.DEVICE}) [DEVICE_NOT_SUPPORTED].",
          )
        }
        throw StudioSoundException(describeStudioSoundError(e), e.statusCode, e)
      }
    Log.i(TAG, "Session created for ${session.audioFormat}")

    // 2. Receive results on a dedicated thread. ProcessingResult buffers are owned by the service
    //    and can be overwritten as soon as the callback returns, so they're written to disk inside
    //    the callback.
    val callbackExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "StudioSound-callback") }
    val outcomes = Channel<ChunkOutcome>(Channel.UNLIMITED)
    val stems = StemFiles(outputDir, input.bytesPerFrame)
    // Length of the chunk currently being processed (only one is ever in flight).
    val inFlightFrames = AtomicLong(0)
    try {
      session.registerCallback(
        callbackExecutor,
        object : StudioSoundCallback {
          override fun onProcessed(processingResults: List<ProcessingResult>) {
            val outcome =
              try {
                ChunkOutcome.Processed(stems.append(processingResults, inFlightFrames.get()))
              } catch (t: Throwable) {
                ChunkOutcome.Failed(t)
              }
            outcomes.trySend(outcome)
          }

          override fun onError(statusCode: Int) {
            outcomes.trySend(ChunkOutcome.Error(statusCode))
          }

          override fun onCancelled(statusCode: Int) {
            outcomes.trySend(ChunkOutcome.Error(statusCode))
          }
        },
      )

      // 3. Feed the audio chunk by chunk.
      val bytesPerFrame = input.bytesPerFrame
      val preferredChunkFrames = (chunkDurationMs * input.sampleRate / 1000).coerceAtLeast(1L)
      var totalChunks = ceilDiv(totalFrames, preferredChunkFrames)
      var framesDone = 0L
      var chunk = 0
      onProgress(SeparationProgress(0, totalChunks))
      FileInputStream(input.file).channel.use { source ->
        while (framesDone < totalFrames) {
          ensureActive()
          val buffer =
            session.dequeueInputBuffer() ?: throw StudioSoundException("Studio Sound has no free input buffer.")
          // Studio Sound pads each chunk's output up to the next 0.5 s boundary plus 62 frames, and
          // its output shared-memory buffer has the exact same byte capacity as the input buffer.
          // Leaving 1 second of headroom prevents a full buffer from overflowing the destination slot.
          val capacityFrames = ((buffer.capacity() / bytesPerFrame).toLong() - input.sampleRate).coerceAtLeast(1L)
          val maxChunkFrames = minOf(preferredChunkFrames, capacityFrames)
          val chunkFrames = minOf(maxChunkFrames, totalFrames - framesDone)
          if (chunk == 0) totalChunks = ceilDiv(totalFrames, maxChunkFrames)

          // Fill from the start of the slot, then flip: the service processes buffer.remaining()
          // bytes starting at offset 0.
          buffer.clear()
          buffer.limit((chunkFrames * bytesPerFrame).toInt())
          source.position(framesDone * bytesPerFrame)
          while (buffer.hasRemaining()) {
            if (source.read(buffer) < 0) break
          }
          buffer.flip()

          val chunkStartedAt = SystemClock.elapsedRealtime()
          inFlightFrames.set(chunkFrames)
          session.queueInputBuffer(buffer)
          val outcome =
            withTimeoutOrNull(chunkTimeoutMs(chunkFrames, input.sampleRate)) {
              select<ChunkOutcome> {
                outcomes.onReceive { it }
                disconnected.onAwait { ChunkOutcome.Error(it) }
              }
            } ?: throw StudioSoundException("Timed out waiting for Studio Sound to process the audio.")

          when (outcome) {
            is ChunkOutcome.Processed ->
              Log.i(
                TAG,
                "Chunk ${chunk + 1}/$totalChunks: $chunkFrames frames in → ${outcome.summary} " +
                  "(${SystemClock.elapsedRealtime() - chunkStartedAt} ms)",
              )
            is ChunkOutcome.Error ->
              throw StudioSoundException(
                "${statusHint(outcome.statusCode) ?: "Studio Sound couldn't process the audio."} " +
                  "[${statusName(outcome.statusCode)}]",
                outcome.statusCode,
              )
            is ChunkOutcome.Failed ->
              throw StudioSoundException("Couldn't save the processed audio.", cause = outcome.error)
          }
          framesDone += chunkFrames
          chunk++
          onProgress(SeparationProgress(chunk, totalChunks))
        }
      }

      // 4. Make every stem exactly as long as the input, so they line up with the original.
      val result =
        stems.finish(totalFrames)
          .map { (key, file) ->
            Stem(key.id, StemType.fromAudioType(key.audioType), key.label, PcmFile(file, input.sampleRate, input.channelCount))
          }
          .sortedWith(compareBy({ it.type.ordinal }, { it.id }))
      if (result.isEmpty()) throw StudioSoundException("Studio Sound didn't return any audio.")
      SeparationResult(
        stems = result,
        chunkCount = chunk,
        processingTimeMs = SystemClock.elapsedRealtime() - startedAt,
        audioDurationMs = input.durationMs,
      ).also { Log.i(TAG, "Separated ${it.audioDurationMs} ms of audio into ${it.stems.size} stems in ${it.processingTimeMs} ms") }
    } catch (e: CancellationException) {
      runCatching { session.cancel() }
      throw e
    } finally {
      runCatching { session.unregisterCallback() }
      runCatching { session.release() }
      callbackExecutor.shutdown()
      runCatching { callbackExecutor.awaitTermination(2, TimeUnit.SECONDS) }
      stems.close()
    }
  }

  private sealed interface ChunkOutcome {
    class Processed(val summary: String) : ChunkOutcome

    class Error(val statusCode: Int) : ChunkOutcome

    class Failed(val error: Throwable) : ChunkOutcome
  }

  companion object {
    private const val TAG = "StudioSoundEngine"

    /**
     * Default chunk length (115 s): fits inside the session's 120 s buffer capacity with 5 s of
     * headroom for Studio Sound's 0.5 s + 62-frame output tail padding, while keeping 3-minute
     * songs down to 2 chunks.
     */
    const val DEFAULT_CHUNK_DURATION_MS = 115_000L

    private fun ceilDiv(a: Long, b: Long): Int = ((a + b - 1) / b).toInt()

    private fun chunkTimeoutMs(frames: Long, sampleRate: Int): Long = 60_000L + frames * 4_000L / sampleRate

    /** Checks whether [Build.MODEL] is present in the 16.0.0-beta03 client allowlist (`zzbq.zza`). */
    private fun isModelOnClientAllowlist(): Boolean {
      return runCatching {
        val model = Build.MODEL.lowercase(java.util.Locale.US)
        val clazz = Class.forName("com.google.android.gms.internal.media_effect_studiosound.zzbq")
        val field = clazz.getDeclaredField("zza").apply { isAccessible = true }
        val current = field.get(null) as? Set<*> ?: return true
        model in current
      }.getOrDefault(true)
    }

    /**
     * Adds or removes [Build.MODEL] in the 16.0.0-beta03 client library's `zzbq.zza` allowlist
     * when the user toggles "Bypass client allowlist" in the diagnostic dialog.
     */
    private fun setClientModelAllowlistOverride(enabled: Boolean) {
      runCatching {
        val model = Build.MODEL.lowercase(java.util.Locale.US)
        val clazz = Class.forName("com.google.android.gms.internal.media_effect_studiosound.zzbq")
        val field = clazz.getDeclaredField("zza").apply { isAccessible = true }
        val current = field.get(null) as? Set<*> ?: return
        var c: Class<*>? = current.javaClass
        while (c != null && c != Any::class.java) {
          for (f in c.declaredFields) {
            f.isAccessible = true
            val inner = f.get(current)
            if (inner is MutableCollection<*> && inner !== current) {
              @Suppress("UNCHECKED_CAST")
              val mutable = inner as MutableCollection<String>
              if (enabled) {
                mutable.add(model)
                Log.i(TAG, "Added '$model' to Studio Sound client model allowlist (debug override)")
              } else {
                mutable.remove(model)
                Log.i(TAG, "Removed '$model' from Studio Sound client model allowlist")
              }
              return
            }
          }
          c = c.superclass
        }
      }.onFailure { Log.w(TAG, "Could not update client model allowlist", it) }
    }
  }
}

/**
 * Collects the isolated audio returned for each chunk into one PCM file per stem, keeping all stems
 * aligned on the same timeline. Only touched from the callback thread while a chunk is in flight.
 */
private class StemFiles(private val dir: File, private val bytesPerFrame: Int) : Closeable {

  /** One stem per sound category. */
  data class Key(val audioType: Int) {
    val id: String get() = StemType.fromAudioType(audioType).name.lowercase()
    val label: String get() = StemType.fromAudioType(audioType).label
  }

  private val channels = LinkedHashMap<Key, FileChannel>()
  private val files = LinkedHashMap<Key, File>()
  private var timelineBytes = 0L
  private val zeros: ByteBuffer = ByteBuffer.allocateDirect(64 * 1024)
  private val sumBlock = FloatArray(MERGE_BLOCK_SAMPLES)
  private val readBlock = FloatArray(MERGE_BLOCK_SAMPLES)
  private val writeBlock: ByteBuffer =
    ByteBuffer.allocateDirect(MERGE_BLOCK_SAMPLES * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

  /**
   * Appends one chunk's results, keeping exactly [inputFrames] frames per stem.
   *
   * Two things measured on-device shape this:
   * - Output is sample-aligned with the input at the start but padded at the end (rounded up to
   *   whole 0.5 s blocks, plus 62 frames). The padding is trimmed so stems never drift.
   * - Studio Sound can return the same type several times (e.g. three SPEECH outputs), in an order
   *   that isn't stable across chunks. Outputs of the same type are summed into one stem, so each
   *   category (Speech, Music, ...) behaves consistently for the whole clip.
   *
   * @return a short summary for logging.
   */
  fun append(results: List<ProcessingResult>, inputFrames: Long): String {
    val chunkBytes = inputFrames * bytesPerFrame
    val summary = StringBuilder()
    for ((audioType, outputs) in results.groupBy { it.audioType }) {
      val key = Key(audioType)
      val channel = channels.getOrPut(key) {
        val file = File(dir, "stem_${key.id}.pcm")
        files[key] = file
        FileOutputStream(file).channel.also { padTo(it, timelineBytes) }
      }
      val startSize = channel.size()
      if (outputs.size == 1) {
        // Copy the bytes as-is (native byte order), on a duplicate so the service-owned buffer's
        // position/limit are left untouched.
        val data = outputs.single().byteBuffer.duplicate()
        val available = data.remaining() - data.remaining() % bytesPerFrame
        data.limit(data.position() + minOf(available.toLong(), chunkBytes).toInt())
        while (data.hasRemaining()) channel.write(data)
      } else {
        writeSum(outputs, (chunkBytes / Float.SIZE_BYTES).toInt(), channel)
      }
      if (summary.isNotEmpty()) summary.append(", ")
      summary.append(key.id)
      if (outputs.size > 1) summary.append('×').append(outputs.size)
      summary.append('=').append(outputs.maxOf { it.byteBuffer.remaining() } / bytesPerFrame)
      check(channel.size() - startSize <= chunkBytes)
    }
    timelineBytes += chunkBytes
    // A stem missing from this chunk (or shorter than the input) is padded with silence.
    channels.values.forEach { padTo(it, timelineBytes) }
    return summary.toString()
  }

  /** Writes the sample-wise sum of [outputs] (first [samples] samples), block by block. */
  private fun writeSum(outputs: List<ProcessingResult>, samples: Int, channel: FileChannel) {
    val views = outputs.map { it.byteBuffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer() }
    val total = minOf(samples, views.maxOf { it.limit() })
    var offset = 0
    while (offset < total) {
      val n = minOf(MERGE_BLOCK_SAMPLES, total - offset)
      sumBlock.fill(0f, 0, n)
      for (view in views) {
        val available = (view.limit() - offset).coerceIn(0, n)
        if (available == 0) continue
        view.position(offset)
        view.get(readBlock, 0, available)
        for (i in 0 until available) sumBlock[i] += readBlock[i]
      }
      writeBlock.clear()
      writeBlock.asFloatBuffer().put(sumBlock, 0, n)
      writeBlock.limit(n * Float.SIZE_BYTES)
      while (writeBlock.hasRemaining()) channel.write(writeBlock)
      offset += n
    }
  }

  /** Pads or truncates every stem to [totalFrames] and closes the files. */
  fun finish(totalFrames: Long): List<Pair<Key, File>> {
    val target = totalFrames * bytesPerFrame
    channels.values.forEach { channel ->
      if (channel.size() > target) channel.truncate(target) else padTo(channel, target)
    }
    close()
    return files.entries.map { it.key to it.value }
  }

  private fun padTo(channel: FileChannel, size: Long) {
    var remaining = size - channel.size()
    while (remaining > 0) {
      zeros.clear()
      zeros.limit(minOf(remaining, zeros.capacity().toLong()).toInt())
      remaining -= channel.write(zeros)
    }
  }

  override fun close() {
    channels.values.forEach { runCatching { it.close() } }
  }

  private companion object {
    const val MERGE_BLOCK_SAMPLES = 65_536
  }
}

/**
 * The beta SDK decides support on the client by checking [Build.MODEL] against a built-in list of
 * devices (Pixel 6–11, recent Galaxy S, ...), so newer or unlisted models report "unsupported"
 * without ever contacting Google Play services.
 */
fun unsupportedDeviceMessage(): String =
  "Studio Sound doesn't support this device (${Build.MODEL}) yet. The current SDK only enables it on " +
    "specific models such as Pixel 6–11 and recent Galaxy S phones."

fun statusName(statusCode: Int): String = StudioSoundStatusCodes.getStatusCodeString(statusCode)

fun statusHint(statusCode: Int): String? = when (statusCode) {
  StudioSoundStatusCodes.DEVICE_NOT_SUPPORTED -> "This device doesn't support Studio Sound."
  StudioSoundStatusCodes.MAX_SESSIONS_REACHED ->
    "Studio Sound is busy (too many active sessions). Close other apps using it and try again."
  StudioSoundStatusCodes.GLOBAL_INIT_FAILED -> "The Studio Sound service failed to start."
  StudioSoundStatusCodes.SESSION_INIT_FAILED -> "Couldn't start a Studio Sound session."
  StudioSoundStatusCodes.SERVICE_RELEASED -> "The Studio Sound service was shut down."
  StudioSoundStatusCodes.BINDER_DIED -> "Lost the connection to the Studio Sound service."
  StudioSoundStatusCodes.INTERNAL_ERROR -> "Studio Sound hit an internal error while processing."
  StudioSoundStatusCodes.INVALID_OPTIONS -> "This audio format isn't supported by Studio Sound."
  StudioSoundStatusCodes.CANCELED -> "Processing was cancelled."
  CommonStatusCodes.API_NOT_CONNECTED ->
    "Studio Sound isn't available in Google Play services on this device. Update Google Play services and try again."
  else -> null
}

fun describeStudioSoundError(t: Throwable): String = when (t) {
  is ApiException -> {
    val hint = statusHint(t.statusCode) ?: t.status.statusMessage ?: "The Studio Sound request failed."
    "$hint [${statusName(t.statusCode)}]"
  }
  else -> t.message ?: t.javaClass.simpleName
}
