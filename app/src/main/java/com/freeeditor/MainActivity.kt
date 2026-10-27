package com.freeeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    background = Color(0xFF0E0E10), surface = Color(0xFF1A1A1E),
    primary = Color(0xFF4DD0E1), onPrimary = Color.Black,
    onBackground = Color.White, onSurface = Color.White,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = Dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val ctx = LocalContext.current
                    val store = remember { ProjectStore(ctx) }
                    var current by remember { mutableStateOf<Project?>(null) }
                    var list by remember { mutableStateOf(store.loadAll()) }
                    val p = current
                    if (p == null) {
                        HomeScreen(list,
                            onNew = {
                                val np = Project(name = "Project ${list.size + 1}")
                                store.save(np); current = np
                            },
                            onOpen = { current = it },
                            onDelete = { store.delete(it.id); list = store.loadAll() })
                    } else {
                        BackHandler { current = null; list = store.loadAll() }
                        EditorScreen(p, onChange = { store.save(it) },
                            onBack = { current = null; list = store.loadAll() })
                    }
                }
            }
        }
    }
}
