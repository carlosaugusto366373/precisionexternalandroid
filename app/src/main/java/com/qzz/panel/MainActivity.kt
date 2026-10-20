package com.qzz.panel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// --- TEMAS ---
data class AppColors(
    val bgMain: Color,
    val bgPanel: Color,
    val bgCard: Color,
    val accent: Color,
    val accentDark: Color,
    val textMain: Color,
    val textDim: Color,
    val border: Color,
    val btnText: Color
)

val TemaVermelho = AppColors(Color(0xFF0A0000), Color(0xFF1A0808), Color(0xFF140808), Color(0xFFFF0044), Color(0xFFCC0033), Color(0xFFF0E0E0), Color(0xFFB08080), Color(0xFF3A2A2A), Color(0xFF1A0000))
val TemaRoxo = AppColors(Color(0xFF07000F), Color(0xFF170828), Color(0xFF0D0418), Color(0xFF9D4EDD), Color(0xFF5A189A), Color(0xFFEFE0F5), Color(0xFFA58BB0), Color(0xFF33254A), Color(0xFF0A0012))
val TemaPreto = AppColors(Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF111111), Color(0xFF30D158), Color(0xFF248A3D), Color(0xFFFFFFFF), Color(0xFF8E8E93), Color(0xFF333333), Color(0xFF000000))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QzzPanelApp()
        }
    }
}

@Composable
fun QzzPanelApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("qzz_prefs", Context.MODE_PRIVATE)
    
    var isLogado by remember { mutableStateOf(prefs.getString("token", null) != null) }
    var temaIndex by remember { mutableIntStateOf(prefs.getInt("tema_index", 0)) }
    val temaAtual = when(temaIndex) { 1 -> TemaRoxo; 2 -> TemaPreto; else -> TemaVermelho }

    // --- VERIFICADOR DE SESSÃO E EXPIRAÇÃO (API) ---
    LaunchedEffect(isLogado) {
        if (isLogado) {
            while (true) {
                val expiry = prefs.getLong("expiry", 0L)
                val agora = System.currentTimeMillis()
                
                // 1. Checa se expirou localmente
                if (expiry > 0 && agora >= expiry) {
                    prefs.edit().clear().apply()
                    isLogado = false
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "⚠ Sua key expirou!", Toast.LENGTH_LONG).show()
                    }
                    break
                }
                
                // 2. Checa no servidor se a key foi banida
                val key = prefs.getString("key_salva", null)
                if (key != null) {
                    val aindaValida = checarSessaoNoServidor(key, context)
                    if (!aindaValida) {
                        prefs.edit().clear().apply()
                        isLogado = false
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "⚠ Sua key foi revogada ou banida!", Toast.LENGTH_LONG).show()
                        }
                        break
                    }
                }
                
                delay(15000) // Checa a cada 15 segundos
            }
        }
    }

    AnimatedContent(targetState = isLogado, label = "tela_transicao") { logado ->
        if (!logado) {
            TelaLogin(temaAtual, onLoginSucesso = { expiry, keyDigitada ->
                prefs.edit().putString("token", "salvo").putString("key_salva", keyDigitada).putLong("expiry", expiry).apply()
                isLogado = true
            })
        } else {
            TelaPainel(
                tema = temaAtual,
                onTrocarTema = {
                    temaIndex = (temaIndex + 1) % 3
                    prefs.edit().putInt("tema_index", temaIndex).apply()
                },
                onSair = {
                    prefs.edit().clear().apply()
                    isLogado = false
                }
            )
        }
    }
}

@Composable
fun FundoAnimado(tema: AppColors) {
    val infiniteTransition = rememberInfiniteTransition(label = "fundo")
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bolha_x"
    )

    Box(modifier = Modifier.fillMaxSize().background(tema.bgMain)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tema.accent.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(size.width * 0.1f + offsetX, size.height * 0.1f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.1f + offsetX, size.height * 0.1f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tema.accentDark.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(size.width * 0.9f - offsetX, size.height * 0.8f),
                    radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.9f - offsetX, size.height * 0.8f)
            )
        }
    }
}

@Composable
fun TelaLogin(tema: AppColors, onLoginSucesso: (Long, String) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var keyInput by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FundoAnimado(tema)

        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(tema.bgPanel.copy(alpha = 0.85f))
                    .border(1.dp, tema.accent.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(90.dp).clip(CircleShape).border(3.dp, tema.accent, CircleShape),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "GUTO x QZZ", color = tema.accent, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(text = "ACESSO PREMIUM • PAYPAL", color = tema.textDim, fontSize = 10.sp, letterSpacing = 2.sp)
                
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("Digite sua key", color = tema.textDim) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = tema.bgMain,
                        unfocusedContainerColor = tema.bgMain,
                        focusedTextColor = tema.textMain,
                        unfocusedTextColor = tema.textMain,
                        focusedIndicatorColor = tema.accent,
                        unfocusedIndicatorColor = tema.border
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (keyInput.isBlank()) {
                            Toast.makeText(context, "Digite a key", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        carregando = true
                        scope.launch {
                            val resultado = validarKey(keyInput, context)
                            carregando = false
                            if (resultado.first) {
                                Toast.makeText(context, "Acesso liberado", Toast.LENGTH_SHORT).show()
                                onLoginSucesso(resultado.second, keyInput)
                            } else {
                                Toast.makeText(context, "Key inválida", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tema.accent),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !carregando
                ) {
                    if (carregando) {
                        CircularProgressIndicator(color = tema.btnText, modifier = Modifier.size(24.dp))
                    } else {
                        Text("ENTRAR", color = tema.btnText, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botão Discord
                    Button(
                        onClick = { uriHandler.openUri("https://discord.gg/MfY4EAFKnf") },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_discord),
                                contentDescription = "Discord",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discord", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Botão WhatsApp
                    Button(
                        onClick = { uriHandler.openUri("https://whatsapp.com/channel/0029Vb8FZ022Jl87vqng8E2v") },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_whatsapp),
                                contentDescription = "WhatsApp",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelaPainel(tema: AppColors, onTrocarTema: () -> Unit, onSair: () -> Unit) {
    var abaSelecionada by remember { mutableIntStateOf(0) }
    val abas = listOf("FUNÇÕES", "LAG", "ARQUIVOS", "CONTA")
    
    var toggle1 by remember { mutableStateOf(true) }
    var toggle2 by remember { mutableStateOf(false) }
    var toggle3 by remember { mutableStateOf(true) }
    
    var toggleLag1 by remember { mutableStateOf(true) }
    var toggleLag2 by remember { mutableStateOf(false) }
    
    var injetando by remember { mutableStateOf(false) }
    var arquivoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { 
        if (Settings.canDrawOverlays(context)) {
            context.startService(Intent(context, OverlayService::class.java))
        }
    }

    val seletorArquivo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            arquivoUri = uri
            Toast.makeText(context, "Arquivo .zip carregado", Toast.LENGTH_SHORT).show()
        }
    }

    val injetarEAbrir = {
        injetando = true
        scope.launch {
            kotlinx.coroutines.delay(3000)
            injetando = false
            Toast.makeText(context, "Overlay Ativado!", Toast.LENGTH_SHORT).show()
            
            if (!Settings.canDrawOverlays(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                overlayPermissionLauncher.launch(intent)
            } else {
                context.startService(Intent(context, OverlayService::class.java))
            }
            
            executarExploit(context)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FundoAnimado(tema)

        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(28.dp))
                .background(tema.bgPanel.copy(alpha = 0.85f))
                .border(1.dp, tema.accent.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(tema.bgCard)
                        .border(1.dp, tema.border, CircleShape)
                        .clickable { onTrocarTema() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(tema.accent))
                }

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("QZZ x GUTO", color = tema.accent, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Premium • PayPal", color = tema.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                abas.forEachIndexed { index, titulo ->
                    val selecionada = index == abaSelecionada
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selecionada) tema.accent else tema.bgCard)
                            .border(1.dp, if (selecionada) tema.accent else tema.border, RoundedCornerShape(12.dp))
                            .clickable { abaSelecionada = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = titulo,
                            color = if (selecionada) tema.btnText else tema.textDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = abaSelecionada,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                }, label = "abas"
            ) { targetAba ->
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                    when (targetAba) {
                        0 -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(tema.bgCard.copy(alpha = 0.5f))
                                    .border(1.dp, tema.border, RoundedCornerShape(16.dp))
                                    .clickable { 
                                        Toast.makeText(context, "Injete as funções para liberar o Overlay do Aim Assist!", Toast.LENGTH_LONG).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Aim Assist (Overlay)", color = tema.textDim, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Requer injeção para desbloquear", color = tema.border, fontSize = 11.sp)
                                }
                                Text("🔒", fontSize = 18.sp)
                            }

                            CardFuncao("Remover tremedeira", "Estabilização de mira", toggle1, { toggle1 = it }, tema)
                            CardFuncao("Reduzir recoil", "Controle de recuo", toggle2, { toggle2 = it }, tema)
                            CardFuncao("Modo desempenho", "Otimização de performance", toggle3, { toggle3 = it }, tema)
                        }
                        1 -> {
                            CardFuncao("Reduzir lag", "Otimização de rede", toggleLag1, { toggleLag1 = it }, tema)
                            CardFuncao("Travar em 60 FPS", "Estabilidade de quadros", toggleLag2, { toggleLag2 = it }, tema)
                        }
                        2 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(tema.accent.copy(alpha = 0.05f))
                                    .border(2.dp, if (arquivoUri != null) tema.accent else tema.border, RoundedCornerShape(16.dp))
                                    .clickable { seletorArquivo.launch("application/zip") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📦", fontSize = 40.sp)
                                    Text(
                                        text = if (arquivoUri != null) "Arquivo carregado" else "Selecionar arquivo .zip",
                                        color = tema.textMain,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { Toast.makeText(context, "Arquivo Injetado", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = tema.accent),
                                enabled = arquivoUri != null,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("INJETAR ARQUIVO", color = tema.btnText, fontWeight = FontWeight.Bold)
                            }
                        }
                        3 -> {
                            val expiry = LocalContext.current.getSharedPreferences("qzz_prefs", Context.MODE_PRIVATE).getLong("expiry", 0L)
                            val dataStr = if (expiry > 0) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(expiry)) else "Carregando..."
                            
                            CardFuncao("Expira em", dataStr, false, {}, tema, isEstatico = true)
                            CardFuncao("Plataforma", "Android Nativo", false, {}, tema, isEstatico = true)
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Sair da conta",
                                color = tema.textDim,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().clickable { onSair() }.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { injetarEAbrir() },
                    modifier = Modifier.weight(1f).fillMaxHeight().shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = tema.accentDark),
                    colors = ButtonDefaults.buttonColors(containerColor = tema.accentDark),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !injetando
                ) {
                    Text("INJETAR FF\nNORMAL", color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 16.sp)
                }

                Button(
                    onClick = { injetarEAbrir() },
                    modifier = Modifier.weight(1f).fillMaxHeight().shadow(15.dp, RoundedCornerShape(20.dp), ambientColor = tema.accent),
                    colors = ButtonDefaults.buttonColors(containerColor = tema.accent),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !injetando
                ) {
                    Text("INJETAR FF\nMAX", color = tema.btnText, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 16.sp)
                }
            }
            
            Text("Android • Overlay", color = tema.textDim, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, letterSpacing = 1.sp)
        }

        if (injetando) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = tema.accent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Injetando...", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun CardFuncao(titulo: String, desc: String, marcado: Boolean, onMudar: (Boolean) -> Unit, tema: AppColors, isEstatico: Boolean = false) {
    val scale by animateFloatAsState(
        targetValue = if (marcado) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (marcado) 15.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = tema.accent.copy(alpha = glowAlpha),
                spotColor = tema.accent.copy(alpha = glowAlpha)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(tema.bgCard.copy(alpha = 0.9f))
            .border(
                width = 1.dp,
                color = if (marcado) tema.accent else tema.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !isEstatico) { onMudar(!marcado) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, color = if (marcado) Color.White else tema.textMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = if (marcado) tema.textMain else tema.textDim, fontSize = 11.sp)
        }
        if (!isEstatico) {
            val trackColor by animateColorAsState(
                targetValue = if (marcado) tema.accent else tema.border,
                animationSpec = tween(300),
                label = "track_color"
            )
            val thumbOffset by animateDpAsState(
                targetValue = if (marcado) 20.dp else 2.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "thumb_offset"
            )

            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(trackColor)
                    .clickable { onMudar(!marcado) }
                    .padding(2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

// --- FUNÇÕES AUXILIARES ---

fun pegarHwid(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "android_desconhecido"
}

suspend fun validarKey(key: String, context: Context): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("key", key.trim())
            put("hwid", pegarHwid(context))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://qzz-backend.onrender.com/api/auth/login")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val resBody = response.body?.string() ?: ""
        val jsonRes = JSONObject(resBody)

        if (jsonRes.optBoolean("success")) {
            val expiry = jsonRes.optLong("expiresAt", System.currentTimeMillis() + 86400000L)
            return@withContext Pair(true, expiry)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext Pair(false, 0L)
}

suspend fun checarSessaoNoServidor(key: String, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("key", key.trim())
            put("hwid", pegarHwid(context))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://qzz-backend.onrender.com/api/auth/login")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val resBody = response.body?.string() ?: ""
        val jsonRes = JSONObject(resBody)

        return@withContext jsonRes.optBoolean("success", false)
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext true
    }
}

fun executarExploit(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage("com.dts.freefireth")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.dts.freefireth"))
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(marketIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao abrir o Free Fire", Toast.LENGTH_SHORT).show()
    }
}