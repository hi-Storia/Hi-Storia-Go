package it.historia.player.domain.model

sealed interface PlayerEvent {
    data class Touch(val index: Int) : PlayerEvent
    data class Message(val text: String) : PlayerEvent
    data class Unknown(val originalLine: String) : PlayerEvent
}
