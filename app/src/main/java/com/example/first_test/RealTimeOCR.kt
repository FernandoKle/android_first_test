package com.example.first_test

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.util.Size
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
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.first_test.ml.RosettaDr
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import org.tensorflow.lite.support.model.Model
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.system.measureTimeMillis

//import org.tensorflow.lite.task.core.BaseOptions
//import org.tensorflow.lite.task.vision.detector.Detection
//import org.tensorflow.lite.task.vision.detector.ObjectDetector

class RealTimeOCR : ComponentActivity(), SensorEventListener {

    private lateinit var module: RosettaDr
    // private lateinit var module: KerasOcrDrNoCtc

    private var tokens: List<String>? = null

    private val input_w = 100 // 100
    private val input_h = 32 // 32

    private lateinit var processor: ImageProcessor

    private lateinit var sensorManager: SensorManager
    private var sensorAccel: Sensor? = null
    private var accel = floatArrayOf(0f , 0f , 0f) // [ M / s^2 ]
    private var gravity = floatArrayOf(0f , 0f , 0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp()
        }

        try {
            /** Initialization */

            processor = ImageProcessor.Builder()
                .add(ResizeOp(input_h, input_w, ResizeOp.ResizeMethod.BILINEAR))
                .add(TransformToGrayscaleOp())
                // 0~255 a 0~1 ==> Hace: (valor - mean) / stddev
                .add(NormalizeOp(127.5f, 127.5f)) // Rosetta
                //.add(NormalizeOp(0f, 255f)) // Keras OCR
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

                module = RosettaDr.newInstance(this, builder)

                Log.i("MODEL", "Utilizando GPU")
            }
            catch (e: Exception){
                Log.e("MODEL", "Error al utilizar la GPU:", e)
                try {
                    val builder = Model.Options.Builder()
                        .setDevice(Model.Device.NNAPI)
                        .setNumThreads(4)
                        .build()

                    module = RosettaDr.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando NNAPI")
                }
                catch (e: Exception){
                    Log.e("MODEL", "Error al utilizar NNAPI:", e)
                    val builder = Model.Options.Builder()
                        .setDevice(Model.Device.CPU)
                        .setNumThreads(4)
                        .build()

                    module = RosettaDr.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando CPU")
                }

            }

            // Cargar tokens
            BufferedReader(InputStreamReader(assets.open("rosetta-tokens.txt"))).use { br ->
                tokens = br.readLines()
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

    override fun onDestroy() {
        super.onDestroy()

        module.close()
    }

    override fun onSensorChanged(event: SensorEvent) {
        /** FILTRO Pasa altos **/
        // alpha is calculated as t / (t + dT)
        // with t, the low-pass filter's time-constant
        // and dT, the event delivery rate

        val alpha = 0.8f;

        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        accel[0] = event.values[0] - gravity[0];
        accel[1] = event.values[1] - gravity[1];
        accel[2] = event.values[2] - gravity[2];

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
    @Composable
    fun MyApp() {
        val context = LocalContext.current
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var zoomBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var detectedText by remember { mutableStateOf<String>("") }
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

                        // No procesar si el dispositivo se esta moviendo
                        val accelMag = kotlin.math.sqrt(
                            accel[0].pow(2) + accel[1].pow(2) + accel[2].pow(2))

                        Log.d("SENSOR", "accelMag: $accelMag [M/s^2]")

                        if (accelMag < 0.25){

                            // Recorte
                            val w = newBitmap.width
                            val h = newBitmap.height

                            val input_w = input_w * 2
                            val input_h = input_h * 2

                            val x = ( w/2 - input_w/2 ).toInt()
                            val y = ( 0.2f * h ).toInt()

                            zoomBitmap = Bitmap.createBitmap(
                                newBitmap, x, y, input_w, input_h)

                            //Log.d("OCR", "recorte: w: ${zoomBitmap?.width} h: ${zoomBitmap?.height}")

                            // INFERIR con el modelo
                            detectedText = doOCR(zoomBitmap ?: newBitmap)

                            Log.d("OCR", "detecto: $detectedText")
                        }

                        // Pintar un cuadro
                        bitmap = paintStaticRect(newBitmap, detectedText)
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
                        .clip(RoundedCornerShape(16.dp))
                        .fillMaxWidth()
                        .clickable {
                            //hasStarted = false
                            Log.d("INFORMACION", "TOCO la imagen")
                        }
                )
            }

            zoomBitmap?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ZOOM",
                    color = Color.Green
                )
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .fillMaxWidth()
                        .clickable {
                            //hasStarted = false
                            Log.d("INFORMACION", "TOCO la imagen")
                        }
                )
            }

            Text(
                text = "Texto Detectado: $detectedText",
                color = Color.Green
            )

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

                    val screenSize = Size(200,64)//if (rotation == 0) Size(720, 1280) else Size(1280, 720)
                    val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(screenSize,
                        ResolutionStrategy.FALLBACK_RULE_NONE)
                    ).build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        //.setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        //.setTargetResolution(Size(640, 640))
                        //.setResolutionSelector(resolutionSelector)
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

    fun doOCR(bitmap: Bitmap) : String {

        // Inferir
        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = processor.process(tensorBitmap)

        val outputsBuffer = module.process(input.tensorBuffer)

        val outputs = outputsBuffer.outputFeature0AsTensorBuffer.floatArray

        // Procesar resultados
        return output2string(outputs)
    }

    private fun output2string(outputs: FloatArray): String {

        var finalText: String = ""
        val numTokens = tokens?.size ?: 37
        val numChars = outputs.size / numTokens

        for (i in 0 until numChars) {

            val prediction : FloatArray =
                outputs.sliceArray((i*numTokens)until (numTokens+i*numTokens))

            val tokenId = argmax(prediction)

            //if ( prediction[tokenId] > 0.3 ) {
            finalText += tokens?.get(tokenId) ?: ""
            //}
        }

        return finalText
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

    private fun paintStaticRect(input: Bitmap, detectedText: String = "") : Bitmap {

        val w = input.width
        val h = input.height
        val scale : Float = w / 1000f // bitmap.width = 900 ~ 3000

        Log.d("Recibio imagen", "w: ${input.width}, h: ${input.height}, scale: $scale")

        val mutableBitmap = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            strokeWidth = 4.0f * scale
            style = Paint.Style.STROKE
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 35f * scale //30f
            style = Paint.Style.FILL
        }

        val input_w = input_w * 2
        val input_h = input_h * 2

        val x = ( w/2 - input_w/2 ).toFloat()
        val y = 0.2f * h
        val cuadro = RectF(x, y, x + input_w, y + input_h)

        canvas.drawRect(cuadro, paint)
        // Texto
        val text = "Detecto: $detectedText" // "Apunta el numero aca !"
        val textX = cuadro.left
        val textY = cuadro.bottom + textPaint.textSize
        canvas.drawText(text, textX, textY, textPaint)

        return mutableBitmap
    }

    // END
}
