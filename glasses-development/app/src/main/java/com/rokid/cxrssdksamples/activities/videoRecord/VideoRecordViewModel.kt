package com.rokid.cxrssdksamples.activities.videoRecord

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rokid.cxrssdksamples.activities.keys.KeyReceiver
import com.rokid.cxrssdksamples.activities.keys.KeyReceiverListener
import com.rokid.cxrssdksamples.activities.keys.KeyType
import com.rokid.cxrssdksamples.default.CONSTANT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

interface PermissionNeed {
    fun needPermission()
}

interface ContextProvider {
    fun getContext(): Context?
}

data class VideoResolution(val width: Int, val height: Int, val label: String)

@SuppressLint("MissingPermission")
class VideoRecordViewModel : ViewModel() {
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted = _permissionGranted.asStateFlow()
    
    private val _selectedResolution = MutableStateFlow<VideoResolution?>(null)
    val selectedResolution = _selectedResolution.asStateFlow()
    
    var permissionNeed: PermissionNeed? = null
    var contextProvider: ContextProvider? = null
    
    // 可用的分辨率选项
    val availableResolutions = listOf(
        VideoResolution(1280, 720, "720p"),
        VideoResolution(1920, 1080, "1080p"),
        VideoResolution(3840, 2160, "4K")
    )
    
    private var keyReceiver: KeyReceiver = KeyReceiver().apply {
        listener = object : KeyReceiverListener {
            override fun onReceive(keyType: KeyType) {
                when (keyType) {
                    KeyType.CLICK -> {
                        Log.e("VideoRecordActivity", "点击")
                        if (!permissionGranted.value) {
                            permissionNeed?.needPermission()
                        } else {
                            if (_isRecording.value) {
                                _isRecording.value = false
                                stopRecording()
                            } else {
                                if (_selectedResolution.value == null) {
                                    // 如果没有选择分辨率，默认使用第一个
                                    _selectedResolution.value = availableResolutions.first()
                                }
                                val context = contextProvider?.getContext()
                                if (context != null) {
                                    _isRecording.value = true
                                    startRecording(context)
                                } else {
                                    Log.e(TAG, "Context not available for recording")
                                }
                            }
                        }
                    }
                    KeyType.BUTTON_DOWN -> {
                        Log.e("VideoRecordActivity", "按下")
                    }
                    KeyType.BUTTON_UP -> {
                        Log.e("VideoRecordActivity", "抬起")
                    }
                    KeyType.DOUBLE_CLICK -> {
                        Log.e("VideoRecordActivity", "双击")
                    }
                    KeyType.AI_START -> {
                        Log.e("VideoRecordActivity", "AI开始")
                    }
                    KeyType.LONG_PRESS -> {
                        Log.e("VideoRecordActivity", "长按")
                    }
                    else -> {
                        Log.e("VideoRecordActivity", "其他按键")
                    }
                }
            }
        }
    }
    
    // CameraX相关
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    
    // AudioRecord相关
    private var audioRecorder: AudioRecord? = null
    private var audioRecordingThread: Thread? = null
    private var isAudioRecordingActive = false
    
    // 录制文件路径
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null

    // 单调时钟时间戳（用于后期音画同步）
    private var videoStartNs: Long = 0L
    private var audioStartNs: Long = 0L

    // 本次录制的时间戳前缀（yyyyMMdd_HHmmss）
    private var recordTimeStamp: String? = null
    
    companion object {
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = CONSTANT.AUDIO_CHANNEL
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16-bit
        private const val BUFFER_SIZE = 1024
        private const val CHANNEL_COUNT = 8   // 当前音频为8声道
        private const val TAG = "VideoRecordViewModel"
    }
    
    private val cameraExecutor: Executor = Executors.newSingleThreadExecutor()
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerReceiver(activity: Activity) {
        activity.registerReceiver(keyReceiver, IntentFilter().apply {
            addAction(KeyType.CLICK.action)
            addAction(KeyType.BUTTON_DOWN.action)
            addAction(KeyType.BUTTON_UP.action)
            addAction(KeyType.DOUBLE_CLICK.action)
            addAction(KeyType.AI_START.action)
            addAction(KeyType.LONG_PRESS.action)
            priority = 100
        })
    }
    
    fun unregisterReceiver(activity: Activity) {
        try {
            activity.unregisterReceiver(keyReceiver)
        } catch (e: IllegalArgumentException) {
            // 已经注销或未注册，忽略
            Log.w(TAG, "Receiver already unregistered or not registered", e)
        }
    }
    
    fun permissionGranted(granted: Boolean) {
        _permissionGranted.value = granted
    }
    
    fun setResolution(resolution: VideoResolution) {
        _selectedResolution.value = resolution
    }
    
    fun initializeCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProvider = cameraProviderFuture.get()
                
                // 创建Recorder用于视频录制
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                
                // 创建VideoCapture用例（不需要Preview）
                videoCapture = VideoCapture.Builder(recorder)
                    .build()
                
                // 绑定到生命周期
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    videoCapture
                )
                
                Log.d(TAG, "Camera initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing camera", e)
            }
        }
    }
    
    fun startRecording(context: Context) {
        if (_selectedResolution.value == null) {
            Log.e(TAG, "Resolution not selected")
            return
        }

        val resolution = _selectedResolution.value!!

        // 创建统一的时间戳前缀
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        recordTimeStamp = timeStamp

        // 创建文件
        val videoDir = File("/sdcard/Video/")
        if (!videoDir.exists()) {
            videoDir.mkdirs()
        }
        
        val audioDir = File("/sdcard/Audio/")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        
        currentVideoFile = File(videoDir, "${timeStamp}.mp4")
        currentAudioFile = File(audioDir, "${timeStamp}.pcm")
        
        Log.d(TAG, "Starting recording - Video: ${currentVideoFile?.absolutePath}, Audio: ${currentAudioFile?.absolutePath}")
        
        // 启动视频录制
        startVideoRecording(context, currentVideoFile!!, resolution)
        
        // 启动音频录制
        startAudioRecording(currentAudioFile!!)
    }
    
    private fun startVideoRecording(context: Context, file: File, resolution: VideoResolution) {
        val videoCapture = this.videoCapture ?: return
        
        val outputOptions = FileOutputOptions.Builder(file)
            .build()
        
        // 禁用VideoCapture的音频录制，因为我们使用AudioRecord单独录制
        recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Video recording started")
                        // 记录视频起始时间（单调时钟）
                        videoStartNs = SystemClock.elapsedRealtimeNanos()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()) {
                            Log.e(TAG, "Video recording error: ${event.cause}")
                        } else {
                            Log.d(TAG, "Video recording finished: ${file.absolutePath}")
                            // 现在不在 Android 端做合成，这里只记录完成日志即可
                        }
                    }
                    else -> {}
                }
            }
    }
    
    private fun startAudioRecording(file: File) {
        if (audioRecorder == null) {
            audioRecorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .build()
        }

        // 记录音频起始时间（单调时钟）
        audioStartNs = SystemClock.elapsedRealtimeNanos()

        audioRecorder?.startRecording()
        isAudioRecordingActive = true
        
        audioRecordingThread = Thread {
            writeAudioDataToFile(file)
        }
        audioRecordingThread?.start()
    }
    
    private fun writeAudioDataToFile(file: File) {
        Log.d(TAG, "Saving audio to: ${file.absolutePath}")
        
        try {
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (isAudioRecordingActive) {
                    val read = audioRecorder?.read(buffer, 0, BUFFER_SIZE) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
            Log.d(TAG, "Audio recording finished: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio data to file", e)
        }
    }
    
    private fun stopRecording() {
        // 已经不在录制，直接返回，避免重复操作
        if (!_isRecording.value && !isAudioRecordingActive && recording == null) {
            return
        }

        // 停止视频录制
        recording?.stop()
        recording = null
        
        // 停止音频录制
        isAudioRecordingActive = false
        audioRecordingThread?.join()
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null

        _isRecording.value = false

        // 停止后写入元数据文件（用于PC端合成）
        saveMetaFile()
    }

    /**
     * 保存本次录制的元数据到 JSON 文件，用于 PC 端做精确音画同步。
     */
    private fun saveMetaFile() {
        val timeStamp = recordTimeStamp ?: return
        val videoPath = currentVideoFile?.absolutePath ?: return
        val audioPath = currentAudioFile?.absolutePath ?: return

        try {
            val metaDir = File("/sdcard/Meta/")
            if (!metaDir.exists()) {
                metaDir.mkdirs()
            }

            val metaFile = File(metaDir, "${timeStamp}_meta.json")
            val json = """
                {
                  "sampleRate": $SAMPLE_RATE,
                  "channelCount": $CHANNEL_COUNT,
                  "videoStartNs": $videoStartNs,
                  "audioStartNs": $audioStartNs,
                  "videoFile": "$videoPath",
                  "audioFile": "$audioPath"
                }
            """.trimIndent()

            metaFile.writeText(json)
            Log.d(TAG, "Meta file saved: ${metaFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving meta file", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 仅在录制仍在进行时尝试停止，避免生命周期结束时出现异常
        if (_isRecording.value || isAudioRecordingActive || recording != null) {
            try {
                stopRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording in onCleared", e)
            }
        }
        cameraProvider?.unbindAll()
    }
}
