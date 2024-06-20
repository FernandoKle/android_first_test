package com.example.first_test

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.first_test.ModelActivity.assetFilePath
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

//import org.tensorflow.lite.task.core.BaseOptions
//import org.tensorflow.lite.task.vision.detector.Detection
//import org.tensorflow.lite.task.vision.detector.ObjectDetector

data class Result(val classIndex: Int, val className: String, val score: Float, val rect: RectF)
class RealTimeDetection : ComponentActivity() {

    private var module: Module? = null
    private var classes: List<String>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp()
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

    @Composable
    fun MyApp() {
        val context = LocalContext.current
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var hasStarted by remember { mutableStateOf(false) }

        val requestPermissionLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(context, "No se pudo obtener permiso para utilizar la camara", Toast.LENGTH_SHORT).show()
                }
            }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (hasStarted){
                CameraPreview({ newBitmap ->
                    /** Aca va el procesamiento de la imagen **/

                    val time = measureTimeMillis {

                        // Mantener la original
                        // bitmap = newBitmap

                        // Pintar un cuadro
                        // bitmap = paintParty(newBitmap)

                        // INFERIR - oh boy...
                        bitmap = detectObjectsAndPaint(newBitmap)

                    }

                    Log.d("IMAGEN_MODIFICADA", "Tiempo de inferencia: $time [ms]")
                },
                    isPreviewVisible = false,
                    //modifier = Modifier.height(300.dp).fillMaxWidth()
                    modifier = Modifier.size(1.dp)
                )

            }

            if (!hasStarted){
                Button(
                    onClick = {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                hasStarted = true
                                Log.d("INFORMACION", "Presiono EL boton")
                            }
                            else -> {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                ) {
                    Text(text = "CLICK ME!")
                }
            }

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            //hasStarted = false
                            Log.d("INFORMACION", "TOCO la imagen")
                        }
                )
            }

        }
        Log.d("INFORMACION", "Bucle de MAIN UI")
        // END MAIN UI
    }

    @Composable
    fun CameraPreview(
        onBitmapReady: (Bitmap) -> Unit,
        isPreviewVisible: Boolean = true,
        modifier: Modifier
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    clipToOutline = true
                    background = ContextCompat.getDrawable(ctx, R.drawable.rounded_corner)
                    visibility = View.INVISIBLE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({

                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    //val preview = Preview.Builder().build().also {
                    //    it.setSurfaceProvider(previewView.surfaceProvider)
                    //}

                    val imageAnalysis = ImageAnalysis.Builder()
                        //.setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        //.setTargetResolution(Size(640, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalysis.Analyzer { image ->
                        val bitmap = image.toBitmap().rotateBitmap(image.imageInfo.rotationDegrees)

                        onBitmapReady(bitmap)

                        image.close()
                    })

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()

                        if (isPreviewVisible){
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        }
                        else{
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                imageAnalysis
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            //modifier = Modifier.fillMaxSize()
            modifier = modifier
        )
    }

    fun Bitmap.rotateBitmap(angle: Int): Bitmap { //source: Bitmap,
        val matrix = Matrix().apply {
            postRotate(-angle.toFloat())
            postScale(-1f,-1f)
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
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

    private fun outputsToNMSPredictions(outputs: FloatArray, imgScaleX: Float, imgScaleY: Float): List<YoloActivity.Result> {
        val results = mutableListOf<YoloActivity.Result>()
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
                results.add(
                    YoloActivity.Result(
                        classId,
                        classes?.get(classId) ?: "Unknown",
                        score,
                        rect
                    )
                )
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

    private fun filterNMS(results: List<YoloActivity.Result>): List<YoloActivity.Result> {

        val sortedResults = results.sortedByDescending { it.score }
        val filteredResults = mutableListOf<YoloActivity.Result>()
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

    private fun paintDetectionResults(results: List<YoloActivity.Result>, bitmap: Bitmap) : Bitmap {

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

    private fun paintParty(input: Bitmap) : Bitmap {

        val scale : Float = input.width / 1000f // bitmap.width = 900 ~ 3000

        Log.d("Recibio imagen", "w: ${input.width}, h: ${input.height}, scale: $scale")

        val mutableBitmap = input.copy(Bitmap.Config.ARGB_8888, true)
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

        val cuadro = RectF(100f,100f,100f,100f)

        canvas.drawRect(cuadro, paint)
        // Texto
        val text = "Apunta el numero aca !"
        val textX = cuadro.left
        val textY = cuadro.bottom + textPaint.textSize
        canvas.drawText(text, textX, textY, textPaint)

        return mutableBitmap
    }

    // END
}
