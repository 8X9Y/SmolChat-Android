package io.shubham0204.smollmandroid.modelmarket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import compose.icons.feathericons.HardDrive

@Composable
fun ModelMarketScreen(
    availableModels: List<ModelInfo>,
    deviceRamGB: Int,
    downloadingModel: ModelInfo?,
    downloadProgress: Float,
    downloadState: ModelDownloadManager.DownloadState?,
    onDownloadClick: (ModelInfo) -> Unit,
    onEnterChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Recommended for ${deviceRamGB}GB RAM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(availableModels, key = { it.filename }) { model ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(model.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (model.description.isNotEmpty()) {
                        Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FeatherIcons.HardDrive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text("${model.sizeMB}MB  |  >=${model.ramRequiredGB}GB RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val isThisModel = downloadingModel?.filename == model.filename
                    when {
                        isThisModel && downloadState is ModelDownloadManager.DownloadState.Progress -> {
                            LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                            Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                        isThisModel && downloadState is ModelDownloadManager.DownloadState.Importing -> {
                            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text(" Importing...") }
                        }
                        isThisModel && downloadState is ModelDownloadManager.DownloadState.Ready -> {
                            Text("✓ Ready", color = Color(0xFF4CAF50))
                        }
                        else -> {
                            Button(onClick = { onDownloadClick(model) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                                Icon(FeatherIcons.Download, null, Modifier.size(16.dp))
                                Text(" Download (${model.sizeMB}MB)")
                            }
                        }
                    }
                }
            }
        }
    }
}
