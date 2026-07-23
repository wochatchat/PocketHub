package com.pockethub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.pockethub.R
import com.pockethub.ui.LocalAppImageLoader
import kotlinx.coroutines.launch

/**
 * Composition-local providing a function that opens an image URL into the in-app
 * zoomable preview. Screens rendering user-facing markdown copy should route
 * `LinkKind.IMAGE_URL` / `IMAGE` taps through this instead of the browser.
 *
 * Default is a no-op so leaf composables don't crash if a screen forgets to provide
 * it (root AppNavigation wires the real implementation).
 */
val LocalImagePreviewer = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * Screen-level zoomable image preview. Renders a single image at fit-into-screen with
 * pinch + double-tap-to-toggle + pan support, plus tap-outside to reset/exit.
 *
 * - At 1x: single tap exits the screen, double tap zooms to 2x.
 * - At >1x: single tap resets to 1x, double tap toggles (2x <-> 1x).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
    imageUrl: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val context = LocalContext.current

    fun animateTo(target: Float, dx: Float = 0f, dy: Float = 0f) {
        scope.launch { scale.animateTo(target, tween(180)) }
        scope.launch { offsetX.animateTo(dx, tween(180)) }
        scope.launch { offsetY.animateTo(dy, tween(180)) }
    }

    val current = scale.value
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = imageUrl.substringAfterLast('/').substringBefore('?')
                    Text(
                        name.ifBlank { imageUrl },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(imageUrl) {
                    detectTapGestures(
                        onTap = {
                            if (current > 1.05f) {
                                animateTo(1f)
                            } else {
                                onBack()
                            }
                        },
                        onDoubleTap = {
                            // Toggle between 1x and 2x — centered pan stays at 0,0 for predictable UX.
                            val target = if (current > 1.05f) 1f else 2f
                            animateTo(target)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
                imageLoader = LocalAppImageLoader.current,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offsetX.value
                        translationY = offsetY.value
                    }
                    .pointerInput(imageUrl) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scope.launch {
                                val next = (scale.value * zoom).coerceIn(1f, 6f)
                                scale.snapTo(next)
                                // Allow pan only when zoomed in; at 1x keep panned offset at 0 so the
                                // fit-to-screen layout dominates (otherwise a stray pan at 1x looks broken).
                                if (next > 1f) {
                                    launch { offsetX.snapTo(offsetX.value + pan.x) }
                                    launch { offsetY.snapTo(offsetY.value + pan.y) }
                                } else {
                                    launch { offsetX.snapTo(0f) }
                                    launch { offsetY.snapTo(0f) }
                                }
                            }
                        }
                    },
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.BrokenImage, null, tint = Color.White, modifier = Modifier.size(40.dp))
                            Text(
                                imageUrl,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                            )
                        }
                    }
                },
            )
        }
    }
}
