package com.gpt.auto.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            var isEnabled by remember { mutableStateOf(checkAccessibilityPermission(context)) }
            var showLogModal by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                while(true) {
                    isEnabled = checkAccessibilityPermission(context)
                    kotlinx.coroutines.delay(1000)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("GPT AUTO-READER", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Cyan)
                        Text("ENGINEERING THE FUTURE", color = Color.Gray, letterSpacing = 2.sp, fontSize = 10.sp)
                        
                        Spacer(modifier = Modifier.height(64.dp))

                        if (!isEnabled) {
                            Button(
                                onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("ENABLE ACCESS", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(48.dp))
                            Text("SYSTEM ARMED", color = Color.Green, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        OutlinedButton(
                            onClick = { showLogModal = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Cyan)
                        ) {
                            Text("OPEN SYSTEM LOGS", color = Color.Cyan)
                        }

                        if (showLogModal) {
                            LogModal(onDismiss = { showLogModal = false })
                        }
                    }
                }
            }
        }
    }

    private fun checkAccessibilityPermission(context: Context): Boolean {
        val expected = "${context.packageName}/${AutoReadService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(expected)
    }

    @Composable
    fun LogModal(onDismiss: () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF050505)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TERMINAL OUTPUT", color = Color.Red, fontWeight = FontWeight.Bold)
                        Row {
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val data = ClipData.newPlainText("AutoReaderLogs", DebugLogger.getFullLog())
                                cm.setPrimaryClip(data)
                                android.widget.Toast.makeText(context, "Logs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, "Copy", tint = Color.Cyan)
                            }
                            IconButton(onClick = { DebugLogger.clear() }) {
                                Icon(Icons.Filled.Delete, "Clear", tint = Color.Gray)
                            }
                            TextButton(onClick = onDismiss) {
                                Text("CLOSE", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    HorizontalDivider(color = Color.Red, thickness = 2.dp)

                    Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(DebugLogger.logs) { log ->
                                SelectionContainer {
                                    Text(
                                        text = log,
                                        color = when {
                                            log.contains("ACTION") -> Color.Green
                                            log.contains("TRACE") -> Color(0xFF8888FF)
                                            log.contains("UI DUMP") -> Color(0xFF444444)
                                            log.contains("DECISION") -> Color.Yellow
                                            else -> Color.White
                                        },
                                        fontSize = 9.sp,
                                        lineHeight = 12.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}