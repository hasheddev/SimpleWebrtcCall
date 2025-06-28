package com.example.simplecall.webrtc

import android.app.Application
import android.content.Context
import com.example.simplecall.utils.SimpleCallApplication
import com.google.gson.Gson
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCFactory @Inject constructor(
    private val application: Application,
    private val gson: Gson
) {

    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private val eglBaseContext = EglBase.create().eglBaseContext
    private var videoCapture: CameraVideoCapturer? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private val streamId = "${SimpleCallApplication.USER_ID}_stream"
    private var localStream: MediaStream? = null
    private val iceServer = listOf<PeerConnection.IceServer>(
        PeerConnection.IceServer.builder(
            "stun:stun.relay.metered.ca:80"
        ).createIceServer()
    )

    init {
        initPeerConnectionFactory(application)
    }
    private fun initPeerConnectionFactory(context: Context) {
        val option = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(option)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
       return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
           .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
           .setOptions(PeerConnectionFactory.Options().apply {
               disableEncryption = false
               disableNetworkMonitor = false
           }).createPeerConnectionFactory()

    }

    fun prepareStream(renderer: SurfaceViewRenderer) {
        initSurfaceView(renderer)
        startLocalVideo(renderer)
    }

    private fun startLocalVideo(renderer: SurfaceViewRenderer) {
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name,
            eglBaseContext
        )
        videoCapture = getVideoCapture()
        videoCapture?.initialize(surfaceTextureHelper, renderer.context, localVideoSource.capturerObserver)
        videoCapture?.startCapture(720, 480, 10)
        localVideoTrack = peerConnectionFactory.createVideoTrack(streamId + "video", localVideoSource)
        localVideoTrack?.addSink(renderer)
        localAudioTrack = peerConnectionFactory.createAudioTrack(streamId + "audio", localAudioSource)
        localStream = peerConnectionFactory.createLocalMediaStream(streamId)
        localStream?.addTrack(localVideoTrack)
        localStream?.addTrack(localAudioTrack)
    }

    private fun getVideoCapture(): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            }?: throw IllegalStateException()
        }
    }

    fun initSurfaceView(renderer: SurfaceViewRenderer) {
        renderer.run {
            setMirror(true)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
    }

    fun onDestroy() {
        runCatching {
            videoCapture?.stopCapture()
            videoCapture?.dispose()

            localAudioTrack?.let {
                it.setEnabled(false)
                it.dispose()
            }
            localVideoTrack?.dispose()
            localStream?.dispose()
        }
    }

    fun switchCamera() {
        videoCapture?.switchCamera(null)
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun  createRTCClient(
        observer: PeerConnection.Observer,
        listener: TransferDataToServerCallBack
    ): RTCClient? {
        val peerConnection = peerConnectionFactory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServer),
            observer
        )
        localStream?.let { peerConnection?.addStream(it) }
        return peerConnection?.let {
            RTCClientImpl(it, listener, gson )
        }
    }
}