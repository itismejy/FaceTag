package com.rokid.cxrssdksamples.activities.videoRecord

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.rokid.cxrssdksamples.theme.CXRSSDKSamplesTheme

class VideoRecordActivity : ComponentActivity() {
    private val viewModel: VideoRecordViewModel by viewModels()

    // 管理外部存储权限请求
    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 检查是否获得了权限
        if (Environment.isExternalStorageManager()) {
            // 已获得权限
            viewModel.permissionGranted(true)
            viewModel.initializeCamera(this, this)
        } else {
            // 用户未授予权限
            viewModel.permissionGranted(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 设置屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            CXRSSDKSamplesTheme {
                VideoRecordScreen(viewModel = viewModel)
            }
        }

        viewModel.permissionNeed = object : PermissionNeed {
            override fun needPermission() {
                requestManageExternalStoragePermission()
            }
        }
        
        viewModel.contextProvider = object : ContextProvider {
            override fun getContext(): Context? {
                return this@VideoRecordActivity
            }
        }

        checkStoragePermission()
        viewModel.registerReceiver(this)
        
        // 初始化CameraX（需要权限授予后）
        if (Environment.isExternalStorageManager()) {
            viewModel.initializeCamera(this, this)
        }
    }

    override fun onDestroy() {
        viewModel.unregisterReceiver(this)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    private fun checkStoragePermission() {
        // Android 11及以上版本
        if (Environment.isExternalStorageManager()) {
            // 已有权限
            viewModel.permissionGranted(true)
            viewModel.initializeCamera(this, this)
        } else {
            // 请求MANAGE_EXTERNAL_STORAGE权限
            viewModel.permissionGranted(false)
            requestManageExternalStoragePermission()
        }
    }

    private fun requestManageExternalStoragePermission() {
        Log.e("VideoRecordActivity", "requestManageExternalStoragePermission: ")
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = "package:$packageName".toUri()
        manageExternalStorageLauncher.launch(intent)
    }
}

@Composable
fun VideoRecordScreen(viewModel: VideoRecordViewModel) {
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val selectedResolution by viewModel.selectedResolution.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (permissionGranted) {
            if (isRecording) {
                Text(
                    text = "录制中，点击镜腿上功能键停止录制",
                    color = Color.Green
                )
            } else {
                Text(
                    text = "选择分辨率后，点击镜腿上的功能键开始录制",
                    color = Color.Green
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 分辨率选择
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "当前选择: ${selectedResolution?.label ?: "未选择"}",
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.availableResolutions.forEach { resolution ->
                            Button(
                                onClick = { viewModel.setResolution(resolution) },
                                enabled = !isRecording
                            ) {
                                Text(text = resolution.label)
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = "单击功能键获取权限",
                color = Color.Green
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VideoRecordScreenPreview() {
    CXRSSDKSamplesTheme {
        VideoRecordScreen(viewModel = VideoRecordViewModel())
    }
}
