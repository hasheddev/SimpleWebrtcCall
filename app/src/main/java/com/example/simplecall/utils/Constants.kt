package com.example.simplecall.utils

object Constants {
    const val TIME_OUT_DURATION_MS = 20000L
     fun getWebSocketUrl(username:String) = "ws://10.0.2.2:8080/?username=$username"
}
