package com.example.simplecall.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simplecall.ui.components.WhoToCall
import com.example.simplecall.ui.viewmodels.MainViewModel
import com.example.simplecall.utils.ConnectionState
import com.example.simplecall.R
import com.example.simplecall.ui.components.CallComponent
import com.example.simplecall.ui.components.IncomingCallSnackBar
import com.example.simplecall.ui.components.YourIdCard
import com.example.simplecall.utils.SimpleCallApplication
import org.webrtc.SurfaceViewRenderer

@Composable
fun MainScreen() {
    val viewModel = hiltViewModel<MainViewModel>()
    val connectionStatus = viewModel.connectionState.collectAsState()
    val context = LocalContext.current
    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {response ->
        if(!response.all { it.value }) {
            Toast.makeText(
                context,
                "Camera and Microphone permission is required",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.connectSocket()
        }
    }

    LaunchedEffect(Unit) {
        requestPermissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA

            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.eventState.collect {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(4.dp, 22.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (connectionStatus.value !is ConnectionState.OnCall) {
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center
                    ) {

                        Image(
                            painter = painterResource(id = R.drawable.webrtc),
                            contentDescription = "YouTube Channel",
                            modifier = Modifier
                                .padding(top = 30.dp)
                                .size(84.dp)
                                .clickable {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/@codewithkael")
                                    )
                                    context.startActivity(intent)
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(
                        text = "Simple Video Call with WebRTC",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(30.dp),
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Enter your friends ID and press call !!",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp, start = 20.dp, end = 20.dp),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                    YourIdCard(userId = SimpleCallApplication.USER_ID) {
                        Toast.makeText(context, "User ID Copied to clipboard", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
            if (connectionStatus.value is ConnectionState.WaitingForCall || connectionStatus.value is ConnectionState.UserOffline) {
                WhoToCall(onCallClick = { targetId ->
                    if (targetId.isEmpty()) {
                        Toast.makeText(context, "Enter User ID to call", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.findUser(targetId)
                    }
                })
            }
        }

        if (connectionStatus.value is ConnectionState.CallingTarget ||
            connectionStatus.value is ConnectionState.OnCall){
            CallComponent(
                onSurfaceRemoteReady = {remoteRenderer ->
                    viewModel.onSurfaceRemoteReady(remoteRenderer)
                },
                onSurfaceLocalReady = {localRenderer ->
                   viewModel.onSurfaceLocalReady(localRenderer)
                },
                onSwitchCamera = {
                   viewModel.switchCamera()
                },
                onEndCall = {
                   viewModel.endCall()
                },
                onToggleMic = {enabled ->
                    viewModel.toggleMic(enabled)
                },
                onToggleCamera = {enabled->
                    viewModel.toggleCamera(enabled)
                },
                onToggleSpeaker = {isSpeaker->
                   viewModel.toggleSpeaker(isSpeaker)
                }
            )
        }

        if (connectionStatus.value is ConnectionState.ReceivedCall){
            val callerId = (connectionStatus.value as ConnectionState.ReceivedCall).sender
            IncomingCallSnackBar(
                callerId = callerId!!,
                onTimeout = {
                   viewModel.incomingCallDismissed()
                    Toast.makeText(context, "Call Timed out", Toast.LENGTH_SHORT).show()
                },
                onAccept = {target->
                    viewModel.acceptIncomingCall(target)
                },
                onReject = {target->
                   viewModel.rejectIncomingCall(target)
                }
            )
        }
    }
}
