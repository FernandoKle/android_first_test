package com.example.first_test
/*

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.first_test.ui.theme.First_testTheme
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

class YoloActivity : ComponentActivity() {
    private var module: Module? = null
    private var classes: List<String>? = null
    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObjectDetectionApp()
        }

        try {
            /** Resultados
            // El modelo cuantizado perdio en velocidad por poco
            // habria que comparar el uso de memoria
             **/

            // 1280 ms en tel de fer
            module = LiteModuleLoader.load(assetFilePath(this, "yolov5s.torchscript"))

            // 1320 ms en tel de fer
            // module = LiteModuleLoader.load(assetFilePath(this, "yolov5s-int8.torchscript"))

            // 2750 ms en tel de fer
            // module = LiteModuleLoader.load(assetFilePath(this, "yolov5m.torchscript"))

            // 2750 ms en tel de fer
            // module = LiteModuleLoader.load(assetFilePath(this, "yolov5m-int8.torchscript"))

            BufferedReader(InputStreamReader(assets.open("classes.txt"))).use { br ->
                classes = br.readLines()
            }

            // Hilos a utilizar --> Crash, no tocar
            //PyTorchAndroid.setNumThreads(2)
        } catch (e: Exception) {
            Log.e("Object Detection", "Error loading model or classes", e)
        }
    }

    private fun assetFilePath(context: Context, assetName: String): String {
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
            )
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
    fun ObjectDetectionApp() {
        First_testTheme {

            var bitmap by remember { mutableStateOf<Bitmap?>(null) }
            var paintedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var detected by remember  { mutableStateOf(false) }

            val context = LocalContext.current

            val imagePicker =
                rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    contentResolver.openInputStream(it)?.use { inputStream ->
                        bitmap = BitmapFactory.decodeStream(inputStream)
                    }
                }
            }

            val takePictureLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success) {
                        photoUri?.let {
                            //bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
                            val auxBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
                            //processImage(bitmap, context) { result ->
                            //    className = result
                            //}
                            val matrix = Matrix().apply { postRotate(90f) }
                            bitmap = Bitmap.createBitmap(auxBitmap, 0, 0, auxBitmap.width, auxBitmap.height, matrix, true)
                            detected = false
                        }
                    } else {
                        Toast.makeText(context, "No se pudo tomar la imagen", Toast.LENGTH_SHORT).show()
                    }
                }

            val requestPermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        photoUri = createImageFileUri(context)
                        takePictureLauncher.launch(photoUri)
                    } else {
                        Toast.makeText(context, "No se pudo obtener permiso para utilizar la camara", Toast.LENGTH_SHORT).show()
                    }
                }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                        //.offset(x = 30.dp)
                ) {
                    Button(
                        onClick = {
                            imagePicker.launch("image/*")
                            detected = false
                        },
                        //modifier = Modifier.offset(x=(-10.dp))
                    ) {
                        Text(text = "Elegir \nImagen")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Button(
                            onClick = {
                                bitmap?.let {
                                    val time = measureTimeMillis {
                                        paintedBitmap = detectObjectsAndPaint(it)
                                    }
                                    detected = true
                                    Log.d("INFERENCIA", "Tomo: $time [ms]")
                                }
                            }
                        ) {
                            Text(text = "Analizar")
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, "Manifest.permission.CAMERA") -> {
                                    photoUri = createImageFileUri(context)
                                    takePictureLauncher.launch(photoUri)
                                    detected = false
                                }
                                else -> {
                                    requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            }
                        },
                        //modifier = Modifier.offset(x=(-10.dp))
                    ) {
                        Text(text = "Tomar \nFoto")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row (
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ){
                    RainbowButton ({
                        val intent = Intent(context, RealTimeDetection::class.java)
                        ContextCompat.startActivity(context, intent, null)
                    },
                        text = "LIVE"
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    RainbowButton ({
                        val intent = Intent(context, RealTimeDetectionTF::class.java)
                        ContextCompat.startActivity(context, intent, null)
                    },
                        text = "TF LIVE"
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (detected){
                    paintedBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    detected = false
                                }
                        )
                    }
                }
                else{
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val time = measureTimeMillis {
                                        paintedBitmap = detectObjectsAndPaint(it)
                                    }
                                    detected = true
                                    Log.d("INFERENCIA", "Tomo: $time [ms]")
                                }
                        )
                    }
                }
            }
        }
    }

    fun detectObjectsAndPaint(bitmap: Bitmap) : Bitmap? {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, floatArrayOf(0.0f, 0.0f, 0.0f), floatArrayOf(1.0f, 1.0f, 1.0f))
        val outputTuple = module?.forward(IValue.from(inputTensor))?.toTuple() ?: return null
        val outputTensor = outputTuple[0].toTensor()
        val outputs = outputTensor.dataAsFloatArray

        val imgScaleX = bitmap.width / 640.0f
        val imgScaleY = bitmap.height / 640.0f

        val results = outputsToNMSPredictions(outputs, imgScaleX, imgScaleY)

        return paintDetectionResults(results, bitmap)
    }

    private fun outputsToNMSPredictions(outputs: FloatArray, imgScaleX: Float, imgScaleY: Float): List<Result> {
        val results = mutableListOf<Result>()
        val numPredictions = outputs.size / 85

        for (i in 0 until numPredictions) {

            val prediction : FloatArray = outputs.sliceArray((i*85)until (85+i*85))
            val clasesScores: FloatArray = prediction.sliceArray(5 until 85)

            val score = prediction[4]

            if (score > 0.3) { //0.5

                val x = prediction[0] * imgScaleX
                val y = prediction[1] * imgScaleY
                val w = prediction[2] * imgScaleX
                val h = prediction[3] * imgScaleY

                //val classId = prediction[5].toInt()
                val classId = argmax(clasesScores)
                val left = x - w / 2
                val top = y - h / 2
                val right = x + w / 2
                val bottom = y + h / 2

                val rect = RectF(left, top, right, bottom)
                results.add(Result(classId, classes?.get(classId) ?: "Unknown", score, rect))
            }
        }

        return filterNMS(results) //* scale
    }

    private fun argmax(array: FloatArray): Int {
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

    private fun filterNMS(results: List<Result>): List<Result> {

        val sortedResults = results.sortedByDescending { it.score }
        val filteredResults = mutableListOf<Result>()
        val threshold: Float = 0.5f

        for (result in sortedResults) {
            var shouldAdd = true

            for (filteredResult in filteredResults) {
                if (result.classIndex == filteredResult.classIndex
                    && getIoU(result.rect, filteredResult.rect) > threshold)
                {
                    shouldAdd = false
                    break
                }
            }

            if (shouldAdd) {
                filteredResults.add(result)
            }
        }

        return filteredResults
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

    private fun paintDetectionResults(results: List<Result>, bitmap: Bitmap) : Bitmap {

        val scale : Float = bitmap.width / 1000f // bitmap.width = 900 ~ 3000

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            strokeWidth = 2.0f * scale
            style = Paint.Style.STROKE
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 30f * scale //30f
            style = Paint.Style.FILL
        }

        for (result in results) {
            // Cuadro
            canvas.drawRect(result.rect, paint)
            // Texto
            val text = "${result.className} (${result.score})"
            val textX = result.rect.left
            val textY = result.rect.bottom + textPaint.textSize
            canvas.drawText(text, textX, textY, textPaint)
        }

        return mutableBitmap
    }

    data class Result(val classIndex: Int, val className: String, val score: Float, val rect: RectF)

    // Cosas para poder usar la camara
    private fun createImageFileUri(context: Context): Uri {
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
}
*/