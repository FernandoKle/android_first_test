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
import com.example.first_test.ml.EastFloat640
import com.example.first_test.ui.theme.First_testTheme
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.model.Model
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.system.measureTimeMillis

class TextDetectionOnly : ComponentActivity() {

    private data class Result(
        val score: Float,
        val rect: RectF
    )

    private lateinit var module: EastFloat640
    private lateinit var processor: ImageProcessor

    private val input_w = 640
    private val input_h = 416
    private val output_w: Int = input_w / 4
    private val output_h: Int = input_h / 4

    private var classes: List<String>? = null
    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObjectDetectionApp()
        }

        try {

            val mean = floatArrayOf(123.68f, 116.779f, 103.939f)
            val stddev = floatArrayOf(1f, 1f, 1f)

            processor = ImageProcessor.Builder()
                .add(ResizeOp(input_h, input_w, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .add(NormalizeOp(mean, stddev)) // 0~255 a -1~1
                .build()

            Log.i("MODEL", "Cargando Modelo")
            try {
                val builder = Model.Options.Builder()
                    .setDevice(Model.Device.GPU)
                    .setNumThreads(4)
                    .build()

                module = EastFloat640.newInstance(this, builder)

                Log.i("MODEL", "Utilizando GPU")

            }
            catch (e: Exception){
                Log.e("MODEL", "Error al utilizar la GPU:", e)
                try {
                    val builder = Model.Options.Builder()
                        .setDevice(Model.Device.NNAPI)
                        .setNumThreads(4)
                        .build()

                    module = EastFloat640.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando NNAPI")
                }
                catch (e: Exception){
                    Log.e("MODEL", "Error al utilizar NNAPI:", e)
                    val builder = Model.Options.Builder()
                        .setDevice(Model.Device.CPU)
                        .setNumThreads(4)
                        .build()

                    module = EastFloat640.newInstance(this, builder)

                    Log.i("MODEL", "Utilizando CPU")
                }

            }


            BufferedReader(InputStreamReader(assets.open("classes.txt"))).use { br ->
                classes = br.readLines()
            }

            // Iniciar OpenCV
            // OpenCVLoader.initLocal()

        } catch (e: Exception) {
            Log.e("Object Detection", "Error loading model or classes", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        module.close()
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
                        val intent = Intent(context, RealTimeDetectionTF::class.java)
                        ContextCompat.startActivity(context, intent, null)
                    },
                        text = "LIVE"
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
        for (y in scores.indices) {
            for (x in scores[y].indices) {

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
                            endX * rW,
                            endY * rH,
                            )
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
        val threshold: Float = 0.5f

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

            Log.d("DIBUJANDO", "(${result.rect.left.toInt()}, ${result.rect.top.toInt()}),  (${result.rect.right.toInt()}, ${result.rect.bottom.toInt()})")

            canvas.drawRect(result.rect, paint)
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
}