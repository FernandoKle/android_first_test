package com.example.first_test

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.first_test.ml.East640Dr
import com.example.first_test.ml.RosettaDr
import com.example.first_test.ui.theme.First_testTheme
import kotlinx.coroutines.delay
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import org.tensorflow.lite.support.model.Model
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.system.measureTimeMillis

class TextDetectionOnly : ComponentActivity() {

    private data class Result(
        val score: Float,
        val rect: RectF,
        var text: String
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var listOfTextDetectionResults: List<Result>

    private lateinit var module: East640Dr
    private lateinit var processor: ImageProcessor

    private lateinit var regModule: RosettaDr
    private lateinit var regProcessor: ImageProcessor

    private val input_w = 640
    private val input_h = 640
    private val output_w: Int = input_w / 4
    private val output_h: Int = input_h / 4

    private val reg_model_w: Int = 100
    private val reg_model_h: Int = 32

    private var tokens: List<String>? = null

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ){
                    SplashScreen { showMainScreen() }
                }
            }
        }

        executor.execute(){
            try {

                val mean = floatArrayOf(103.94f, 116.78f, 123.68f)
                val stddev = floatArrayOf(1f, 1f, 1f)

                processor = ImageProcessor.Builder()
                    .add(ResizeOp(input_h, input_w, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(mean, stddev)) // 0~255 a -1~1
                    .build()

                regProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(reg_model_h, reg_model_w, ResizeOp.ResizeMethod.BILINEAR))
                    .add(TransformToGrayscaleOp())
                    // 0~255 a 0~1 ==> Hace: (valor - mean) / stddev
                    .add(NormalizeOp(127.5f, 127.5f)) // Rosetta
                    //.add(NormalizeOp(0f, 255f)) // Keras OCR
                    .build()

                Log.i("MODEL", "Cargando Modelo")
                try {
                    val builder = Model.Options.Builder()
                        .setDevice(Model.Device.GPU)
                        .setNumThreads(4)
                        .build()

                    module = East640Dr.newInstance(this, builder)
                    regModule = RosettaDr.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando GPU")

                }
                catch (e: Exception){
                    Log.e("MODEL", "Error al utilizar la GPU:", e)
                    try {
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.NNAPI)
                            .setNumThreads(4)
                            .build()

                        module = East640Dr.newInstance(this, builder)
                        regModule = RosettaDr.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando NNAPI")
                    }
                    catch (e: Exception){
                        Log.e("MODEL", "Error al utilizar NNAPI:", e)
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.CPU)
                            .setNumThreads(4)
                            .build()

                        module = East640Dr.newInstance(this, builder)
                        regModule = RosettaDr.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando CPU")
                    }

                }

                // Cargar tokens
                BufferedReader(InputStreamReader(assets.open("rosetta-tokens.txt"))).use { br ->
                    tokens = br.readLines()
                }

                // Iniciar OpenCV
                // OpenCVLoader.initLocal()

            } catch (e: Exception) {
                Log.e("Object Detection", "Error loading model", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.shutdown()
        module.close()
        regModule.close()
    }

    private fun showMainScreen() {
        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ObjectDetectionApp()
                }
            }
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
    fun SplashScreen(onComplete: () -> Unit) {
        var progress by remember { mutableFloatStateOf(0f) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            while (progress < 1f) {
                delay(50)
                progress += 0.01f
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

    @Composable
    fun ObjectDetectionApp() {
        First_testTheme {

            // NADA de corrutinas por aca :p
            // val coroutineScope = rememberCoroutineScope()

            var bitmap by remember { mutableStateOf<Bitmap?>(null) }
            var paintedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var detected by remember  { mutableStateOf(false) }
            var doneOCR by remember  { mutableStateOf(false) }
            var finalText by remember  { mutableStateOf("") }
            var isRunning by remember { mutableStateOf(false) }

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
                            val auxBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
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

                    Button(
                        onClick = {
                            val intent = Intent(context, RealTimeOCR::class.java)
                            ContextCompat.startActivity(context, intent, null)
                        },
                    ) {
                        Text(text = "LIVE")
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
                    Button(
                        onClick = {
                            bitmap?.let {

                                Toast.makeText(
                                    context,
                                    "Analizando...",
                                    Toast.LENGTH_SHORT
                                ).show()

                                isRunning = true

                                executor.execute() {
                                    val time = measureTimeMillis {
                                        paintedBitmap = detectObjectsAndPaint(it)
                                        //paintedBitmap = detectObjectsAndPaintV2(it)

                                        isRunning = false
                                    }
                                    detected = true
                                    Log.d("INFERENCIA", "Tomo: $time [ms]")
                                }

                            }
                        }
                    ) {
                        Text(text = "Detectar Texto")
                    }
                    Spacer(modifier = Modifier.width(30.dp))

                    Button(
                        onClick = {
                            bitmap?.let {

                                Toast.makeText(
                                    context,
                                    "Procesando ${listOfTextDetectionResults.size} resultados",
                                    Toast.LENGTH_SHORT
                                ).show()

                                isRunning = true

                                executor.execute(){
                                    val time = measureTimeMillis {
                                        if (listOfTextDetectionResults.isNotEmpty()){

                                            resultsToString(it)
                                            paintedBitmap = paintDetectionResultsAndText(it)

                                            finalText = ""
                                            for (result in listOfTextDetectionResults){
                                                finalText += result.text + "\n"
                                            }

                                            doneOCR = true
                                        }
                                        isRunning = false
                                    }
                                    Log.d("OCR", "Tomo: $time [ms]")
                                }
                            } ?: run {
                                Toast.makeText(context, "Primero analice una imagen", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(text = "Realizar OCR")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (isRunning){
                    Spacer(modifier = Modifier.height(30.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.padding(50.dp),
                        color = Color(255,0,255)
                    )
                }
                else{
                    if (detected){
                        paintedBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        detected = !detected
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
                                        if (paintedBitmap != null) {
                                            detected = !detected
                                        }
                                    }
                            )
                        }
                    }

                }

                // Lista de textos detectados
                Text(
                    text = finalText,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    color = Color.Green
                )
            }
        }
    }


    /*
    private fun detectObjectsAndPaintV2(bitmap: Bitmap) : Bitmap {

        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = processor.process(tensorBitmap)

        val outputsBuffer = module.process(input.tensorBuffer)

        // Salidas del modelo esperadas
        // EAST scores: (1, h/4, w/4, 1), geometry: (1, h/4, w/4, 5)
        val scores_array   = outputsBuffer.outputFeature0AsTensorBuffer.floatArray
        val geometry_array = outputsBuffer.outputFeature1AsTensorBuffer.floatArray

        // Almacenar en forma de matriz
        val scores   = Array(1) { Array(output_h) { Array(output_w) { FloatArray(1) } } }
        val geometry = Array(1) { Array(output_h) { Array(output_w) { FloatArray(5) } } }

        // Copiar los datos... Esto no se ve eficiente...
        for (i in 0 until output_h) {
            for (j in 0 until output_w) {

                for (k in 0 until 5) {
                    geometry[0][i][j][k] = geometry_array[ (i * output_w + j) * 5 + k ]
                }
                scores[0][i][j] = floatArrayOf(scores_array[ i * output_w + j ])
            }
        }

        // Hacer la transpuesta
        val scoresT =
            Array(1) { Array(1) { Array(output_h) { FloatArray(output_w) } } }
        val geometryT =
            Array(1) { Array(5) { Array(output_h) { FloatArray(output_w) } } }

        for (i in 0 until scoresT[0][0].size) {
            for (j in 0 until geometryT[0][0][0].size) {
                for (k in 0 until 1) {
                    scoresT[0][k][i][j] = scores[0][i][j][k]
                }
                for (k in 0 until 5) {
                    geometryT[0][k][i][j] = geometry[0][i][j][k]
                }
            }
        }

        // Decodificar las Bounding Boxes
        val detectedRotatedRects = ArrayList<RotatedRect>()
        val detectedConfidences = ArrayList<Float>()

        for (y in 0 until scoresT[0][0].size) {
            val detectionScoreData = scoresT[0][0][y]
            val X0Data = geometryT[0][0][y]
            val X1Data = geometryT[0][1][y]
            val X2Data = geometryT[0][2][y]
            val X3Data = geometryT[0][3][y]
            val detectionRotationAngleData = geometryT[0][4][y]

            for (x in 0 until scoresT[0][0][0].size) {
                if (detectionScoreData[x] < 0.5) {
                    continue
                }

                // Compute the rotated bounding boxes and confiences (heavily based on OpenCV example):
                // https://github.com/opencv/opencv/blob/master/samples/dnn/text_detection.py
                val offsetX = x * 4.0
                val offsetY = y * 4.0

                val h = X0Data[x] + X2Data[x]
                val w = X1Data[x] + X3Data[x]

                val angle = detectionRotationAngleData[x]
                val cos = cos(angle.toDouble())
                val sin = sin(angle.toDouble())

                val offset =
                    Point(
                        offsetX + cos * X1Data[x] + sin * X2Data[x],
                        offsetY - sin * X1Data[x] + cos * X2Data[x]
                    )
                val p1 = Point(-sin * h + offset.x, -cos * h + offset.y)
                val p3 = Point(-cos * w + offset.x, sin * w + offset.y)
                val center = Point(0.5 * (p1.x + p3.x), 0.5 * (p1.y + p3.y))

                val textDetection =
                    RotatedRect(
                        center,
                        Size(w.toDouble(), h.toDouble()),
                        (-1 * angle * 180.0 / Math.PI)
                    )
                detectedRotatedRects.add(textDetection)
                detectedConfidences.add(detectionScoreData[x])
            }
        }

        // NMS
        val detectedConfidencesMat = MatOfFloat(vector_float_to_Mat(detectedConfidences))
        val boundingBoxesMat = MatOfRotatedRect(vector_RotatedRect_to_Mat(detectedRotatedRects))
        val indicesMat = MatOfInt()

        NMSBoxesRotated(
            boundingBoxesMat,
            detectedConfidencesMat,
            0.5f,
            0.4f,
            indicesMat
        )

        // Dibujar
        val bitmapWithBoundingBoxes = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmapWithBoundingBoxes)
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            strokeWidth = 10.0f
            style = Paint.Style.STROKE
        }

        val recognitionImageHeight = reg_model_h
        val recognitionImageWidth = reg_model_w
        val ratioWidth = bitmap.width.toFloat() / input_w.toFloat()
        val ratioHeight = bitmap.height.toFloat() / input_h.toFloat()

        for (i in indicesMat.toArray()) {

            val boundingBox = boundingBoxesMat.toArray()[i]

            val targetVertices = ArrayList<Point>()

            targetVertices.add(
                Point(0.toDouble(),
                    (recognitionImageHeight - 1).toDouble()
                )
            )
            targetVertices.add(
                Point(
                    0.toDouble(),
                    0.toDouble()
                )
            )
            targetVertices.add(
                Point(
                    (recognitionImageWidth - 1).toDouble(),
                    0.toDouble()
                )
            )
            targetVertices.add(
                Point(
                    (recognitionImageWidth - 1).toDouble(),
                    (recognitionImageHeight - 1).toDouble()
                )
            )

            val srcVertices = ArrayList<Point>()

            val boundingBoxPointsMat = Mat()

            boxPoints(boundingBox, boundingBoxPointsMat)

            for (j in 0 until 4) {
                srcVertices.add(
                    Point(
                        boundingBoxPointsMat.get(j, 0)[0] * ratioWidth,
                        boundingBoxPointsMat.get(j, 1)[0] * ratioHeight
                    )
                )
                if (j != 0) {
                    canvas.drawLine(
                        (boundingBoxPointsMat.get(j, 0)[0] * ratioWidth).toFloat(),
                        (boundingBoxPointsMat.get(j, 1)[0] * ratioHeight).toFloat(),
                        (boundingBoxPointsMat.get(j - 1, 0)[0] * ratioWidth).toFloat(),
                        (boundingBoxPointsMat.get(j - 1, 1)[0] * ratioHeight).toFloat(),
                        paint
                    )
                }
            }
            canvas.drawLine(
                (boundingBoxPointsMat.get(0, 0)[0] * ratioWidth).toFloat(),
                (boundingBoxPointsMat.get(0, 1)[0] * ratioHeight).toFloat(),
                (boundingBoxPointsMat.get(3, 0)[0] * ratioWidth).toFloat(),
                (boundingBoxPointsMat.get(3, 1)[0] * ratioHeight).toFloat(),
                paint
            )

            // Recortar los rectangulos e inferir con el modelo de OCR

            val srcVerticesMat =
                MatOfPoint2f(srcVertices[0], srcVertices[1], srcVertices[2], srcVertices[3])

            val targetVerticesMat =
                MatOfPoint2f(targetVertices[0], targetVertices[1], targetVertices[2], targetVertices[3])

            val rotationMatrix = getPerspectiveTransform(srcVerticesMat, targetVerticesMat)

            val recognitionBitmapMat = Mat()
            val srcBitmapMat = Mat()

            bitmapToMat(bitmap, srcBitmapMat)

            warpPerspective(
                srcBitmapMat,
                recognitionBitmapMat,
                rotationMatrix,
                Size(recognitionImageWidth.toDouble(), recognitionImageHeight.toDouble())
            )

            val recognitionBitmap =
                Bitmap.createBitmap(
                    recognitionImageWidth,
                    recognitionImageHeight,
                    Bitmap.Config.ARGB_8888
                )

            matToBitmap(recognitionBitmapMat, recognitionBitmap)

            // Inferir con modelo de OCR

            //val recognitionTensorImage =
                //ImageUtils.bitmapToTensorImageForRecognition(
                    //recognitionBitmap,
                    //recognitionImageWidth,
                    //recognitionImageHeight,
                    //recognitionImageMean,
                    //recognitionImageStd
                //)

            //recognitionResult.rewind()
            //recognitionInterpreter.run(recognitionTensorImage.buffer, recognitionResult)

            //var recognizedText = ""
            //for (k in 0 until recognitionModelOutputSize) {
                //var alphabetIndex = recognitionResult.getInt(k * 8)
                //if (alphabetIndex in 0..alphabets.length - 1)
                    //recognizedText = recognizedText + alphabets[alphabetIndex]
            //}
            //Log.d("Recognition result:", recognizedText)
            //if (recognizedText != "") {
                //ocrResults.put(recognizedText, getRandomColor())
            //}
        }

        return bitmapWithBoundingBoxes
    }
    */

    private fun detectObjectsAndPaint(bitmap: Bitmap) : Bitmap {

        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = processor.process(tensorBitmap)

        val outputsBuffer = module.process(input.tensorBuffer)

        // Salidas del modelo esperadas
        // EAST scores: (1, h/4, w/4, 1), geometry: (1, h/4, w/4, 5)
        val scores_array   = outputsBuffer.outputFeature0AsTensorBuffer.floatArray
        val geometry_array = outputsBuffer.outputFeature1AsTensorBuffer.floatArray

        // Almacenar en forma de matriz
        val scores = Array(output_h) { FloatArray(output_w) }
        val geometry = Array(output_h) { Array(output_w) { FloatArray(5) } }

        // Copiar los datos... Esto no se ve eficiente...
        for (i in 0 until output_h) {
            for (j in 0 until output_w) {

                for (k in 0 until 5) {
                    geometry[i][j][k] = geometry_array[ (i * output_w + j) * 5 + k ]
                }
                scores[i][j] = scores_array[ i * output_w + j ]
            }
        }

        val results = processEastOutputs(
            scores,
            geometry,
            bitmap.width.toFloat() / input_w.toFloat(),
            bitmap.height.toFloat() / input_h.toFloat()
        )

        listOfTextDetectionResults = results

        return paintDetectionResults(results, bitmap)
    }

    // Procesar las salidas del modelo EAST para obtener los Rect
    private fun processEastOutputs(
        scores: Array<FloatArray>,
        geometry: Array<Array<FloatArray>>,
        rW: Float,
        rH: Float
    ): List<Result> {

        val boxes = mutableListOf<Result>()

        // Geometry:
        // d1: Distancia desde el píxel actual hasta el borde superior del texto.
        // d2: Distancia desde el píxel actual hasta el borde derecho del texto.
        // d3: Distancia desde el píxel actual hasta el borde inferior del texto.
        // d4: Distancia desde el píxel actual hasta el borde izquierdo del texto.
        // Theta: El ángulo de rotación del texto.

        // Procesar la salida del modelo EAST
        for (y in 0 until output_h) {
            for (x in 0 until output_w) {

                val score = scores[y][x]

                if (score >= 0.5f) {

                    val offsetX = x * 4.0f
                    val offsetY = y * 4.0f

                    val angle = geometry[y][x][4]
                    val cosA = cos(angle)
                    val sinA = sin(angle)

                    val h = geometry[y][x][0] + geometry[y][x][2]
                    val w = geometry[y][x][1] + geometry[y][x][3]

                    val endX = offsetX + cosA * geometry[y][x][1] + sinA * geometry[y][x][2]
                    val endY = offsetY - sinA * geometry[y][x][1] + cosA * geometry[y][x][2]

                    val startX = endX - w
                    val startY = endY - h

                    boxes.add(Result(
                        score = score,
                        rect = RectF(
                            startX * rW,
                            startY * rH,
                            (endX) * rW,
                            (endY) * rH,
                            ),
                        text = ""
                        )
                    )
                }
            }
        }

        Log.d("MODELO", "${boxes.size} potenciales predicciones")

        val filteredBoxes = filterNMS(boxes)

        Log.d("MODELO", "${filteredBoxes.size} predicciones restantes")
        Log.d("MODELO", "Escalando boxes por rW: $rW, rH: $rH")

        return filteredBoxes
    }

    private fun filterNMS(results: List<Result>): List<Result> {

        val sortedResults = results.sortedByDescending { it.score }
        val filteredResults = mutableListOf<Result>()
        val threshold: Float = 0.4f

        for (result in sortedResults) {
            var shouldAdd = true

            for (filteredResult in filteredResults) {
                if ( getIoU(result.rect, filteredResult.rect) > threshold )
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

        val scale : Float = bitmap.width / 1000f

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            strokeWidth = 2.0f * scale
            style = Paint.Style.STROKE
        }

        Log.d("DIBUJANDO", "Bitmap w: ${bitmap.width}, h: ${bitmap.height}")

        for (result in results) {
            canvas.drawRect(result.rect, paint)
        }

        return mutableBitmap
    }

    private fun paintDetectionResultsAndText(bitmap: Bitmap) : Bitmap {

        val scale : Float = bitmap.width / 1000f

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

        for (result in listOfTextDetectionResults) {

            canvas.drawRect(result.rect, paint)

            val text = result.text
            val textX = result.rect.left
            val textY = result.rect.bottom + textPaint.textSize

            canvas.drawText(text, textX, textY, textPaint)
        }

        return mutableBitmap
    }

    // Cosas para poder usar la camara...
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

    private fun resultsToString(bitmap: Bitmap){
        for (result in listOfTextDetectionResults){
            val regBitmap = cropBitmap(bitmap, result.rect)
            result.text = doOCR(regBitmap)
        }
    }

    fun cropBitmap(bitmap: Bitmap, rect: RectF): Bitmap {

        val offset = 20 //bitmap.width / 1000f

        val left = (rect.left - offset).toInt().coerceAtLeast(0)
        val top = (rect.top - offset).toInt().coerceAtLeast(0)
        val width = (rect.width() + offset).toInt().coerceAtMost(bitmap.width - left)
        val height = (rect.height() + offset).toInt().coerceAtMost(bitmap.height - top)

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    fun doOCR(bitmap: Bitmap) : String {

        // Inferir
        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = regProcessor.process(tensorBitmap)

        val outputsBuffer = regModule.process(input.tensorBuffer)

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

            finalText += tokens?.get(tokenId) ?: ""
        }

        Log.d("OCR", "Detecto: \"${finalText}\"")

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


}

//private fun org.opencv.core.Rect.toRect(): android.graphics.Rect {
//    return android.graphics.Rect( x, y, x + width, y + height )
//}
