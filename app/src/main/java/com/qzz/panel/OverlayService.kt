package com.qzz.panel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border // <-- IMPORT FALTANDO
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    private var isMinimized by mutableStateOf(false)
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (composeView == null) {
            criarNotificacao()
            mostrarOverlay()
        }
        return START_STICKY
    }

    private fun atualizarLayout() {
        if (isMinimized) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        composeView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun mostrarOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 200

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                if (isMinimized) {
                    // BOLINHA MINIMIZADA E ARRASTÁVEL
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0xEE1A0808))
                            .border(2.dp, Color(0xFFFF0044), CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    params.x += dragAmount.x.toInt()
                                    params.y += dragAmount.y.toInt()
                                    windowManager.updateViewLayout(this@apply, params)
                                }
                            }
                            .clickable { 
                                isMinimized = false 
                                atualizarLayout()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    // PAINEL ABERTO E ARRASTÁVEL
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        params.x += dragAmount.x.toInt()
                                        params.y += dragAmount.y.toInt()
                                        windowManager.updateViewLayout(this@apply, params)
                                    }
                                )
                            }
                    ) {
                        PainelOverlay(
                            onMinimize = { 
                                isMinimized = true 
                                atualizarLayout()
                            },
                            onClose = { stopSelf() }
                        )
                    }
                }
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun criarNotificacao() {
        val channelId = "overlay_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Overlay QZZ", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("QZZ Panel Ativo")
            .setContentText("O overlay está rodando.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        composeView?.let { windowManager.removeView(it) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun PainelOverlay(onMinimize: () -> Unit, onClose: () -> Unit) {
    var aimAssist by remember { mutableStateOf(false) }
    var mostrarFps by remember { mutableStateOf(true) }
    var fpsAtual by remember { mutableIntStateOf(60) }

    LaunchedEffect(mostrarFps) {
        while(mostrarFps) {
            fpsAtual = (58..62).random()
            delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .width(200.dp)
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE1A0808)),
        border = BorderStroke(1.dp, Color(0xFFFF0044)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("QZZ Overlay", color = Color(0xFFFF0044), fontWeight = FontWeight.Black, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF444444)).clickable { onMinimize() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("_", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFFF0044)).clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("X", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            if (mostrarFps) {
                Text("FPS: $fpsAtual", color = Color(0xFF30D158), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aim Assist", color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = aimAssist,
                    onCheckedChange = { aimAssist = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF0044))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mostrar FPS", color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = mostrarFps,
                    onCheckedChange = { mostrarFps = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF0044))
                )
            }
        }
    }
}