package com.example.contactgrid

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.roundToInt

/** Полноэкранная подгонка фото под рамку карточки: двигать пальцем, масштабировать щипком. */
@Composable
fun CropDialog(bitmap: Bitmap, aspect: Float, onCancel: () -> Unit, onDone: (CropRect) -> Unit) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Расчёт обрезки задаётся внутри области с фото, а вызывается кнопкой «Готово»
        val computeCrop = remember { arrayOfNulls<() -> CropRect>(1) }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()   // не залезать под статус-бар и навигационную панель
        ) {
            Text(
                "Двигайте фото пальцем, увеличивайте двумя пальцами",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                var fw = w * 0.86f
                var fh = fw / aspect
                if (fh > h * 0.94f) { fh = h * 0.94f; fw = fh * aspect }
                val frameLeft = (w - fw) / 2
                val frameTop = (h - fh) / 2
                val bw = bitmap.width.toFloat()
                val bh = bitmap.height.toFloat()
                val minScale = max(fw / bw, fh / bh)

                var scale by remember(bitmap, minScale) { mutableStateOf(minScale) }
                var offset by remember(bitmap, minScale) { mutableStateOf(Offset.Zero) } // центр фото относительно центра рамки
                val image = remember(bitmap) { bitmap.asImageBitmap() }

                fun clamp(o: Offset, s: Float): Offset {
                    val mx = (bw * s - fw) / 2
                    val my = (bh * s - fh) / 2
                    return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
                }

                fun imageTopLeft() = Offset(
                    frameLeft + fw / 2 + offset.x - bw * scale / 2,
                    frameTop + fh / 2 + offset.y - bh * scale / 2
                )

                computeCrop[0] = {
                    val tl = imageTopLeft()
                    val l = ((frameLeft - tl.x) / scale).roundToInt().coerceIn(0, bitmap.width - 1)
                    val t = ((frameTop - tl.y) / scale).roundToInt().coerceIn(0, bitmap.height - 1)
                    val cw = (fw / scale).roundToInt().coerceIn(1, bitmap.width - l)
                    val ch = (fh / scale).roundToInt().coerceIn(1, bitmap.height - t)
                    CropRect(l, t, cw, ch)
                }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap, minScale, fw, fh) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val ns = (scale * zoom).coerceIn(minScale, minScale * 6f)
                                val c = centroid - Offset(frameLeft + fw / 2, frameTop + fh / 2)
                                // Точка под пальцами остаётся на месте
                                val no = (offset - c) * (ns / scale) + c + pan
                                scale = ns
                                offset = clamp(no, ns)
                            }
                        }
                ) {
                    val tl = imageTopLeft()
                    drawImage(
                        image,
                        dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                        dstSize = IntSize((bw * scale).roundToInt(), (bh * scale).roundToInt()),
                        filterQuality = FilterQuality.Medium
                    )
                    val dim = Color.Black.copy(alpha = 0.62f)
                    drawRect(dim, Offset.Zero, Size(size.width, frameTop))
                    drawRect(dim, Offset(0f, frameTop + fh), Size(size.width, size.height - frameTop - fh))
                    drawRect(dim, Offset(0f, frameTop), Size(frameLeft, fh))
                    drawRect(dim, Offset(frameLeft + fw, frameTop), Size(size.width - frameLeft - fw, fh))
                    drawRoundRect(
                        Color.White,
                        Offset(frameLeft, frameTop),
                        Size(fw, fh),
                        CornerRadius(18.dp.toPx()),
                        style = Stroke(2.dp.toPx())
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                TextButton(onClick = onCancel) { Text("Отмена", color = Color.White, fontSize = 16.sp) }
                Button(onClick = { computeCrop[0]?.let { onDone(it()) } }) {
                    Text("Готово", fontSize = 16.sp)
                }
            }
        }
    }
}
