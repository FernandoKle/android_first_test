package com.example.first_test
/*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.first_test.ml.Yolov5nFp16
import com.example.first_test.ui.theme.First_testTheme
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.model.Model
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.system.measureTimeMillis

//import org.tensorflow.lite.task.core.BaseOptions
//import org.tensorflow.lite.task.vision.detector.Detection
//import org.tensorflow.lite.task.vision.detector.ObjectDetector

class RealTimeDetectionTF : ComponentActivity(), SensorEventListener {

    private data class Result(
        val classIndex: Int,
        val className: String,
        val score: Float,
        val rect: RectF
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    //private lateinit var module: Yolov5sFp16NoNms
    private lateinit var module: Yolov5nFp16

    private var classes: List<String>? = null
    private lateinit var processor: ImageProcessor

    private lateinit var sensorManager: SensorManager
    private var sensorAccel: Sensor? = null
    private var accel = floatArrayOf(0f , 0f , 0f) // [ M / s^2 ]
    private var gravity = floatArrayOf(0f , 0f , 0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SplashScreen ({
                        showMainScreen()
                    },
                        speed = 0.03f
                    )
                }
            }
        }

        executor.execute(){
            try {
                /** Initialization */

                processor = ImageProcessor.Builder()
                    .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                    .add(NormalizeOp(0f, 255f)) // 0~255 a 0~1 ==> Hace: (valor - mean) / stddev
                    .build()

                // CPU y yolov5s F16
                // 820 ms con 5 hilos y 900 con 4
                // en tel de fer
                // CPU y yolov5n F16
                /// 500 ms tel de fer
                Log.i("MODEL", "Cargando Modelo")
                try {
                    val builder = Model.Options.Builder()
                        .setDevice(Model.Device.GPU)
                        .setNumThreads(4)
                        .build()

                    module = Yolov5nFp16.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando GPU")
                }
                catch (e: Exception){
                    Log.e("MODEL", "Error al utilizar la GPU:", e)
                    try {
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.NNAPI)
                            .setNumThreads(4)
                            .build()

                        module = Yolov5nFp16.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando NNAPI")
                    }
                    catch (e: Exception){
                        Log.e("MODEL", "Error al utilizar NNAPI:", e)
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.CPU)
                            .setNumThreads(4)
                            .build()

                        module = Yolov5nFp16.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando CPU")
                    }

                }

                // Cargar clases
                BufferedReader(InputStreamReader(assets.open("classes.txt"))).use { br ->
                    classes = br.readLines()
                }
            }
            catch (e: Exception) {
                Log.e("Object Detection", "Error loading model or classes", e)
            }

            try {
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                sensorAccel = if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                    // Success! There's an accelerometer.
                    Log.d("SENSOR", "Accelerometro iniciado")
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                }
                else {
                    // Failure! No accelerometer.
                    null
                }

            }
            catch (e: Exception) {
                Log.e("SENSOR", "Error inicializando sensores", e)
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.shutdown()
        module.close()
    }

    override fun onSensorChanged(event: SensorEvent) {
        /** FILTRO Pasa altos **/
        // alpha is calculated as t / (t + dT)
        // with t, the low-pass filter's time-constant
        // and dT, the event delivery rate

        val alpha = 0.8f

        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        accel[0] = event.values[0] - gravity[0]
        accel[1] = event.values[1] - gravity[1]
        accel[2] = event.values[2] - gravity[2]

        // Sin filtro

        //accel[0] = event.values[0]
        //accel[1] = event.values[1]
        //accel[2] = event.values[2]

        //Log.d("SENSOR", "Accelerometro actualizado")
    }

    // Cambio de precision
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    /** https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview */
    override fun onResume() {
        super.onResume()
        sensorAccel?.also { light ->
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

// @=============== START MAIN UI ===============@

    private fun showMainScreen() {
        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyApp()
                }
            }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray)
        ) {

            if (hasStarted){
                CameraPreview({ newBitmap ->
                    /** Aca la funcion de procesamiento de la imagen **/

                    val time = measureTimeMillis {

                        // Mantener la original
                        // bitmap = newBitmap

                        // Pintar un cuadro
                        // bitmap = paintParty(newBitmap)

                        // INFERIR con el modelo
                        bitmap = detectObjectsAndPaint(newBitmap)

                    }

                    Log.d("IMAGEN_MODIFICADA", "Tiempo de procesamiento: $time [ms]")
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
        //Log.d("INFORMACION", "Bucle de MAIN UI")

// @=============== END MAIN UI ===============@
    }

    @Composable
    fun CameraPreview(
        onBitmapReady: (Bitmap) -> Unit,
        isPreviewVisible: Boolean = true,
        modifier: Modifier
    ) {
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

    private fun detectObjectsAndPaint(bitmap: Bitmap) : Bitmap {

        // No procesar si el dispositivo se esta moviendo
        val accelMag = kotlin.math.sqrt(accel[0].pow(2) + accel[1].pow(2) + accel[2].pow(2))
        Log.d("SENSOR", "accelMag: $accelMag [M/s^2]")
        if (accelMag >= 0.25){
            return bitmap
        }

        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = processor.process(tensorBitmap)

        val outputsBuffer = module.process(input.tensorBuffer)

        val outputs = outputsBuffer.outputFeature0AsTensorBuffer.floatArray

        val imgScaleX = bitmap.width * 1.0f
        val imgScaleY = bitmap.height * 1.0f

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

                val classId = argmax(clasesScores)
                val left = x - w / 2
                val top = y - h / 2
                val right = x + w / 2
                val bottom = y + h / 2

                val rect = RectF(left, top, right, bottom)
                results.add(
                    Result(
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

    private fun filterNMS(results: List<Result>): List<Result> {

        val sortedResults = results.sortedByDescending { it.score }
        val filteredResults = mutableListOf<Result>()
        val threshold = 0.5f

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

    // END
}
*/