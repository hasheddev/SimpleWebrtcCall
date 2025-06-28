package com.example.simplecall.remote.socket

import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SocketClient @Inject constructor(
    private val gson: Gson
) {
    private var socketClient: WebSocketClient? = null

    fun init(socketUrl: String, listener: SocketCallBack) {
        if (socketClient == null) {
            socketClient = object: WebSocketClient(URI(socketUrl)) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    listener.onRemoteSocketClientOpened()
                }

                override fun onMessage(message: String?) {
                    runCatching {
                        gson.fromJson(message.toString(), SignalMessageModel::class.java)
                    }.onSuccess {
                        listener.onRemoteSocketClientNewMessage(it)
                    }
                }

                override fun onClose(
                    code: Int,
                    reason: String?,
                    remote: Boolean
                ) {
                    listener.onRemoteSocketClientClosed()
                }

                override fun onError(ex: java.lang.Exception?) {
                    listener.onRemoteSocketClientConnectionError(ex)
                }
            }.apply {
                connect()
            }
        } else {
            listener.onRemoteSocketClientOpened()
        }
    }

    fun sendDataToHost(data: Any) {
        runCatching {
            socketClient?.send(gson.toJson(data))
        }
    }

    fun close() {
        socketClient?.let {
            it.close()
        }
    }
}

interface  SocketCallBack {
    fun onRemoteSocketClientOpened()
    fun onRemoteSocketClientClosed()
    fun onRemoteSocketClientConnectionError(e: Exception?)
    fun onRemoteSocketClientNewMessage(message: SignalMessageModel)
}