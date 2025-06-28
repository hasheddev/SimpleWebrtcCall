package com.example.simplecall.webrtc

import android.Manifest
import com.example.simplecall.remote.socket.SignalMessageModel
import com.example.simplecall.remote.socket.SignalMessageType
import com.example.simplecall.utils.SimpleCallApplication
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class RTCClientImpl(
    connection: PeerConnection,
    private val transferListener: TransferDataToServerCallBack,
    private val gson: Gson
): RTCClient {
    override val peerConnection: PeerConnection = connection

    private val mediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }

    override fun onDestroy() {
        runCatching {
            peerConnection.close()
        }
    }

    override fun offer(target: String) {
        peerConnection.createOffer(
            object : MySdpObserver() {
                override fun onCreateSuccess(description: SessionDescription?) {
                    super.onCreateSuccess(description)
                    peerConnection.setLocalDescription(MySdpObserver(), description)
                    transferListener.onTransferEventToSocket(
                        SignalMessageModel(
                            type = SignalMessageType.Offer,
                            target = target,
                            sender = SimpleCallApplication.USER_ID,
                            data = description?.description
                        )
                    )
                }
            },
            mediaConstraints
        )
    }

    override fun answer(target: String) {
        peerConnection.createAnswer(
            object : MySdpObserver() {
                override fun onCreateSuccess(description: SessionDescription?) {
                    super.onCreateSuccess(description)
                    peerConnection.setLocalDescription(MySdpObserver(), description)
                    transferListener.onTransferEventToSocket(
                        SignalMessageModel(
                            type = SignalMessageType.Answer,
                            target = target,
                            sender = SimpleCallApplication.USER_ID,
                            data = description?.description
                        )
                    )
                }
            },
            mediaConstraints
        )
    }

    override fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection.setRemoteDescription(
           MySdpObserver(), sessionDescription
        )
    }

    override fun onIceCandidateReceived(candidate: IceCandidate) {
        peerConnection.addIceCandidate(candidate)
    }

    override fun onLocalIceCandidateGenerated(
        iceCandidate: IceCandidate,
        target: String
    ) {
        peerConnection.addIceCandidate(iceCandidate)
        transferListener.onTransferEventToSocket(
            SignalMessageModel(
                type = SignalMessageType.ICE,
                sender = SimpleCallApplication.USER_ID,
                target = target,
                data = gson.toJson(iceCandidate)
            )
        )
    }
}

interface TransferDataToServerCallBack {
    fun onTransferEventToSocket(data: SignalMessageModel)
}
