package io.shubham0204.smollmandroid.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Mic
import compose.icons.feathericons.MicOff
import compose.icons.feathericons.Paperclip
import compose.icons.feathericons.Send
import compose.icons.feathericons.StopCircle
import compose.icons.feathericons.X
import android.util.Log
import io.shubham0204.smollmandroid.llm.FileTextExtractor
import io.shubham0204.smollmandroid.R
import io.shubham0204.smollmandroid.data.Chat
import io.shubham0204.smollmandroid.ui.screens.chat.ChatScreenViewModel.ModelLoadingState

/** Maximum file size for attachments: 200KB to prevent context overflow. */
private const val MAX_ATTACHED_FILE_BYTES = 5_000_000L

@Composable
fun MessageInput(
    currChat: Chat,
    modelLoadingState: ModelLoadingState,
    audioTranscriptionUIState: AudioTranscriptionUIState,
    isGeneratingResponse: Boolean,
    onEvent: (ChatScreenUIEvent) -> Unit,
    defaultQuestion: String? = null,
    attachedFile: ChatFileAttachment? = null,
) {
    if (currChat.llmModelId == -1L) {
        Text(modifier = Modifier.padding(8.dp), text = stringResource(R.string.chat_select_model))
    } else {
        var questionText by rememberSaveable { mutableStateOf(defaultQuestion ?: "") }
        val keyboardController = LocalSoftwareKeyboardController.current
        val context = LocalContext.current

        val coroutineScope = rememberCoroutineScope()

        val filePickerLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let {
                    try {
                        // Check file size before reading to avoid OOM and context overflow
                        val fileSize = context.contentResolver.openFileDescriptor(it, "r")?.statSize ?: 0L
                        if (fileSize > MAX_ATTACHED_FILE_BYTES) {
                            android.widget.Toast.makeText(
                                context,
                                "File too large (max ${MAX_ATTACHED_FILE_BYTES / (1024 * 1024)}MB)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@let
                        }
                        val inputStream = context.contentResolver.openInputStream(it)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        if (bytes != null) {
                            val fileName = getFileName(context, it)
                            Log.d("PDF", "File selected: $fileName, size=${bytes.size}")
                            val bytesSize = bytes.size
                            val startMs = System.currentTimeMillis()
                            Log.d("RAG_READ", "[1/5] File selected: $fileName, rawBytes=$bytesSize")
                            coroutineScope.launch {
                                try {
                                    val content = withContext(Dispatchers.IO) {
                                        FileTextExtractor.extract(bytes, fileName)
                                    }
                                    val elapsed = System.currentTimeMillis() - startMs
                                    val wordCount = content.split(Regex("\\s+")).count { it.isNotBlank() }
                                    Log.d("RAG_READ", "[1/5] Extract done: $fileName chars=${content.length} words=$wordCount elapsed=${elapsed}ms")
                                    android.widget.Toast.makeText(
                                        context,
                                        "Parsed: $wordCount words (${content.length} chars)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onEvent(
                                        ChatScreenUIEvent.ChatEvents.AttachFile(
                                            name = fileName,
                                            content = content,
                                            sizeBytes = bytesSize.toLong()
                                        )
                                    )
                                } catch (e: Throwable) {
                                    Log.e("RAG_READ", "[1/5] Extract FAILED: $fileName error=${e.message}", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to parse file: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } catch (_: Throwable) {
                        Log.e("PDF", "File read failed")
                    }
                }
            }

        Column(modifier = Modifier.padding(8.dp)) {
            // File attachment preview
            if (attachedFile != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = FeatherIcons.Paperclip,
                        contentDescription = "Attached file",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachedFile.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onEvent(ChatScreenUIEvent.ChatEvents.RemoveAttachedFile) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = FeatherIcons.X,
                            contentDescription = "Remove attached file",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedVisibility(modelLoadingState == ModelLoadingState.IN_PROGRESS) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.chat_loading_model),
                    )
                }
                AnimatedVisibility(modelLoadingState == ModelLoadingState.FAILURE) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.chat_model_cannot_be_loaded),
                    )
                }
                AnimatedVisibility(modelLoadingState == ModelLoadingState.SUCCESS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // File picker button (always visible when model is loaded)
                        IconButton(onClick = { filePickerLauncher.launch(arrayOf("text/*", "application/pdf")) }) {
                            Icon(
                                FeatherIcons.Paperclip,
                                contentDescription = "Attach file",
                            )
                        }
                        if (audioTranscriptionUIState.isAvailable) {
                            IconButton(
                                onClick = {
                                    if (audioTranscriptionUIState.isRecording) {
                                        onEvent(ChatScreenUIEvent.ChatEvents.StopAudioTranscription)
                                    } else {
                                        onEvent(ChatScreenUIEvent.ChatEvents.StartAudioTranscription {
                                            questionText = it
                                        })
                                    }
                                }
                            ) {
                                if (audioTranscriptionUIState.isRecording) {
                                    Icon(
                                        FeatherIcons.MicOff,
                                        contentDescription = "Stop Audio Transcription",
                                    )
                                } else {
                                    Icon(
                                        FeatherIcons.Mic,
                                        contentDescription = "Start Audio Transcription",
                                    )
                                }
                            }
                        }
                        TextField(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            value = questionText,
                            onValueChange = { questionText = it },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                TextFieldDefaults.colors(
                                    disabledTextColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            placeholder = {
                                Text(
                                    text =
                                        if (audioTranscriptionUIState.isRecording) {
                                            stringResource(R.string.chat_listening)
                                        } else {
                                            stringResource(R.string.chat_ask_question)
                                        },
                                )
                            },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (isGeneratingResponse) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                                IconButton(
                                    onClick = {
                                        onEvent(ChatScreenUIEvent.ChatEvents.StopGeneration)
                                    },
                                ) {
                                    Icon(FeatherIcons.StopCircle, contentDescription = "Stop")
                                }
                            }
                        } else {
                            IconButton(
                                enabled = questionText.isNotEmpty() || attachedFile != null,
                                modifier =
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape,
                                    ),
                                onClick = {
                                    keyboardController?.hide()
                                    onEvent(
                                        ChatScreenUIEvent.ChatEvents.SendUserQuery(
                                            questionText.ifEmpty { "Please analyze the attached file." }
                                        )
                                    )
                                    questionText = ""
                                },
                            ) {
                                Icon(
                                    imageVector = FeatherIcons.Send,
                                    contentDescription = "Send text",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts the filename from a content URI by querying the content resolver.
 * Falls back to the last path segment if the display name is unavailable.
 */
private fun getFileName(
    context: android.content.Context,
    uri: Uri,
): String {
    var name = uri.lastPathSegment ?: "unknown"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            name = cursor.getString(nameIndex) ?: name
        }
    }
    return name
}
