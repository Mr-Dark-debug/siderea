package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4dp grid. */
object SideriaSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal screen margin. */
    val gutter = 20.dp
}

object SideriaDimens {
    /** Minimum size of any primary control: big enough for a cold, gloved or one-handed thumb. */
    val touchTarget = 56.dp

    /** Secondary controls never go below the Material minimum. */
    val touchTargetMin = 48.dp
    val hairline = 1.dp
    val iconSmall = 20.dp
    val iconMedium = 24.dp
}

object SideriaShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(20.dp)
    val large = RoundedCornerShape(28.dp)
    val pill = CircleShape
}

/** Motion is quick and quiet; nothing bounces in a dark field. */
object SideriaMotion {
    private val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> fast() = tween<T>(durationMillis = 120, easing = standardEasing)

    fun <T> standard() = tween<T>(durationMillis = 220, easing = standardEasing)

    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}
