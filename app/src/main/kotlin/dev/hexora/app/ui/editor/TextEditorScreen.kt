/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    state: TextEditorUiState,
    viewModel: TextEditorViewModel,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmBack by remember { mutableStateOf(false) }
    val tryBack = { if (state.dirty) confirmBack = true else onBack() }
    BackHandler(onBack = tryBack)
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = tryBack) { Icon(Icons.Outlined.ArrowBack, "Voltar") }
                },
                title = {
                    Column {
                        Text(state.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.document?.let {
                            Text(
                                "${it.encoding.name} · ${it.lineEnding.name}${if (state.dirty) " · modificado" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSearch) { Icon(Icons.Outlined.Search, "Buscar e substituir") }
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = state.document?.editable == true && state.dirty && !state.saving,
                    ) { Icon(Icons.Outlined.Save, "Salvar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.searchVisible) SearchReplaceBar(state, viewModel)
            state.document?.takeIf { it.truncated }?.let {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Visualização segura: arquivo grande demais para edição integral. Apenas os primeiros 256 KiB foram carregados.",
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    CodeEditor(state = state, onValueChange = viewModel::updateValue)
                }
                if (state.saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("Descartar alterações?") },
            text = { Text("As mudanças ainda não foram salvas.") },
            confirmButton = { Button(onClick = onBack) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmBack = false }) { Text("Continuar editando") } },
        )
    }
}

@Composable
private fun SearchReplaceBar(state: TextEditorUiState, viewModel: TextEditorViewModel) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Buscar") },
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = viewModel::findNext) { Icon(Icons.Outlined.Search, "Próxima ocorrência") }
            Text("Regex", style = MaterialTheme.typography.labelMedium)
            Switch(checked = state.regex, onCheckedChange = { viewModel.toggleRegex() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.replace,
                onValueChange = viewModel::updateReplace,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Substituir por") },
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = viewModel::replaceSelection) { Icon(Icons.Outlined.FindReplace, "Substituir seleção") }
        }
    }
}

@Composable
private fun CodeEditor(state: TextEditorUiState, onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val lineCount = state.value.text.count { it == '\n' } + 1
    val gutter = remember(lineCount) {
        val visibleCount = lineCount.coerceAtMost(20_000)
        buildString {
            for (line in 1..visibleCount) append(line).append('\n')
            if (lineCount > visibleCount) append('…')
        }
    }
    val transformation = remember(state.entry.name, state.value.text.length) {
        if (state.value.text.length <= 200_000) CodeVisualTransformation(state.entry.name)
        else VisualTransformation.None
    }
    Row(
        Modifier.fillMaxSize().horizontalScroll(horizontal).verticalScroll(vertical)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = gutter,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        HorizontalDivider(modifier = Modifier.width(1.dp).height((lineCount * 20).dp.coerceAtMost(100_000.dp)))
        BasicTextField(
            value = state.value,
            onValueChange = onValueChange,
            modifier = Modifier.padding(10.dp).width(1600.dp),
            enabled = state.document?.editable == true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = transformation,
        )
    }
}

private class CodeVisualTransformation(fileName: String) : VisualTransformation {
    private val extension = fileName.substringAfterLast('.', "").lowercase()
    private val keywordPattern = when (extension) {
        "kt", "kts" -> Regex("\\b(class|fun|val|var|object|interface|when|if|else|for|while|return|suspend|import|package|private|public|internal|override|data|sealed)\\b")
        "java" -> Regex("\\b(class|interface|public|private|protected|static|final|void|new|return|if|else|for|while|package|import)\\b")
        "js", "jsx", "ts", "tsx" -> Regex("\\b(const|let|var|function|class|interface|type|return|if|else|for|while|import|export|async|await)\\b")
        "py" -> Regex("\\b(def|class|return|if|elif|else|for|while|import|from|async|await|try|except|with|lambda)\\b")
        "smali" -> Regex("(?m)^\\s*(\\.class|\\.super|\\.method|\\.end|\\.field|invoke-\\w+|move-\\w+|return-\\w+)")
        else -> Regex("\\b(true|false|null)\\b")
    }
    private val stringPattern = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    private val commentPattern = Regex("(?m)//.*$|#.*$")

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        keywordPattern.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = Color(0xFFB7C8FF)), match.range.first, match.range.last + 1)
        }
        stringPattern.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = Color(0xFFC6E88A)), match.range.first, match.range.last + 1)
        }
        commentPattern.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = Color(0xFF7D8A92)), match.range.first, match.range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
