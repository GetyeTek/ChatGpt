package com.gpt.auto.reader

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.ClipboardManager
import android.content.Context
import android.content.ClipData
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("GPT AUTO-READER", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Cyan)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Restoring the feature they stole.", color = Color.Gray)
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        Button(
                            onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
                        ) {
                            Text("GRANT PERMISSION", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Status: Ensure 'GPT Auto Reader' is ON", color = Color.White, fontSize = 12.sp)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("DEBUG LOGS", fontWeight = FontWeight.Bold, color = Color.Red)
                            Row {
                                IconButton(onClick = { 
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Logs", DebugLogger.getFullLog()))
                                }) {
                                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.Cyan)
                                }
                                IconButton(onClick = { DebugLogger.clear() }) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        }

                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF111111))
                            .padding(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(DebugLogger.logs) { log ->
                                    SelectionContainer {
                                        Text(
                                            text = log,
                                            color = if (log.contains("ACTION")) Color.Green 
                                                   else if (log.contains("UI DUMP")) Color(0xFF666666)
                                                   else Color.White,
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFF222222))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}