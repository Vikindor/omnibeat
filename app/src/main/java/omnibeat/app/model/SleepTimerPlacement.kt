package omnibeat.app.model

enum class SleepTimerPlacement {
    PanelLeft,
    PanelRight,
    FreePlacement,
}

data class SleepTimerFreePosition(
    val xFraction: Float = 1f,
    val yFraction: Float = 0.5f,
)
