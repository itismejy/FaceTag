package com.rokid.cxrssdksamples.activities.audioRecord

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.rokid.cxrssdksamples.activities.main.MyButton
import com.rokid.cxrssdksamples.theme.CXRSSDKSamplesTheme

class AudioRecordActivity : ComponentActivity() {
    private val viewModel: AudioRecordViewModel by viewModels()


    // 管理外部存储权限请求
    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 检查是否获得了权限
        if (Environment.isExternalStorageManager()) {
            // 已获得权限
            viewModel.permissionGranted(true)
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
                AudioRecordScreen(viewModel = viewModel)
            }
        }

        viewModel.permissionNeed =  object : PermissionNeed{
            override fun needPermission() {
                requestManageExternalStoragePermission()
            }

        }

        checkStoragePermission()
        viewModel.registerReceiver(this)
    }

    override fun onDestroy() {
        viewModel.unregisterReceiver(this)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        //

        return super.onKeyDown(keyCode, event)
    }

    private fun checkStoragePermission() {
        // Android 11及以上版本
        if (Environment.isExternalStorageManager()) {
            // 已有权限
            viewModel.permissionGranted(true)
        } else {
            // 请求MANAGE_EXTERNAL_STORAGE权限
            viewModel.permissionGranted(false)
            requestManageExternalStoragePermission()
        }
    }

    private fun requestManageExternalStoragePermission() {
        Log.e("AudioRecordActivity", "requestManageExternalStoragePermission: ",)
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = "package:$packageName".toUri()
        manageExternalStorageLauncher.launch(intent)
    }
}

@Composable
fun AudioRecordScreen(viewModel: AudioRecordViewModel) {
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isPreparing by viewModel.isPreparing.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (permissionGranted){
            if (isRecording){
                Text(text = "录音中，点击镜腿上功能键停止录音", color = Color.Green)
            }else{
                Text(text = "点击镜腿上的功能键 开始录音", color = Color.Green)
            }
        }else{
            Text(text = "单击功能键获取权限", color = Color.Green)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioRecordScreenPreview() {
    CXRSSDKSamplesTheme {
        AudioRecordScreen(viewModel = AudioRecordViewModel())
    }
}