package com.example.first_test

import android.content.Context
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            module = LiteModuleLoader.load(assetFilePath(this, "yolov5s.torchscript"))
            BufferedReader(InputStreamReader(assets.open("classes.txt"))).use { br ->
                classes = br.readLines()
            }
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
    fun ObjectDetectionApp() {
        First_testTheme {

            var bitmap by remember { mutableStateOf<Bitmap?>(null) }

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
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = 30.dp)
                ) {
                    Button(
                        onClick = { imagePicker.launch("image/*") }
                    ) {
                        Text(text = "Elegir \nImagen")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { bitmap?.let { detectObjects(it) } }
                    ) {
                        Text(text = "Analizar")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, "Manifest.permission.CAMERA") -> {
                                    photoUri = createImageFileUri(context)
                                    takePictureLauncher.launch(photoUri)
                                }
                                else -> {
                                    requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            }
                        }
                    ) {
                        Text(text = "Tomar \nFoto")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            //.size(512.dp)
                            .fillMaxWidth()
                            .clickable {
                                detectObjects(it)
                            }
                    )
                }
            }
        }
    }

    private fun detectObjects(bitmap: Bitmap) {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, floatArrayOf(0.0f, 0.0f, 0.0f), floatArrayOf(1.0f, 1.0f, 1.0f))
        val outputTuple = module?.forward(IValue.from(inputTensor))?.toTuple() ?: return
        val outputTensor = outputTuple[0].toTensor()
        val outputs = outputTensor.dataAsFloatArray

        val imgScaleX = bitmap.width / 640.0f
        val imgScaleY = bitmap.height / 640.0f

        val results = outputsToNMSPredictions(outputs, imgScaleX, imgScaleY)
        //val results = outputsToNMSPredictions(outputTensor, imgScaleX, imgScaleY)
        showDetectionResults(results, bitmap)
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
        //return results
        return filterResults(results, 20.0f)
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

    fun filterResults(results: MutableList<Result>, threshold: Float): List<Result> {

        val filteredResults = mutableListOf<Result>()

        for (result in results) {
            var isTooClose = false

            for (filteredResult in filteredResults) {
                if (
                    result.classIndex == filteredResult.classIndex
                    && areRectsClose(result.rect, filteredResult.rect, threshold)
                    ) {
                    isTooClose = true
                    break
                }
            }

            if (!isTooClose) {
                filteredResults.add(result)
            }
        }

        return filteredResults
    }
    fun areRectsClose(rect1: RectF, rect2: RectF, threshold: Float): Boolean {
        val X1 = rect1.centerX()
        val Y1 = rect1.centerY()
        val X2 = rect2.centerX()
        val Y2 = rect2.centerY()

        val distance = Math.sqrt(((X2 - X1) * (X2 - X1) + (Y2 - Y1) * (Y2 - Y1)).toDouble())

        return distance < threshold
    }

    private fun showDetectionResults(results: List<Result>, bitmap: Bitmap) {
        setContent {
            First_testTheme {
                var detectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(Unit) {
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(mutableBitmap)
                    val paint = Paint().apply {
                        color = android.graphics.Color.RED
                        strokeWidth = 2.0f
                        style = Paint.Style.STROKE
                    }
                    val textPaint = Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 60f //30f
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

                    detectedBitmap = mutableBitmap
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    /*
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = 60.dp)
                    ) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { }
                        ) {
                            Text(text = "Regresar")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    */

                    detectedBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            //modifier = Modifier.size(800.dp)
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
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