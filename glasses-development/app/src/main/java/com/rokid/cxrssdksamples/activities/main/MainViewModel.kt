package com.rokid.cxrssdksamples.activities.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.rokid.cxrssdksamples.activities.keys.KeysActivity
import com.rokid.cxrssdksamples.activities.connect.ConnectActivity

enum class UsageType {
    KEYS,
    CONNECT,
}

class MainViewModel : ViewModel() {

    fun toUsage(context: Context, type: UsageType) {
        when (type) {
            UsageType.KEYS -> {
                context.startActivity(Intent(context, KeysActivity::class.java))
            }
            UsageType.CONNECT -> {
                context.startActivity(Intent(context, ConnectActivity::class.java))
            }
        }
    }
}