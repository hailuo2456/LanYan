package com.lanyan.chat.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.RectangleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val AVATAR_SIZE = 512
private const val MAX_SOURCE_SIZE = 1440
private const val MIN_ZOOM = 1f
private const val BASE_MAX_ZOOM = 6f

/**
 * 头像裁剪弹窗：拖动平移、双指缩放，中间圆形取景，保存时输出 512×512 的正方形头像。
 */
@Composable
fun AvatarCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var source by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    val transform = remember(uri) { CropTransform() }
    var cropSide by remember(uri) { mutableStateOf(MaxCropSide) }

    LaunchedEffect(uri) {
        val loaded = runCatching { loadBitmap(context, uri) }.getOrNull()
        if (loaded == null) failed = true else source = loaded
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                val bitmap = source
                val density = LocalDensity.current
                val sidePx = with(density) { cropSide.roundToPx().toFloat() }
                val canExport = bitmap != null && transform.canCover(sidePx, bitmap)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Text(
                        "裁剪头像",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = canExport,
                        onClick = {
                            val target = bitmap ?: return@TextButton
                            val cropped = runCatching {
                                cropToSquare(target, sidePx, transform.scale, transform.offset)
                            }.getOrNull()
                            if (cropped != null) onConfirm(cropped)
                        },
                    ) { Text("保存") }
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val side = minOf(maxWidth, maxHeight, MaxCropSide)
                    SideEffect { if (cropSide != side) cropSide = side }
                    when {
                        failed -> Text(
                            "无法读取所选图片，请重新选择",
                            color = MaterialTheme.colorScheme.error,
                        )
                        source == null -> CircularProgressIndicator()
                        else -> CropCanvas(
                            bitmap = source!!,
                            side = side,
                            transform = transform,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (canExport) {
                            "拖动调整位置 · 双指缩放或双击放大"
                        } else {
                            "原图已完整显示：双指放大（或双击），直到填满圆形区域"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { transform.reset() }) { Text("重置") }
                }
            }
        }
    }
}

private val MaxCropSide = 360.dp

/** 裁剪变换状态。放在稳定对象里，手势回调每次都能读到最新值。 */
private class CropTransform {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun panZoom(pan: Offset, zoom: Float, boxSize: Float, bitmap: Bitmap) {
        val maxScale = maxScale(boxSize, bitmap)
        val newScale = (scale * zoom).coerceIn(MIN_ZOOM, maxScale)
        val fit = fitFactor(boxSize, bitmap)
        val maxX = axisMax(bitmap.width * fit * newScale, boxSize)
        val maxY = axisMax(bitmap.height * fit * newScale, boxSize)
        scale = newScale
        offset = Offset(
            (offset.x + pan.x).coerceIn(-maxX, maxX),
            (offset.y + pan.y).coerceIn(-maxY, maxY),
        )
    }

    fun toggleZoom(boxSize: Float, bitmap: Bitmap) {
        if (scale > 1.05f) {
            scale = MIN_ZOOM
            offset = Offset.Zero
            return
        }
        val cover = coverScale(boxSize, bitmap).coerceAtMost(maxScale(boxSize, bitmap))
        if (cover <= MIN_ZOOM + 0.01f) {
            // 方图在原始大小就已铺满，双击只负责回到居中状态
            scale = MIN_ZOOM
            offset = Offset.Zero
        } else {
            scale = cover
            clampOffset(boxSize, bitmap)
        }
    }

    fun reset() {
        scale = MIN_ZOOM
        offset = Offset.Zero
    }

    /** 图片是否完全盖住圆形取景框（尺寸与位置都要满足），只有盖住时裁剪结果才没有空白。 */
    fun canCover(boxSize: Float, bitmap: Bitmap): Boolean {
        val fit = fitFactor(boxSize, bitmap)
        val displayWidth = bitmap.width * fit * scale
        val displayHeight = bitmap.height * fit * scale
        val maxX = (displayWidth - boxSize) / 2f
        val maxY = (displayHeight - boxSize) / 2f
        return maxX >= -0.5f && maxY >= -0.5f &&
            abs(offset.x) <= maxX + 0.5f && abs(offset.y) <= maxY + 0.5f
    }

    private fun clampOffset(boxSize: Float, bitmap: Bitmap) {
        val fit = fitFactor(boxSize, bitmap)
        val maxX = axisMax(bitmap.width * fit * scale, boxSize)
        val maxY = axisMax(bitmap.height * fit * scale, boxSize)
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    /**
     * 单轴可移动范围。图片比取景框大时按边缘对齐；比取景框小时允许在框内滑动，
     * 并额外留一点余量，避免“刚好等宽时拖不动”的错觉。
     */
    private fun axisMax(displaySize: Float, boxSize: Float): Float {
        val base = abs(displaySize - boxSize) / 2f
        return if (displaySize < boxSize - 0.5f) base + boxSize * 0.08f else base
    }

    /** 让原图完整放进取景框的缩放系数。 */
    private fun fitFactor(boxSize: Float, bitmap: Bitmap): Float =
        min(boxSize / bitmap.width.toFloat(), boxSize / bitmap.height.toFloat())

    /** 刚好填满取景框所需的缩放倍数。 */
    private fun coverScale(boxSize: Float, bitmap: Bitmap): Float {
        val fit = fitFactor(boxSize, bitmap)
        return max(boxSize / (bitmap.width * fit), boxSize / (bitmap.height * fit))
    }

    private fun maxScale(boxSize: Float, bitmap: Bitmap): Float =
        max(BASE_MAX_ZOOM, coverScale(boxSize, bitmap) * 1.3f)
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    side: androidx.compose.ui.unit.Dp,
    transform: CropTransform,
) {
    val scale = transform.scale
    val offset = transform.offset
    Box(Modifier.size(side).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        transform.panZoom(
                            pan = pan,
                            zoom = zoom,
                            boxSize = size.width.toFloat(),
                            bitmap = bitmap,
                        )
                    }
                }
                .pointerInput(bitmap) {
                    detectTapGestures(
                        onDoubleTap = {
                            transform.toggleZoom(
                                boxSize = size.width.toFloat(),
                                bitmap = bitmap,
                            )
                        },
                    )
                },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addOval(Rect(center = center, radius = radius))
            }
            drawPath(mask, Color.Black.copy(alpha = 0.55f))
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** 把取景框内的内容裁成正方形，并缩放到头像尺寸。 */
private fun cropToSquare(
    bitmap: Bitmap,
    sidePx: Float,
    scale: Float,
    offset: Offset,
): Bitmap {
    val fit = min(sidePx / bitmap.width.toFloat(), sidePx / bitmap.height.toFloat())
    val displayWidth = bitmap.width * fit * scale
    val displayHeight = bitmap.height * fit * scale
    val imageLeft = offset.x - displayWidth / 2f
    val imageTop = offset.y - displayHeight / 2f
    val pixelsPerBitmapPixel = fit * scale
    val sourceSize = (sidePx / pixelsPerBitmapPixel)
        .toInt()
        .coerceIn(1, min(bitmap.width, bitmap.height))
    val x = ((-sidePx / 2f - imageLeft) / pixelsPerBitmapPixel)
        .toInt()
        .coerceIn(0, bitmap.width - sourceSize)
    val y = ((-sidePx / 2f - imageTop) / pixelsPerBitmapPixel)
        .toInt()
        .coerceIn(0, bitmap.height - sourceSize)
    val cropped = Bitmap.createBitmap(bitmap, x, y, sourceSize, sourceSize)
    val result = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true)
    if (result !== cropped && cropped !== bitmap) cropped.recycle()
    return result
}

private suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_SOURCE_SIZE) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@withContext null

    val orientation = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    rotateForOrientation(decoded, orientation)
}

private fun rotateForOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    val rotated = runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrNull() ?: return bitmap
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
