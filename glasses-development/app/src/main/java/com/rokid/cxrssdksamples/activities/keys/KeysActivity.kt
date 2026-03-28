package com.rokid.cxrssdksamples.activities.keys

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.rokid.cxrssdksamples.theme.CXRSSDKSamplesTheme

class KeysActivity : ComponentActivity() {

    private val viewModel: KeysViewModel by viewModels()
    private var latestKeyType by mutableStateOf<KeyType?>(null)

    private val keyReceiver = KeyReceiver().apply {
        listener = object : KeyReceiverListener {
            override fun onReceive(keyType: KeyType) {
                // 更新状态以在UI中显示最新的按键类型
                latestKeyType = keyType

                when (keyType) {
                    KeyType.CLICK -> {
                        // 处理按键点击事件
                        Log.d("KeysActivity", "system event: button on left leg")
                    }
                    KeyType.BUTTON_DOWN -> {
                        // 处理按键按下事件
                        Log.d("KeysActivity", "system event: button on left leg down")
                    }
                    KeyType.BUTTON_UP -> {
                        // 处理按键抬起事件
                        Log.d("KeysActivity", "system event: button on left leg up")
                    }
                    KeyType.DOUBLE_CLICK -> {
                        // 处理按键双击事件
                        Log.d("KeysActivity", "system event: button on left leg double click")
                    }
                    KeyType.AI_START -> {
                        // 处理touchpad 长按事件
                        Log.d("KeysActivity", "system event: touchpad long pressed")
                    }
                    KeyType.LONG_PRESS -> {
                        // 处理按键长按事件
                        Log.d("KeysActivity", "system event: long pressed the button on left leg")
                    }

                    KeyType.ACTION_TWO_FINGER_SINGLE_TAP -> {
                        // 处理双指单击事件
                        Log.d("KeysActivity", "system event: two finger single tap")
                    }
                    KeyType.ACTION_TWO_FINGER_DOUBLE_TAP -> {
                        // 处理双指双击事件
                        Log.d("KeysActivity", "system event: two finger double tap")
                    }
                    KeyType.ACTION_TWO_FINGER_SWIPE_FORWARD -> {
                        // 处理双指滑动事件
                        Log.d("KeysActivity", "system event: two finger swipe forward")
                    }
                    KeyType.ACTION_TWO_FINGER_SWIPE_BACK -> {
                        // 处理双指滑动事件
                        Log.d("KeysActivity", "system event: two finger swipe back")
                    }
                    KeyType.ACTION_SETTINGS_KEY -> {
                        // 处理双指长按事件
                        Log.d("KeysActivity", "system event: two finger long pressed")
                    }
                }
            }
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 设置屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContent {
            CXRSSDKSamplesTheme {
                KeysScreen(latestKeyType = latestKeyType?.name ?: "")
            }
        }
        registerReceiver(keyReceiver, IntentFilter().apply {
            addAction(KeyType.CLICK.action)
            addAction(KeyType.BUTTON_DOWN.action)
            addAction(KeyType.BUTTON_UP.action)
            addAction(KeyType.DOUBLE_CLICK.action)
            addAction(KeyType.AI_START.action)
            addAction(KeyType.LONG_PRESS.action)
            addAction(KeyType.ACTION_TWO_FINGER_SINGLE_TAP.action)
            addAction(KeyType.ACTION_TWO_FINGER_DOUBLE_TAP.action)
            addAction(KeyType.ACTION_TWO_FINGER_SWIPE_FORWARD.action)
            addAction(KeyType.ACTION_TWO_FINGER_SWIPE_BACK.action)
            addAction(KeyType.ACTION_SETTINGS_KEY.action)
            priority = 100
        })
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("KeysActivity", "onKeyDown: $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                // 拦截返回键
                Log.d("KeysActivity", "onKeyDown: back pressed")
                return true
            }
            KeyEvent.KEYCODE_ENTER->{
                Log.d("KeysActivity", "onKeyUp: touchpad single down")
                return  true
            }
            else -> {
                Log.d("KeysActivity", "onKeyUp: $keyCode")
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("KeysActivity", "onKeyUp: $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                // 拦截返回键
                Log.d("KeysActivity", "onKeyUp: back pressed")
                return true
            }
            KeyEvent.KEYCODE_ENTER->{
                Log.d("KeysActivity", "onKeyUp: touchpad single up")
                return  true
            }
            else -> {
                Log.d("KeysActivity", "onKeyUp: $keyCode")
            }
        }
        return super.onKeyUp(keyCode, event)
    }

//    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
//    @SuppressLint("GestureBackNavigation", "MissingSuperCall")
//    override fun onBackPressed() {
//        Log.d("KeysActivity", "onBackPressed")
////        super.onBackPressed()
//    }
    override fun onDestroy() {
        unregisterReceiver(keyReceiver)
        super.onDestroy()
    }
}

@Composable
fun KeysScreen(latestKeyType: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "在该界面可以屏蔽所有的可屏蔽按键进行自定义操作", color = Color.Green)
        Text(text = "Key = $latestKeyType", color = Color.Green)

    }
}

@Preview(showBackground = true)
@Composable
fun KeysScreenPreview() {
    CXRSSDKSamplesTheme {
        KeysScreen("")
    }
}