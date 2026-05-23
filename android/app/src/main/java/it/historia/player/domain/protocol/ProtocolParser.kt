package it.historia.player.domain.protocol

import it.historia.player.domain.model.PlayerEvent

object ProtocolParser {
    fun parse(line: String): PlayerEvent {
        val noCrLf = line
            .replace("\r", "")
            .replace("\n", "")

        val normalized = noCrLf.trim()
        if (normalized.isEmpty()) {
            return PlayerEvent.Unknown(noCrLf)
        }

        if (normalized.startsWith("MSG:")) {
            val message = normalized.substringAfter("MSG:", missingDelimiterValue = "").trim()
            return PlayerEvent.Message(message)
        }

        if (normalized.startsWith("T")) {
            val index = normalized.drop(1).toIntOrNull()
            if (index != null) {
                return PlayerEvent.Touch(index)
            }
        }

        return PlayerEvent.Unknown(noCrLf)
    }
}
