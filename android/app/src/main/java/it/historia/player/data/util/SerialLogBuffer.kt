package it.historia.player.data.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SerialLogBuffer(
    private val maxLines: Int = DEFAULT_MAX_LINES
) {
    private val lock = Any()
    private val deque = ArrayDeque<String>(maxLines)
    private val _linesFlow = MutableStateFlow<List<String>>(emptyList())

    val linesFlow: StateFlow<List<String>> = _linesFlow.asStateFlow()

    fun append(line: String) {
        val snapshot: List<String> = synchronized(lock) {
            deque.addLast(line)
            while (deque.size > maxLines) {
                deque.removeFirst()
            }
            deque.toList()
        }
        _linesFlow.value = snapshot
    }

    companion object {
        private const val DEFAULT_MAX_LINES = 300
    }
}
