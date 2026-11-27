package com.vibecodegram

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.collectLatest

/**
 * A screen-coordinate backdrop, including for Dialog/Popup (separate Android windows).
 * Sources never contain the surfaces that sample them: wallpaper for cards, screen for overlays.
 */
@Stable
internal class GlassBackdrop(private val layer: GraphicsLayer) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    internal var origin by mutableStateOf(Offset.Zero)
    internal var recorded = false
    internal var readers by mutableIntStateOf(0)
    internal var frame by mutableLongStateOf(0L)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        if (!recorded || coordinates == null || !coordinates.isAttached) return
        val here = coordinates.positionOnScreen()
        if (!here.x.isFinite() || !here.y.isFinite()) return
        translate(left = origin.x - here.x, top = origin.y - here.y) {
            drawLayer(layer)
        }
    }

    internal fun recordAndDraw(scope: androidx.compose.ui.graphics.drawscope.ContentDrawScope) = with(scope) {
        layer.record { scope.drawContent() }
        recorded = true
        drawLayer(layer)
    }
}

@Composable
internal fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

@Composable
internal fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier {
    if (Build.VERSION.SDK_INT < 31) return this
    // Refresh while an overlay samples the source; idle screens do not run a frame loop.
    LaunchedEffect(backdrop) {
        snapshotFlow { backdrop.readers > 0 }.collectLatest { watched ->
            while (watched) withFrameNanos { backdrop.frame = it }
        }
    }
    return onGloballyPositioned {
        val position = it.positionOnScreen()
        if (position.x.isFinite() && position.y.isFinite()) backdrop.origin = position
    }.drawWithContent {
        backdrop.frame
        backdrop.recordAndDraw(this)
    }
}
