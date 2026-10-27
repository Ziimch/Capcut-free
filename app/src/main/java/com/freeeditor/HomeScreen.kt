package com.freeeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(projects: List<Project>, onNew: () -> Unit, onOpen: (Project) -> Unit, onDelete: (Project) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Free Video Editor", style = MaterialTheme.typography.headlineMedium)
        Text("No watermark. No ads. No account.", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New project")
        }
        Spacer(Modifier.height(16.dp))
        Text("Recent projects", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(projects, key = { it.id }) { p ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(p) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("${p.clips.size} clips · " + DateFormat.getDateTimeInstance().format(Date(p.updatedAt)),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
    }
}
