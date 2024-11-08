package com.example.first_test
/*

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.first_test.ml.BestFp16
import com.example.first_test.ml.Cuadro
import com.example.first_test.ml.RosettaDr
import com.example.first_test.ui.theme.First_testTheme
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import org.tensorflow.lite.support.model.Model
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.LongBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

class OnnxYoloOCR : ComponentActivity() {

    private data class Result(
        val score: Float,
        val classIndex: Int,
        val rect: RectF,
        var text: String
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var listOfTextDetectionResults: List<Result>

    private lateinit var module: Cuadro
    private lateinit var processor: ImageProcessor

    private lateinit var regModule: RosettaDr
    private lateinit var regProcessor: ImageProcessor

    private var classes = List<String>(2){"Analogico"; "Digital"}
    private val input_w = 640
    private val input_h = 640

    private val reg_model_w: Int = 100
    private val reg_model_h: Int = 32

    private var tokens: List<String>? = null

    private var photoUri: Uri? = null

    private var ortEnv: OrtEnvironment? = null
    private var ortS: OrtSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ){
                    SplashScreen ({
                        showMainScreen()
                    },
                        speed = 0.01f
                    )
                }
            }
        }

        executor.execute(){
            try {

                val mean = floatArrayOf(103.94f, 116.78f, 123.68f)
                val stddev = floatArrayOf(1f, 1f, 1f)

                processor = ImageProcessor.Builder()
                    .add(ResizeOp(input_h, input_w, ResizeOp.ResizeMethod.BILINEAR))
                    //.add(NormalizeOp(mean, stddev)) // 0~255 a -1~1
                    .add(NormalizeOp(0f, 255f)) // 0~255 a 0~1 ==> Hace: (valor - mean) / stddev
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

                    module = Cuadro.newInstance(this, builder)
                    //regModule = RosettaDr.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando GPU")

                }
                catch (e: Exception){
                    Log.e("MODEL", "Error al utilizar la GPU:", e)
                    try {
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.NNAPI)
                            .setNumThreads(4)
                            .build()

                        module = Cuadro.newInstance(this, builder)
                        //regModule = RosettaDr.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando NNAPI")
                    }
                    catch (e: Exception){
                        Log.e("MODEL", "Error al utilizar NNAPI:", e)
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.CPU)
                            .setNumThreads(4)
                            .build()

                        module = Cuadro.newInstance(this, builder)
                        //regModule = RosettaDr.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando CPU")
                    }

                }

                // Onnx
                ortEnv = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL)
                val so = SessionOptions()

                so.setIntraOpNumThreads(4)
                so.addNnapi()

                ortS = ortEnv?.createSession(
                    //resources.openRawResource(R.raw.reg_model_dr).readBytes(),
                    resources.openRawResource(R.raw.reg_model).readBytes(),
                    so
                )

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

    private fun detectObjectsAndPaint(bitmap: Bitmap) : Bitmap {


        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = processor.process(tensorBitmap)

        val outputsBuffer = module.process(input.tensorBuffer)

        val outputs = outputsBuffer.outputFeature0AsTensorBuffer.floatArray

        val imgScaleX = bitmap.width * 1.0f
        val imgScaleY = bitmap.height * 1.0f

        val results = outputsToNMSPredictions(outputs, imgScaleX, imgScaleY)

        listOfTextDetectionResults = results

        return paintDetectionResults(results, bitmap)
    }

    private fun outputsToNMSPredictions(outputs: FloatArray, imgScaleX: Float, imgScaleY: Float): List<Result> {
        val results = mutableListOf<Result>()
        val outSize = 7
        val numPredictions = outputs.size / outSize

        for (i in 0 until numPredictions) {

            val prediction : FloatArray = outputs.sliceArray((i*outSize)until (outSize+i*outSize))
            val clasesScores: FloatArray = prediction.sliceArray(5 until outSize)

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
                        classIndex = classId,
                        score = score,
                        rect = rect,
                        text = ""
                    )
                )
            }
        }

        return filterNMS(results) //* scale
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

        val offset = 20 //20 //bitmap.width / 1000f

        val left = (rect.left - offset).toInt().coerceAtLeast(0)
        val top = (rect.top - offset).toInt().coerceAtLeast(0)
        val width = (rect.width() + offset).toInt().coerceAtMost(bitmap.width - left)
        val height = (rect.height() + offset).toInt().coerceAtMost(bitmap.height - top)

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    //private fun preprocessImage(bitmap: Bitmap) : FloatBuffer {
//
        //val w = reg_model_w
        //val h = reg_model_h
        //val imgData = FloatBuffer.allocate(1 * w * h * 1)
        //imgData.rewind()
//
        //val bmpData = IntArray(reg_model_w * reg_model_h)
        //bitmap.getPixels(bmpData, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
//
        //var idx = 0
        //for (i in 0..reg_model_w - 1) {
            //for (j in 0..reg_model_h - 1) {
                //val pixelValue = bmpData[idx++]
                //imgData.put(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                //imgData.put(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                //imgData.put(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            //}
        //}
//
        //return imgData ;
    //}

    private fun doOCR(bitmap: Bitmap) : String {

        // Inferir
        val tensorBitmap = TensorImage.fromBitmap(bitmap)
        val input = regProcessor.process(tensorBitmap)

        //while (ortS?.inputNames?.iterator()?.hasNext() == true){
        //    Log.d("INFERENCIA", "Input: ${ortS?.inputNames?.iterator()?.next()}")
        //}

        val inputNameImg = "input" //ortS?.inputNames?.iterator()?.next()
        val inputNameText = "text" //"onnx::Gather_1" //ortS?.inputNames?.iterator()?.next()
        val shapeImg = longArrayOf(1, 1, reg_model_h.toLong(), reg_model_w.toLong())
        val shapeText = longArrayOf(1, 26)

        Log.d("INFERENCIA", "Input 1: ${inputNameImg}")
        Log.d("INFERENCIA", "Input 2: ${inputNameText}")

        ortEnv.use {

            val inputTensorImg = OnnxTensor.createTensor(ortEnv,
                input.buffer.asFloatBuffer(), shapeImg)

            val inputTensorText = OnnxTensor.createTensor(ortEnv,
                LongBuffer.allocate(26), shapeText)

            inputTensorImg.use {

                var output: OrtSession.Result? = null
                try {
                    output = ortS?.run(
                        mapOf(
                            inputNameImg to inputTensorImg,
                            inputNameText to inputTensorText
                        )
                    )
                }
                catch (e: Exception) {
                    Log.e("INFERENCIA", "Error:", e)
                }

                output?.use {
                    @Suppress("UNCHECKED_CAST")
                    val pred = ((output.get(0)?.value) as Array<Array<FloatArray>>)[0]
                    return output2stringV2(pred)
                }
            }
        }

        // Procesar resultados
        //return output2string(out_str)
        return ""
    }

    private fun output2stringV2(outputs: Array<FloatArray>): String {

        var finalText: String = ""

        for (i in 0 until outputs.size) {

            val tokenId = argmax(outputs[i])

            finalText += tokens?.get(tokenId) ?: ""
        }

        Log.d("OCR", "Detecto: \"${finalText}\"")

        return finalText
    }

    private fun output2string(outputs: FloatArray): String {

        var finalText: String = ""
        val numTokens = tokens?.size ?: 38 //37
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

}
 */