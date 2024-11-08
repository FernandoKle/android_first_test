package com.example.first_test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.delay
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

// Mew Mew Ichigo =D
//const val splashScreenGIF = "https://media.tenor.com/nDAaARpgX8gAAAAM/tokyo-mew-mew-mew-mew-power.gif"
// Otro >:(
const val splashScreenGIF = "https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExbmNhNTR4NjZma3c1aGQ1MDdud3V3N3Rjd3JnYzA2em0xYjlqaWRqNCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/RgzryV9nRCMHPVVXPV/giphy.webp"

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

data class Result(
    val score: Float,
    val classIndex: Int,
    val rect: RectF,
    var text: String,
    var esDecimal: Boolean,
    var x: Float = 0f,
    var y: Float = 0f
)

fun getCorrectionAngle(results: List<Result>): Int{

    val detections = results.sortedBy{ it.rect.left }
    val p1 = detections.first().rect
    val p2 = detections.last().rect

    val y2 = p2.bottom.toDouble()
    val y1 = p1.bottom.toDouble()
    val x2 = p2.right.toDouble()
    val x1 = p1.right.toDouble()

    return Math.toDegrees(atan2(y2 - y1, x2 - x1)).toInt()
}

fun aplicarReduccionDeRuido(bitmap: Bitmap): Bitmap {

    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    //val medMat = Mat()
    Imgproc.medianBlur(mat, mat, 9)

    //if (medMat.type() != CvType.CV_8UC3) {
    //    medMat.convertTo(medMat, CvType.CV_8UC3)
    //}

    /** Error: Assertion failed ((src.type() == CV_8UC1 || src.type() == CV_8UC3)
     * && src.data != dst.data) in bilateralFilter_8u **/
    //val filMat = Mat()
    //Imgproc.bilateralFilter(medMat, filMat, 5, 150.0, 150.0)

    //val outputBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    //Utils.matToBitmap(mat, outputBitmap)

    Utils.matToBitmap(mat, bitmap)
    mat.release()
    //medMat.release()
    //filMat.release()

    return bitmap
}

fun Bitmap.toMat(): Mat { //source: Bitmap,
    val mat = Mat()
    Utils.bitmapToMat(this, mat)
    return mat
}

fun bitmapFromMat(mat: Mat): Bitmap { //source: Bitmap,

    val outputBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, outputBitmap)

    mat.release()
    return outputBitmap
}

fun Bitmap.rotateBitmapPure(angle: Float): Bitmap { //source: Bitmap,
    val matrix = Matrix().apply {
        postRotate(-angle)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.rotateBitmapAroundAxis(angle: Double, x: Double, y: Double): Bitmap { //source: Bitmap,

    val mat = this.toMat()
    val rM = Imgproc.getRotationMatrix2D(Point(x,y), angle, 1.0)

    Imgproc.warpAffine(mat, mat, rM, Size(mat.width().toDouble(), mat.height().toDouble()))

    return bitmapFromMat(mat)
}

fun getDetectionScore(a: FloatArray): FloatArray {

    val outSize = 16
    val numPredictions = a.size / outSize
    var score = 0f
    var count = 0
    var cumulativeScore = 0f

    for (i in 0 until numPredictions) {

        val prediction : FloatArray = a.sliceArray((i*outSize)until (outSize+i*outSize))

        cumulativeScore += prediction[4]

        if (prediction[4] > 0.5f){
            score += prediction[4]
            count += 1
        }
    }

    score /= count.toFloat()
    cumulativeScore /= numPredictions

    return floatArrayOf(score, cumulativeScore)
}

fun guardarString(context: Context, clave: String, valor: String) {
    val sharedPreferences = context.getSharedPreferences("datosMedidor", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString(clave, valor)
    editor.apply()  // Usa apply() en lugar de commit() para realizar la operación de manera asíncrona
}

fun obtenerString(context: Context, clave: String): String? {
    val sharedPreferences = context.getSharedPreferences("datosMedidor", Context.MODE_PRIVATE)
    return sharedPreferences.getString(clave, "") // Retorna null si no encuentra la clave
}

fun distanciaCajas(a: Result, b: Result): Float {

    val ra = a.rect
    val rb = b.rect

    //return rb.left - ra.right
    return if (ra.right < rb.left) {
        rb.left - ra.right
    } else if (rb.right < ra.left) {
        ra.left - rb.right
    } else {
        0f
    }
}

fun mediaDistancias(results: List<Result>): Float {
    var media = 0f
    val cantidad = results.size
    var comas = 0

    for (i in 0 until cantidad-1) {
        if ((results[i].classIndex != 0) and (results[i+1].classIndex != 0)){
            media += distanciaCajas(results[i], results[i+1])
        }
        else {
            comas ++
        }
    }

    return media / ( cantidad - comas )
}

fun estaAdentro(ra: Result, rb: Result): Boolean {
    val a = ra.rect
    val b = rb.rect

    // Encontrar la menor area
    val areaA = a.width() * a.height()
    val areaB = b.width() * b.height()
    val areaMenor = min(areaA, areaB)

    // Area de interseccion
    val interLeft = max(a.left, b.left)
    val interTop = max(a.top, b.top)
    val interRight = min(a.right, b.right)
    val interBottom = min(a.bottom, b.bottom)

    val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)

    // areaMenor siempre es mayor a interArea
    val ratio = interArea / areaMenor

    if (ratio >= 0.5f){
        return true
    }
    else {
        return false
    }

}

fun doOCR(results: List<Result>) : String {

    if (results.isEmpty()){
        return ""
    }

    var text = ""
    val sortedResults: MutableList<Result> = results.sortedBy{ it.rect.left }.toMutableList()

    var encontroComa = false
    /* Viejo metodo para encontrar comas
    for (r in sortedResults){
        if (r.classIndex == 0){
            encontroComa = true
        }
        if (encontroComa){
            r.esDecimal = true
        }
    }
    */
    // Nuevo metodo para encontrar comas
    val resultComa = sortedResults.find { it.classIndex == 0 }
    resultComa ?.let {
        for (r in sortedResults){
            if (estaAdentro(r, it)){
                r.esDecimal = true
            }
        }
    }

    sortedResults.removeAll { it.classIndex == 0 }

    val cantidad = sortedResults.size
    val media = mediaDistancias(sortedResults)

    encontroComa = false

    for (i in 0 until cantidad-1) {

        val dist = distanciaCajas(sortedResults[i], sortedResults[i+1])

        if ( ( abs(dist - media) > media ) ){ //(dist < media / 2) or
            text += sortedResults[i].text
            text += "X"
        }
        else {
            text += sortedResults[i].text
        }

        if ((sortedResults[i+1].esDecimal) and (encontroComa == false)){
            encontroComa = true
            text += ","
        }

    }

    text += sortedResults[cantidad - 1].text

    return text
}

fun doOCRDigital(results: List<Result>) : String {

    if (results.isEmpty()){
        return ""
    }

    var text = ""
    val sortedResults: MutableList<Result> = results.sortedBy{ it.rect.left }.toMutableList()

    var encontroComa = false
    for (r in sortedResults){
        if (r.classIndex == 0){
            encontroComa = true
        }
        if (encontroComa){
            r.esDecimal = true
        }
    }

    sortedResults.removeAll { it.classIndex == 0 }

    val cantidad = sortedResults.size
    val media = mediaDistancias(sortedResults)

    encontroComa = false

    for (i in 0 until cantidad-1) {

        val dist = distanciaCajas(sortedResults[i], sortedResults[i+1])

        if ( ( abs(dist - media) > media ) ){ //(dist < media / 2) or
            text += sortedResults[i].text
            text += "X"
        }
        else {
            text += sortedResults[i].text
        }

        if ((sortedResults[i+1].esDecimal) and (encontroComa == false)){
            encontroComa = true
            text += ","
        }

    }

    text += sortedResults[cantidad - 1].text

    return text
}

fun createImageFileUri(context: Context): Uri {
    val imageFile = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        "JPEG_${System.currentTimeMillis()}.jpg"
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )
}
fun getImageRotation(photoUri: Uri, context: Context): Float {
    val inputStream = context.contentResolver.openInputStream(photoUri)
    val exif = inputStream?.let { ExifInterface(it) }
    val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
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