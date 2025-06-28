package com.example.simplecall.webrtc

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class MySdpObserver: SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {

    }

    override fun onSetSuccess() {}

    override fun onCreateFailure(p0: String?) {}

    override fun onSetFailure(p0: String?) {}
}