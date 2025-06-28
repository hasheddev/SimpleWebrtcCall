package com.example.simplecall.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplecall.remote.socket.SignalMessageModel
import com.example.simplecall.remote.socket.SignalMessageType
import com.example.simplecall.remote.socket.SocketCallBack
import com.example.simplecall.remote.socket.SocketClient
import com.example.simplecall.utils.ConnectionState
import com.example.simplecall.utils.Constants
import com.example.simplecall.utils.SignallingClient
import com.example.simplecall.utils.SimpleCallApplication
import com.example.simplecall.webrtc.MyPeerObserver
import com.example.simplecall.webrtc.RTCAudioManager
import com.example.simplecall.webrtc.RTCClient
import com.example.simplecall.webrtc.TransferDataToServerCallBack
import com.example.simplecall.webrtc.WebRTCFactory
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val socketClient: SocketClient,
    private val signalSender: SignallingClient,
    private val  webrtcFactory: WebRTCFactory,
    private val gson: Gson,
    application: Application
): ViewModel() {
    var connectionState = MutableStateFlow<ConnectionState>(ConnectionState.New)
    val eventState: MutableSharedFlow<String> = MutableSharedFlow(replay = 0)
    private var rtcClient: RTCClient? = null

    private var target = ""

    private var jobTimeout: Job? = null
    private var remoteSurface: SurfaceViewRenderer? = null
    private val rtcAudioManager by lazy { RTCAudioManager(application) }

    init {
        rtcAudioManager.setDefaultAudioDevice(
            RTCAudioManager.AudioDevice.SPEAKER_PHONE
        )
    }

    private fun handleIncomingMessage(messageModel: SignalMessageModel) {
        when(messageModel.type) {
            SignalMessageType.UserOnline -> handleUserOnline(messageModel)
            SignalMessageType.UserOffline -> handleUserOffline(messageModel)
            SignalMessageType.StartCall -> handleStartCall(messageModel)
            SignalMessageType.AcceptCall -> handleAcceptCall(messageModel)
            SignalMessageType.RejectCall -> handleRejectCall(messageModel)
            SignalMessageType.Offer -> handleOffer(messageModel)
            SignalMessageType.Answer -> handleAnswer(messageModel)
            SignalMessageType.ICE -> handleIce(messageModel)
            SignalMessageType.EndCall -> handleEndCall()
            else -> Unit
        }
    }

    private fun  handleStartCall(message: SignalMessageModel) {
        if (connectionState.value is ConnectionState.OnCall) {
            signalSender.sendRejectCall(message.sender)
            return
        }
        setConnectionState(ConnectionState.ReceivedCall(message.sender))
    }

    private fun  handleAcceptCall(message: SignalMessageModel) {
        setConnectionState(ConnectionState.OnCall(message.sender))
        // set up rtc connection
        setUpRTCConnection(message.sender)?.offer(message.sender)
    }

    private fun  handleRejectCall(message: SignalMessageModel) {
        setConnectionState(ConnectionState.WaitingForCall)
        viewModelScope.launch { eventState.emit("Call rejected") }
        webrtcFactory.onDestroy()
    }

    private fun  handleOffer(message: SignalMessageModel) {
        this.target = message.sender
        val sessionDescription = SessionDescription(
            SessionDescription.Type.OFFER,
            message.data.toString()
        )
        setUpRTCConnection(message.sender)?.also {
            it.onRemoteSessionReceived(sessionDescription)
            it.answer(message.sender)
        }
    }

    private fun  handleAnswer(message: SignalMessageModel) {
        val sessionDescription = SessionDescription(
            SessionDescription.Type.ANSWER,
            message.data.toString()
        )
        rtcClient?.onRemoteSessionReceived(sessionDescription)
    }

    private fun  handleIce(message: SignalMessageModel) {
        runCatching {
            val iceCandidate = gson.fromJson(message.data.toString(), IceCandidate::class.java)
            rtcClient?.onIceCandidateReceived(iceCandidate)
        }
    }

    private fun  handleEndCall() {
        finishCall()
    }

    fun findUser(target: String) {
        if (target == SimpleCallApplication.USER_ID) {
            viewModelScope.launch {
                eventState.emit("You cannot Call yourself")
            }
            return
        }
        setConnectionState(ConnectionState.WaitingForCall)
        signalSender.findUser(target)
    }
    private fun handleUserOnline(message: SignalMessageModel) {
        setConnectionState(ConnectionState.CallingTarget(message.target))
        startCallWithTimeOut(message.target)
    }

    private fun startCallWithTimeOut(target: kotlin.String) {
        this.target = target
        signalSender.sendStartCall(target)
        jobTimeout?.cancel()
        jobTimeout = viewModelScope.launch {
            delay(Constants.TIME_OUT_DURATION_MS)
            if(connectionState.value is ConnectionState.CallingTarget) {
                setConnectionState(ConnectionState.WaitingForCall)
                webrtcFactory.onDestroy()
            }
        }
    }

    fun incomingCallDismissed(){
        setConnectionState(ConnectionState.WaitingForCall)
        webrtcFactory.onDestroy()
    }

    fun acceptIncomingCall(target: String) {
        setConnectionState(ConnectionState.OnCall(target))
        signalSender.sendAcceptCall(target)
    }

    fun rejectIncomingCall(target: String) {
        setConnectionState(ConnectionState.WaitingForCall)
        signalSender.sendRejectCall(target)
    }

    private fun handleUserOffline(message: SignalMessageModel) {
        setConnectionState(ConnectionState.UserOffline(message.target))
        viewModelScope.launch {
            eventState.emit("${message.target} is offline")
        }
    }

    fun onSurfaceLocalReady(renderer: SurfaceViewRenderer) {
        webrtcFactory.prepareStream(renderer)
    }

    fun onSurfaceRemoteReady(renderer: SurfaceViewRenderer) {
        this.remoteSurface = renderer
        webrtcFactory.initSurfaceView(renderer)
    }

    fun connectSocket() {
        socketClient.init(
            Constants.getWebSocketUrl(SimpleCallApplication.USER_ID),
            callBack
        )
    }
    private fun setConnectionState(state: ConnectionState) {
        connectionState.update { state }
    }

    override fun onCleared() {
        super.onCleared()
        remoteSurface?.release()
        remoteSurface = null
        webrtcFactory.onDestroy()
        socketClient.close()
    }

    private fun setUpRTCConnection(target: String): RTCClient? {
        runCatching {
            rtcClient?.onDestroy()
        }
        rtcClient = null
        rtcClient = webrtcFactory.createRTCClient(
            object : MyPeerObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    p0?.let { rtcClient?.onLocalIceCandidateGenerated(it, target) }
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    p0?.let { stream ->
                        runCatching {
                            remoteSurface?.let { remote ->
                                stream.videoTracks[0]?.addSink(remote)
                            }
                        }
                    }
                }
            },
            object: TransferDataToServerCallBack {
                override fun onTransferEventToSocket(data: SignalMessageModel) {
                    socketClient.sendDataToHost(data)
                }
            }
        )
        return rtcClient
    }

    private fun finishCall() {
        rtcClient?.onDestroy()
        rtcClient = null
        webrtcFactory.onDestroy()
        setConnectionState(ConnectionState.WaitingForCall)
    }

    fun switchCamera() {
        webrtcFactory.switchCamera()
    }

    fun endCall() {
        signalSender.sendEndCall(target)
        finishCall()
    }

    fun toggleMic(enabled: kotlin.Boolean) {
        webrtcFactory.toggleMic(enabled)
    }

    fun toggleCamera(enabled: kotlin.Boolean) {
        webrtcFactory.toggleCamera(enabled)
    }

    fun toggleSpeaker(speaker: kotlin.Boolean) {
        if (speaker) {
            rtcAudioManager.setDefaultAudioDevice(
                RTCAudioManager.AudioDevice.SPEAKER_PHONE
            )
        } else {
            rtcAudioManager.setDefaultAudioDevice(
                RTCAudioManager.AudioDevice.EARPIECE
            )
        }
    }

    private val callBack = object : SocketCallBack {
        override fun onRemoteSocketClientOpened() {
            setConnectionState(ConnectionState.WaitingForCall)
        }

        override fun onRemoteSocketClientClosed() {

        }

        override fun onRemoteSocketClientConnectionError(e: Exception?) {

        }

        override fun onRemoteSocketClientNewMessage(message: SignalMessageModel) {
            handleIncomingMessage(message)
        }
    }
}
