package com.example.first_test

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.first_test.ml.NumAn
import com.example.first_test.ml.NumDig
import com.example.first_test.ui.theme.First_testTheme
import org.opencv.android.OpenCVLoader
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.model.Model
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

class YoloOCR : ComponentActivity(), SensorEventListener {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var listOfTextDetectionResults: List<Result>

    private lateinit var module: NumAn
    private lateinit var moduleDig: NumDig
    private lateinit var processor: ImageProcessor
    private lateinit var rotProcessor: ImageProcessor

    private var classes = listOf(",", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    private val input_w = 640
    private val input_h = 640

    private var analogScore = 0f
    private var digitalScore = 0f
    private var isLastAnalog = false

    private var photoUri: Uri? = null

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)


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
                        speed = 0.04f
                    )
                }
            }
        }

        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            /*
            sensorGiro = if (sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null) {
                // Success! There's an accelerometer.
                Log.d("SENSOR", "Giroscopio iniciado")
                sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            }
            else {
                // Failure! No sensor.
                Log.e("SENSOR", "No inicio el sensor")
                null
            }
             */
        }
        catch (e: Exception) {
            Log.e("SENSOR", "Error inicializando sensores", e)
        }

        executor.execute(){
            try {

                processor = ImageProcessor.Builder()
                    .add(ResizeOp(input_h, input_w, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(0f, 255f)) // 0~255 a 0~1 ==> Hace: (valor - mean) / stddev
                    .build()

                rotProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(128, 128, ResizeOp.ResizeMethod.BILINEAR))
                    //.add(NormalizeOp(0f, 255f)) // 0~255 a 0~1 ==> Hace: (valor - mean) / stddev
                    .build()

                Log.i("MODEL", "Cargando Modelo")
                try {
                    val builder = Model.Options.Builder()
                        .setDevice(Model.Device.GPU)
                        .setNumThreads(4)
                        .build()

                    module = NumAn.newInstance(this, builder)
                    moduleDig = NumDig.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando GPU")

                }
                catch (e: Exception){
                    Log.e("MODEL", "Error al utilizar la GPU:", e)
                    try {
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.NNAPI)
                            .setNumThreads(4)
                            .build()

                        module = NumAn.newInstance(this, builder)
                        moduleDig = NumDig.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando NNAPI")
                    }
                    catch (e: Exception){
                        Log.e("MODEL", "Error al utilizar NNAPI:", e)
                        val builder = Model.Options.Builder()
                            .setDevice(Model.Device.CPU)
                            .setNumThreads(4)
                            .build()

                        module = NumAn.newInstance(this, builder)
                        moduleDig = NumDig.newInstance(this, builder)

                        Log.i("MODEL", "Utilizando CPU")
                    }

                }

                // Iniciar OpenCV
                OpenCVLoader.initLocal()

            } catch (e: Exception) {
                Log.e("Object Detection", "Error loading model", e)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            //Log.d("SENSOR", "Accelerometro actualizado")

        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            //Log.d("SENSOR", "Magnetic Field actualizado")
        }
        //Log.d("SENSOR", "Accelerometro actualizado")
        //updateOrientationAngles()
        //Log.d("SENSOR", "Orientacion: ${orientationAngles[0]},${orientationAngles[1]},${orientationAngles[2]}")
        //Log.d("SENSOR", "Rotacion: ${rotationMatrix[0]},${rotationMatrix[1]},${rotationMatrix[2]}")
    }

    fun updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        // "rotationMatrix" now has up-to-date information.
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // "orientationAngles" now has up-to-date information.
    }


    // Cambio de precision
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    override fun onResume() {
        super.onResume()

        /*
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        */

    }

    override fun onPause() {
        super.onPause()
        //sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.shutdown()
        module.close()
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

    fun checkPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, "Manifest.permission.CAMERA") == PackageManager.PERMISSION_GRANTED
        //return ContextCompat.checkSelfPermission(context, "Manifest.permission.CAMERA") == PackageManager.PERMISSION_GRANTED &&
        //        ContextCompat.checkSelfPermission(context, "Manifest.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED
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
                        val auxBitmap = BitmapFactory.decodeStream(inputStream)
                        val rot = getImageRotation(it, context)
                        val matrix = Matrix().apply { postRotate(rot) }
                        bitmap = Bitmap.createBitmap(auxBitmap, 0, 0, auxBitmap.width, auxBitmap.height, matrix, true)
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
                            val rot = getImageRotation(it, context)

                            //updateOrientationAngles()
                            //val ori = orientationAngles[2]
                            //Log.d("SENSOR", "Orientacion: ${orientationAngles}")
                            //Log.d("SENSOR", "Rotacion: ${rotationMatrix}")

                            val matrix = Matrix().apply { postRotate(rot) }

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
                        //photoUri = createImageFileUri(context)
                        //takePictureLauncher.launch(photoUri)
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

                    /** Test **/
                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            val data = result.data
                            data?.let {
                                if (it.extras?.get("data") != null) {
                                    // Si la imagen viene de la cámara
                                    bitmap = it.extras?.get("data") as Bitmap
                                    detected = false
                                } else if (it.data != null) {
                                    // Si la imagen viene de la galería
                                    photoUri = it.data
                                }
                            }
                            photoUri?.let {
                                val auxBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
                                val rot = getImageRotation(it, context)
                                val matrix = Matrix().apply { postRotate(rot) }
                                bitmap = Bitmap.createBitmap(auxBitmap, 0, 0, auxBitmap.width, auxBitmap.height, matrix, true)
                                detected = false
                            }
                            Log.d("IMAGEN", "h: ${bitmap?.height}, w: ${bitmap?.width}")
                        }
                    }
                    Button(
                        onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, "android.permission.CAMERA") -> {
                                    Log.d("IMAGEN", "Hay Permiso de camara")
                                    openImageChooser(context, launcher) { uri ->
                                        photoUri = uri
                                    }
                                }
                                else -> {
                                    requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            }
                        },
                        //modifier = Modifier.offset(x=(-10.dp))
                    ) {
                        Icon(Icons.Rounded.Image, "Piss off")
                    }
                    /** END Test **/

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
                            //val intent = Intent(context, RealTimeOCR::class.java)
                            //ContextCompat.startActivity(context, intent, null)
                            Toast.makeText(context, "Ya no hay...", Toast.LENGTH_SHORT).show()
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

                                    if (isLastAnalog){
                                        finalText = doOCR(listOfTextDetectionResults)
                                    }
                                    else {
                                        finalText = doOCRDigital(listOfTextDetectionResults)
                                    }
                                    //paintedBitmap = paintDetectionResultsAndText(it)

                                    doneOCR = true
                                    isRunning = false
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

    fun openImageChooser(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        onCameraUriCreated: (Uri) -> Unit
    ) {
        // Crear un archivo temporal para la foto
        /*
        val photoFile = createImageFile(context)
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        */
        val photoUri = createImageFileUri(context)

        Log.d("IMAGEN", "algo")
        onCameraUriCreated(photoUri)

        // Intent para seleccionar de la galería
        val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        // Intent para la cámara
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }

        // Crear un Intent Chooser para que el usuario elija entre cámara o galería
        val chooserIntent = Intent.createChooser(pickPhotoIntent, "Selecciona una imagen o toma una foto")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent, pickPhotoIntent))

        Log.d("IMAGEN", "Ejecutando chooser")
        launcher.launch(chooserIntent)
    }

    fun createImageFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = context.getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }

    private fun detectObjectsAndPaint(bitmap: Bitmap) : Bitmap {


        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = processor.process(tensorBitmap)

        val outputsBufferAn = module.process(input.tensorBuffer)
        val outputsBufferDig = moduleDig.process(input.tensorBuffer)

        val outputsAn = outputsBufferAn.outputFeature0AsTensorBuffer.floatArray
        val outputsDig = outputsBufferDig.outputFeature0AsTensorBuffer.floatArray
        //var inputArray = Array(3){Array(640){Array(640){FloatArray(3)}}}
        //inputArray[0] = outputsAn as Array<Array<FloatArray>>

        val outputs = selectMaxScore(outputsAn, outputsDig)

        val imgScaleX = bitmap.width * 1.0f
        val imgScaleY = bitmap.height * 1.0f

        val results = outputsToNMSPredictions(outputs, imgScaleX, imgScaleY)

        listOfTextDetectionResults = results

        return paintDetectionResultsAndText(bitmap)
    }

    private fun selectMaxScore(a: FloatArray, d: FloatArray): FloatArray {

        val outSize = 16
        val numPredictions = a.size / outSize
        var aScore = 0f
        var dScore = 0f

        for (i in 0 until numPredictions) {

            val aPrediction : FloatArray = a.sliceArray((i*outSize)until (outSize+i*outSize))
            val bPrediction : FloatArray = d.sliceArray((i*outSize)until (outSize+i*outSize))

            aScore += aPrediction[4]
            dScore += bPrediction[4]

        }

        aScore /= numPredictions
        dScore /= numPredictions

        analogScore = aScore
        digitalScore = dScore

        if (aScore > dScore){
            isLastAnalog = true
            return a
        }
        else {
            isLastAnalog = false
            return d
        }
    }

    private fun outputsToNMSPredictions(outputs: FloatArray, imgScaleX: Float, imgScaleY: Float): List<Result> {
        val results = mutableListOf<Result>()
        val outSize = 16
        val numPredictions = outputs.size / outSize

        for (i in 0 until numPredictions) {

            val prediction : FloatArray = outputs.sliceArray((i*outSize)until (outSize+i*outSize))
            val clasesScores: FloatArray = prediction.sliceArray(5 until outSize)

            val score = prediction[4]

            if (score > 0.5) {

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
                        text = classes[classId],
                        esDecimal = false
                    )
                )
            }
        }

        // return filterNMS(results)
        return nonMaxSuppression(results) //* scale
    }

    private fun nonMaxSuppression(detections: List<Result>, iouThreshold: Float = 0.5f): List<Result> {
        // Agrupar detecciones por clase
        val classGroups = detections.groupBy { it.classIndex }

        val selectedDetections = mutableListOf<Result>()
        val addedIndices = mutableSetOf<Int>()

        for ((classId, classDetections) in classGroups) {
            // Ordenar las detecciones de la clase por score
            val sortedDetections = classDetections.sortedByDescending { it.score }.toMutableList()

            while (sortedDetections.isNotEmpty()) {
                // Seleccionar la detección con el score más alto
                val bestDetection = sortedDetections.removeAt(0)
                selectedDetections.add(bestDetection)

                // Filtrar las detecciones restantes
                sortedDetections.removeAll {
                    getIoU(bestDetection.rect, it.rect) >= iouThreshold
                }
            }
        }

        // Ordenar las detecciones seleccionadas por score en orden descendente
        selectedDetections.sortByDescending { it.score }

        val finalDetections = mutableListOf<Result>()

        for (i in selectedDetections.indices) {
            if (i in addedIndices) continue

            var bestDetection = selectedDetections[i]

            for (j in i + 1 until selectedDetections.size) {
                val currentDetection = selectedDetections[j]
                if (getIoU(bestDetection.rect, currentDetection.rect) >= 0.5f) {
                    if (currentDetection.score > bestDetection.score) {
                        bestDetection = currentDetection
                        addedIndices.add(i)
                    } else {
                        addedIndices.add(j)
                    }
                }
            }

            if (!finalDetections.contains(bestDetection)) {
                finalDetections.add(bestDetection)
            }
        }

        return finalDetections
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
    private fun distanciaCajas(a: Result, b: Result): Float {

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

    private fun mediaDistancias(results: List<Result>): Float {
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

    private fun estaAdentro(ra: Result, rb: Result): Boolean {
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

    private fun doOCR(results: List<Result>) : String {

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

    private fun doOCRDigital(results: List<Result>) : String {

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

}