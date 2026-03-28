package com.rokid.cxrssdksamples.activities.main

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.rokid.cxrssdksamples.theme.CXRSSDKSamplesTheme

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 设置屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContent {
            CXRSSDKSamplesTheme(darkTheme = true) {
                MainScreen(toConnect = {
                    viewModel.toUsage(this@MainActivity, UsageType.CONNECT)
                }, toKeys = {
                    viewModel.toUsage(this@MainActivity, UsageType.KEYS)
                })
            }
        }
    }

    var lastCode = -1
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: $keyCode")
        // keycode == 22 然后到20 被认为是一个前滑事件
        if (lastCode == 22) {
            if (keyCode == 20) {//前滑
                Log.i(TAG, "onKeyDown: front")
                viewModel.toUsage(this@MainActivity, UsageType.CONNECT)
                return true
            }
        }
        if (lastCode == 21) {
            if (keyCode == 19) {//后滑
                Log.i(TAG, "onKeyDown: back")
                viewModel.toUsage(this@MainActivity, UsageType.KEYS)
                return true
            }
        }

        lastCode = keyCode
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun MainScreen(toConnect: () -> Unit, toKeys: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MyButton(text = "Connect", onClick = toConnect)
        MyButton(text = "Keys", onClick = toKeys)
    }
}

@Composable
fun MyButton(text: String, onClick: () -> Unit, enableStatus: Boolean = true) {

    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        onClick = onClick,
        enabled = enableStatus,
        shape = RoundedCornerShape(5.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.Green,
            disabledContainerColor = Color.Black,
            disabledContentColor = Color.Green.copy(alpha = 0.23f)
        ),
        border = BorderStroke(1.dp, if (enableStatus) Color.Green else Color.Green.copy(0.23f))
    ) {
        Text(text = text)
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    CXRSSDKSamplesTheme {
        MainScreen(toConnect = {}, toKeys = {})
    }
}