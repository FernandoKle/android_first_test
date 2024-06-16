package com.example.first_test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
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
            val imagePicker =
                rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    contentResolver.openInputStream(it)?.use { inputStream ->
                        bitmap = BitmapFactory.decodeStream(inputStream)
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                Button(
                    onClick = { imagePicker.launch("image/*") }
                ) {
                    Text(text = "Elegir Imagen")
                }
                Button(
                    onClick = { bitmap?.let { detectObjects(it) } }
                ) {
                    Text(text = "Analizar")
                }

                Spacer(modifier = Modifier.height(16.dp))

                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(512.dp)
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

    fun outputsToNMSPredictions(outputs: FloatArray, imgScaleX: Float, imgScaleY: Float): List<Result> {
        val results = mutableListOf<Result>()
        val numPredictions = outputs.size / 85

        for (i in 0 until numPredictions) {

            val prediction : FloatArray = outputs.sliceArray((i*85)until (85+i*85))
            val clasesScores: FloatArray = prediction.sliceArray(5 until 85)

            val x = prediction[0] * imgScaleX
            val y = prediction[1] * imgScaleY
            val w = prediction[2] * imgScaleX
            val h = prediction[3] * imgScaleY

            val score = prediction[4]


            if (score > 0.3) { //0.5
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
        return results
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

                    for (result in results) {
                        canvas.drawRect(result.rect, paint)
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
}