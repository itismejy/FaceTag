package com.rokid.cxrssdksamples.activities.videoRecord

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.util.Log
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
class VideoRecordMediaTypeViewModel : ViewModel() {
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    
    private val _isPreparing = MutableStateFlow(false)
    val isPreparing = _isPreparing.asStateFlow()
    
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
                        Log.e("VideoRecordMediaTypeActivity", "点击")
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
                        Log.e("VideoRecordMediaTypeActivity", "按下")
                    }
                    KeyType.BUTTON_UP -> {
                        Log.e("VideoRecordMediaTypeActivity", "抬起")
                    }
                    KeyType.DOUBLE_CLICK -> {
                        Log.e("VideoRecordMediaTypeActivity", "双击")
                    }
                    KeyType.AI_START -> {
                        Log.e("VideoRecordMediaTypeActivity", "AI开始")
                    }
                    KeyType.LONG_PRESS -> {
                        Log.e("VideoRecordMediaTypeActivity", "长按")
                    }
                    else -> {
                        Log.e("VideoRecordMediaTypeActivity", "未知按键")
                    }
                }
            }
        }
    }
    
    // MediaRecorder相关
    private var mediaRecorder: MediaRecorder? = null
    
    // AudioRecord相关
    private var audioRecorder: AudioRecord? = null
    private var audioRecordingThread: Thread? = null
    private var isAudioRecordingActive = false
    
    // 录制文件路径
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null
    private var recordingStartTime: Long = 0
    
    companion object {
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = CONSTANT.AUDIO_CHANNEL
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16-bit
        private const val BUFFER_SIZE = 1024
        private const val TAG = "VideoRecordMediaTypeViewModel"
    }
    
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
        keyReceiver.let {
            activity.unregisterReceiver(it)
        }
    }
    
    fun permissionGranted(granted: Boolean) {
        _permissionGranted.value = granted
    }
    
    fun setResolution(resolution: VideoResolution) {
        _selectedResolution.value = resolution
    }
    
    fun startRecording(context: Context) {
        if (_selectedResolution.value == null) {
            Log.e(TAG, "Resolution not selected")
            return
        }
        
        val resolution = _selectedResolution.value!!
        recordingStartTime = System.nanoTime()
        
        // 创建文件
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        
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
        
        // 启动视频录制（MediaRecorder）
        startVideoRecording(context, currentVideoFile!!, resolution)
        
        // 启动音频录制（AudioRecord）
        startAudioRecording(currentAudioFile!!)
    }
    
    private fun startVideoRecording(context: Context, file: File, resolution: VideoResolution) {
        try {
            // 初始化MediaRecorder
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                // 不设置音频源，只录制视频
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(resolution.width, resolution.height)
                setVideoFrameRate(30)
                
                // 根据分辨率设置比特率
                val bitRate = when (resolution.label) {
                    "720p" -> 5000000 // 5Mbps
                    "1080p" -> 10000000 // 10Mbps
                    "4K" -> 20000000 // 20Mbps
                    else -> 5000000
                }
                setVideoEncodingBitRate(bitRate)
                
                setOutputFile(file.absolutePath)
                
                prepare()
                start()
            }
            
            Log.d(TAG, "Video recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting video recording", e)
            releaseMediaRecorder()
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
            
            // 音频录制完成后，检查视频是否也完成了，然后合并
            if (mediaRecorder == null || !_isRecording.value) {
                mergeAudioVideo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio data to file", e)
        }
    }
    
    private fun stopRecording() {
        // 停止视频录制
        try {
            mediaRecorder?.apply {
                stop()
                reset()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        }
        releaseMediaRecorder()
        
        // 停止音频录制
        isAudioRecordingActive = false
        audioRecordingThread?.join()
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
        
        // 合并音视频
        mergeAudioVideo()
    }
    
    private fun releaseMediaRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }
    
    private fun mergeAudioVideo() {
        val videoFile = currentVideoFile ?: return
        val audioFile = currentAudioFile ?: return
        
        if (!videoFile.exists() || !audioFile.exists()) {
            Log.e(TAG, "Video or audio file does not exist")
            return
        }
        
        viewModelScope.launch {
            try {
                val mergedFile = File(
                    videoFile.parent,
                    "${videoFile.nameWithoutExtension}_merged.mp4"
                )
                
                Log.d(TAG, "Starting merge: ${mergedFile.absolutePath}")
                
                // 使用MediaMuxer合并音视频
                val muxer = MediaMuxer(mergedFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                
                // 提取视频轨道
                val videoExtractor = MediaExtractor()
                videoExtractor.setDataSource(videoFile.absolutePath)
                val videoTrackIndex = selectTrack(videoExtractor, true)
                if (videoTrackIndex < 0) {
                    Log.e(TAG, "No video track found")
                    videoExtractor.release()
                    muxer.release()
                    return@launch
                }
                videoExtractor.selectTrack(videoTrackIndex)
                val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
                
                // 编码PCM音频为AAC（8声道，16K采样率）
                val audioFormat = encodePcmToAac(audioFile, mergedFile.parent ?: "/sdcard/Video/")
                
                if (audioFormat == null) {
                    Log.e(TAG, "Failed to encode audio")
                    videoExtractor.release()
                    muxer.release()
                    return@launch
                }
                
                // 添加轨道到Muxer
                val muxerVideoTrackIndex = muxer.addTrack(videoFormat)
                val muxerAudioTrackIndex = muxer.addTrack(audioFormat)
                
                muxer.start()
                
                // 写入视频数据
                val videoBuffer = ByteBuffer.allocate(1024 * 1024)
                val videoBufferInfo = MediaCodec.BufferInfo()
                var videoInputEOS = false
                
                // 获取视频的第一帧时间戳作为基准
                var videoStartTimeUs: Long = -1
                
                while (!videoInputEOS) {
                    videoBuffer.clear()
                    val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                    if (sampleSize < 0) {
                        videoInputEOS = true
                        videoBufferInfo.size = 0
                    } else {
                        if (videoStartTimeUs < 0) {
                            videoStartTimeUs = videoExtractor.sampleTime
                        }
                        
                        videoBufferInfo.offset = 0
                        videoBufferInfo.size = sampleSize
                        // 转换 MediaExtractor flags 到 MediaCodec flags
                        var flags = 0
                        if ((videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        videoBufferInfo.flags = flags
                        videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        
                        videoBuffer.position(0)
                        videoBuffer.limit(sampleSize)
                        muxer.writeSampleData(muxerVideoTrackIndex, videoBuffer, videoBufferInfo)
                        videoExtractor.advance()
                    }
                }
                
                // 写入音频数据（从编码后的AAC文件）
                val audioEncodedFile = File(mergedFile.parent, "${audioFile.nameWithoutExtension}.aac")
                if (audioEncodedFile.exists()) {
                    val audioExtractor = MediaExtractor()
                    audioExtractor.setDataSource(audioEncodedFile.absolutePath)
                    val audioTrackIndex = selectTrack(audioExtractor, false)
                    if (audioTrackIndex >= 0) {
                        audioExtractor.selectTrack(audioTrackIndex)
                        
                        val audioBuffer = ByteBuffer.allocate(1024 * 1024)
                        val audioBufferInfo = MediaCodec.BufferInfo()
                        var audioInputEOS = false
                        
                        // 使用视频的第一帧时间戳作为音频的起始时间戳，实现同步
                        var audioStartTimeUs: Long = videoStartTimeUs
                        
                        while (!audioInputEOS) {
                            audioBuffer.clear()
                            val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                            if (sampleSize < 0) {
                                audioInputEOS = true
                                audioBufferInfo.size = 0
                            } else {
                                audioBufferInfo.offset = 0
                                audioBufferInfo.size = sampleSize
                                // 转换 MediaExtractor flags 到 MediaCodec flags
                                var flags = 0
                                if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                                    flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                                }
                                audioBufferInfo.flags = flags
                                
                                // 对齐音频时间戳到视频时间戳
                                val originalTimeUs = audioExtractor.sampleTime
                                if (audioStartTimeUs < 0) {
                                    audioStartTimeUs = videoStartTimeUs
                                }
                                // 计算相对时间，然后加上视频起始时间
                                audioBufferInfo.presentationTimeUs = audioStartTimeUs + originalTimeUs
                                
                                audioBuffer.position(0)
                                audioBuffer.limit(sampleSize)
                                muxer.writeSampleData(muxerAudioTrackIndex, audioBuffer, audioBufferInfo)
                                audioExtractor.advance()
                            }
                        }
                        
                        audioExtractor.release()
                    }
                    audioEncodedFile.delete() // 删除临时AAC文件
                }
                
                videoExtractor.release()
                muxer.stop()
                muxer.release()
                
                Log.d(TAG, "Merge completed: ${mergedFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error merging audio and video", e)
            }
        }
    }
    
    private fun selectTrack(extractor: MediaExtractor, isVideo: Boolean): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (isVideo && mime.startsWith("video/")) {
                return i
            } else if (!isVideo && mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }
    
    private fun encodePcmToAac(pcmFile: File, outputDir: String): MediaFormat? {
        try {
            val aacFile = File(outputDir, "${pcmFile.nameWithoutExtension}.aac")
            
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                SAMPLE_RATE,
                8 // 8声道
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000) // 8声道需要更高的比特率
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            
            val fis = FileInputStream(pcmFile)
            val fos = FileOutputStream(aacFile)
            
            var inputEOS = false
            var outputEOS = false
            
            while (!outputEOS) {
                if (!inputEOS) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val capacity = inputBuffer.capacity()
                            val buffer = ByteArray(capacity)
                            val sampleSize = fis.read(buffer)
                            
                            if (sampleSize < 0) {
                                encoder.queueInputBuffer(
                                    inputBufferIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEOS = true
                            } else {
                                inputBuffer.clear()
                                inputBuffer.put(buffer, 0, sampleSize)
                                encoder.queueInputBuffer(
                                    inputBufferIndex, 0, sampleSize, 0, 0
                                )
                            }
                        }
                    }
                }
                
                val bufferInfo = MediaCodec.BufferInfo()
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data, 0, bufferInfo.size)
                        fos.write(data)
                    }
                    
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEOS = true
                    }
                }
            }
            
            fis.close()
            fos.close()
            encoder.stop()
            encoder.release()
            
            // 读取AAC文件并返回格式
            val extractor = MediaExtractor()
            extractor.setDataSource(aacFile.absolutePath)
            val trackIndex = selectTrack(extractor, false)
            if (trackIndex < 0) {
                extractor.release()
                return null
            }
            val format = extractor.getTrackFormat(trackIndex)
            extractor.release()
            
            return format
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding PCM to AAC", e)
            return null
        }
    }
    
    
    override fun onCleared() {
        super.onCleared()
        stopRecording()
    }
}
