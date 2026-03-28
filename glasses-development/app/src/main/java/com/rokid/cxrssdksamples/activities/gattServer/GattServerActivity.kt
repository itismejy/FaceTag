package com.rokid.cxrssdksamples.activities.gattServer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokid.cxrssdksamples.theme.CXRSSDKSamplesTheme

private const val TAG = "GattServerActivity"

/**
 * GATT Server 示例页面。
 * 提供一键打开/关闭蓝牙 GATT Server 与 BLE 广播：点击「Start Gatt Server」前会请求
 * BLUETOOTH_CONNECT、BLUETOOTH_ADVERTISE 权限，授权后启动服务与广播（便于扫描端发现）；
 * 点击「End Gatt Server」关闭广播与服务。状态由 [GattServerViewModel.gattStatus] 驱动 UI。
 */
class GattServerActivity : ComponentActivity() {

    private val viewModel: GattServerViewModel by viewModels()

    /** 启动 GATT Server 所需的权限（Android 12+ 需 BLUETOOTH_ADVERTISE 才能被扫描发现）。 */
    private val gattServerPermissions: Array<String> by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    /**
     * 用于请求蓝牙相关运行时权限的 Launcher。
     * 仅在全部授权时执行 [viewModel.startGattServer]。
     */
    @SuppressLint("MissingPermission")
    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        Log.d(TAG, "requestBluetoothPermissions: result allGranted=$allGranted, $results")
        if (allGranted) {
            viewModel.startGattServer(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d(TAG, "onCreate")
        setContent {
            CXRSSDKSamplesTheme {
                GattServerScreen(
                    viewModel = viewModel,
                    onClick = { bool ->
                        if (bool) {
                            val missing = gattServerPermissions.filter {
                                ContextCompat.checkSelfPermission(this@GattServerActivity, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isEmpty()) {
                                Log.d(TAG, "onClick Start: permissions ok, startGattServer")
                                viewModel.startGattServer(applicationContext)
                            } else {
                                Log.d(TAG, "onClick Start: request permissions $missing")
                                requestBluetoothPermissions.launch(gattServerPermissions)
                            }
                        } else {
                            Log.d(TAG, "onClick End: stopGattServer")
                            viewModel.stopGattServer(applicationContext)
                        }
                    }
                )
            }
        }
    }
}

/**
 * GATT Server 主界面。
 * 根据 [viewModel] 的 [GattServerViewModel.gattStatus] 显示「Start Gatt Server」或「End Gatt Server」，
 * 点击时通过 [onClick] 回传目标状态：true 表示要启动，false 表示要关闭。
 *
 * @param viewModel 负责 GATT 开关逻辑的 ViewModel
 * @param onClick 点击回调，参数为当前要执行的操作：true = 启动，false = 关闭；Preview 时可为空
 */
@Composable
fun GattServerScreen(viewModel: GattServerViewModel, onClick : (Boolean) -> Unit = {}) {
    val keyStatus by viewModel.gattStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                // 传递与当前状态相反的目标：当前关闭则传 true 表示启动，当前打开则传 false 表示关闭
                onClick(!keyStatus)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonColors(
                containerColor = Color.Black,
                contentColor = Color.Green,
                disabledContainerColor = Color.LightGray,
                disabledContentColor = Color.DarkGray
            ),
            border = BorderStroke(1.dp, Color.Green)
        ) {
            if (!keyStatus) {
                Text(text = "Start Gatt Server", color = Color.Green)
            } else {
                Text(text = "End Gatt Server", color = Color.Green)
            }
        }
    }
}

/**
 * 仅用于布局预览，使用默认空 [onClick]，不执行真实 GATT 逻辑。
 */
@Preview(showBackground = true)
@Composable
fun GattServerScreenPreview() {
    CXRSSDKSamplesTheme {
        GattServerScreen(viewModel() { GattServerViewModel() })
    }
}