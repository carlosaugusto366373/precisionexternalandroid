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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

class OverlayService : LifecycleService() {
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (composeView == null) {
            criarNotificacao()
            mostrarOverlay()
        }
        return START_STICKY
    }

    private fun mostrarOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val params = WindowManager.LayoutParams(
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
            setContent {
                var aimAssist by remember { mutableStateOf(false) }
                var mostrarFps by remember { mutableStateOf(true) }
                var fpsAtual by remember { mutableIntStateOf(60) }

                LaunchedEffect(mostrarFps) {
                    while(mostrarFps) {
                        fpsAtual = (58..62).random()
                        delay(1000)
                    }
                }

                // Design do Overlay Flutuante
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
                            Box(
                                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFFF0044)).clickable {
                                    stopSelf() // Fecha o overlay
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("X", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (mostrarFps) {
                            Text("FPS: $fpsAtual", color = Color(0xFF30D158), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Toggle Aim Assist
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

                        // Toggle Mostrar FPS
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

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("QZZ Panel Ativo")
            .setContentText("O overlay está rodando.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        composeView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}