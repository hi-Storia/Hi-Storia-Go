package it.historia.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LogPanel(
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Color(0xFF2A2A2A))
            .padding(8.dp)
    ) {
        itemsIndexed(lines) { _, line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = lineColor(line)
            )
        }
    }
}

private fun lineColor(line: String): Color {
    return when {
        line.startsWith("USB:") -> Color(0xFF6FCF97)
        line.startsWith("ERR:") -> Color(0xFFFF6B6B)
        else -> Color(0xFFF5F5F5)
    }
}
