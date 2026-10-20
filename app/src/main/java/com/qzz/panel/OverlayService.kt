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
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    private var isMinimized by mutableStateOf(false)
    private var aimAssistAtivo by mutableStateOf(false)
    private var mostrarFpsAtivo by mutableStateOf(true)
    private var fpsAtual by mutableIntStateOf(60)
    
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
            iniciarContadorFps()
        }
        return START_STICKY
    }

    private fun iniciarContadorFps() {
        Thread {
            while (true) {
                if (mostrarFpsAtivo) {
                    fpsAtual = (58..62).random()
                }
                Thread.sleep(1000)
            }
        }.start()
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
                    // Bolinha Azul
                    val infiniteTransition = rememberInfiniteTransition(label = "glow")
                    val glowScale by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFF0F1E3D), Color(0xFF020813))))
                            .border(2.dp, Color(0xFF00AAFF), CircleShape)
                            .shadow(15.dp, CircleShape, ambientColor = Color(0xFF00AAFF))
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
                    // Painel Azul
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
                            isAimAssistAtivo = aimAssistAtivo,
                            onAimAssistChange = { aimAssistAtivo = it },
                            isMostrarFpsAtivo = mostrarFpsAtivo,
                            onMostrarFpsChange = { mostrarFpsAtivo = it },
                            fps = fpsAtual,
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
fun PainelOverlay(
    isAimAssistAtivo: Boolean,
    onAimAssistChange: (Boolean) -> Unit,
    isMostrarFpsAtivo: Boolean,
    onMostrarFpsChange: (Boolean) -> Unit,
    fps: Int,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "alpha"
    )

    Card(
        modifier = Modifier
            .width(220.dp)
            .padding(8.dp)
            .shadow(15.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFF00AAFF).copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE0A1428)),
        border = BorderStroke(1.dp, Color(0xFF00AAFF).copy(alpha = borderAlpha)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("QZZ Overlay", color = Color(0xFF00AAFF), fontWeight = FontWeight.Black, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF1A3050)).clickable { onMinimize() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("_", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFFF0044)).clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("X", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            if (isMostrarFpsAtivo) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text("FPS: ", color = Color(0xFF88A0C0), fontSize = 12.sp)
                    Text("$fps", color = Color(0xFF00FF44), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aim Assist", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = isAimAssistAtivo,
                    onCheckedChange = onAimAssistChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00AAFF),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF1A3050)
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mostrar FPS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = isMostrarFpsAtivo,
                    onCheckedChange = onMostrarFpsChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00AAFF),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF1A3050)
                    )
                )
            }
        }
    }
}