package com.example.first_test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

const val splashScreenGIF = "https://media.tenor.com/nDAaARpgX8gAAAAM/tokyo-mew-mew-mew-mew-power.gif"

//private fun org.opencv.core.Rect.toAndroidRect(): android.graphics.Rect {
//    return android.graphics.Rect( x, y, x + width, y + height )
//}

fun assetFilePath(context: Context, assetName: String): String {
    val file = File(context.filesDir, assetName)
    try {
        context.assets.open(assetName).use { inputStream ->
            file.outputStream().use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
    } catch (e: IOException) {
        Log.e("PytorchHelloWorld", "Error process asset $assetName to file path")
    }
    return file.absolutePath
}

fun Bitmap.rotateBitmap(angle: Int): Bitmap { //source: Bitmap,
    val matrix = Matrix().apply {
        postRotate(-angle.toFloat())
        postScale(-1f,-1f)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun getIoU(rect1: RectF, rect2: RectF): Float {
    val intersectionLeft = max(rect1.left, rect2.left)
    val intersectionTop = max(rect1.top, rect2.top)
    val intersectionRight = min(rect1.right, rect2.right)
    val intersectionBottom = min(rect1.bottom, rect2.bottom)

    val intersectionArea = max(0f, intersectionRight - intersectionLeft) * max(0f, intersectionBottom - intersectionTop)

    val rect1Area = (rect1.right - rect1.left) * (rect1.bottom - rect1.top)
    val rect2Area = (rect2.right - rect2.left) * (rect2.bottom - rect2.top)

    val unionArea = rect1Area + rect2Area - intersectionArea

    return if (unionArea > 0) intersectionArea / unionArea else 0f
}

fun argmax(array: FloatArray): Int {
    var maxIdx = 0
    var maxValue = array[0]
    for (i in array.indices) {
        if (array[i] > maxValue) {
            maxValue = array[i]
            maxIdx = i
        }
    }
    return maxIdx
}

@Composable
fun RainbowButton(onClick: () -> Unit, text: String) {
    val colors = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Blue,
    )

    val infiniteTransition = rememberInfiniteTransition()
    val color by infiniteTransition.animateColor(
        initialValue = colors.first(),
        targetValue = colors.last(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Restart
        ), label = ""
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .background(color, shape = RoundedCornerShape(12.dp))
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = text, fontSize = 16.sp)
    }
}

@Composable
fun SplashScreen(onComplete: () -> Unit, speed: Float = 0.01f) {

    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    LaunchedEffect(Unit) {
        while (progress < 1f) {
            delay(50)
            progress += speed
        }
        isLoading = false
        onComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = splashScreenGIF,
                imageLoader = imageLoader, // Necesario para GIFs
                contentDescription = null,
                placeholder = painterResource(id = R.drawable.cat_bye),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .size(width = 200.dp, height = 180.dp)
            )
            Text(
                text = "Cargando...",
                fontSize = 24.sp,
                color = Color.Green,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}