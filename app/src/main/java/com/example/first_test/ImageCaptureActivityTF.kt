package com.example.first_test
/*
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.first_test.ml.MobilenetV2
import com.example.first_test.ui.theme.First_testTheme
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.model.Model
import java.io.File
import java.io.IOException

class ImageCaptureActivityTF : ComponentActivity() {

    private lateinit var module: MobilenetV2
    private lateinit var processor: ImageProcessor

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        processor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(NormalizeOp(127.5f, 127.5f)) // 0~255 a -1~1 ==> Hace: (valor - mean) / stddev
            .build()

        Log.i("MODEL", "Cargando Modelo")
        try {
            val builder = Model.Options.Builder()
                .setDevice(Model.Device.GPU)
                .setNumThreads(4)
                .build()

            module = MobilenetV2.newInstance(this, builder)

            Log.i("MODEL", "Utilizando GPU")

        }
        catch (e: Exception){
            try {
                val builder = Model.Options.Builder()
                    .setDevice(Model.Device.NNAPI)
                    .setNumThreads(4)
                    .build()

                module = MobilenetV2.newInstance(this, builder)

                Log.i("MODEL", "Utilizando NNAPI")
            }
            catch (e: Exception){
                val builder = Model.Options.Builder()
                    .setDevice(Model.Device.CPU)
                    .setNumThreads(4)
                    .build()

                module = MobilenetV2.newInstance(this, builder)

                Log.i("MODEL", "Utilizando CPU")
            }

        }

        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        module.close()
    }

    @Composable
    fun MainContent() {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var className by remember { mutableStateOf("") }
        var estadoAnalisis by remember { mutableStateOf("Esperando imagen...") }
        val context = LocalContext.current
        //val scope = rememberCoroutineScope()

        val takePictureLauncher =
            rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                photoUri?.let {
                    bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
                    //processImage(bitmap, context) { result ->
                    //    className = result
                    //}
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
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {

            Button(onClick = {
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context, "Manifest.permission.CAMERA") -> {
                        photoUri = createImageFileUri(context)
                        takePictureLauncher.launch(photoUri)
                    }
                    else -> {
                        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }
            }) {
                BasicText("Tomar Foto")
            }

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(top = 16.dp)
                )
            }

            Text(
                text = className,
                modifier = Modifier.padding(top = 16.dp),
                color = Color.Green
            )
            Text(
                text = estadoAnalisis,
                modifier = Modifier.padding(top = 16.dp),
                color = Color.Green
            )

            Button(onClick = {
                if (bitmap != null){
                    estadoAnalisis = "Analizando..."
                    processImage(bitmap) { result ->
                    className = result
                    }
                    estadoAnalisis = "Listo"
                }
                else{
                    estadoAnalisis = "No hay imagen"
                }
            }) {
                BasicText("Analizar Imagen")
            }
        }
    }

    private fun processImage(bitmap: Bitmap?, callback: (String) -> Unit) {
        bitmap?.let {

            val tensorBitmap = TensorImage.fromBitmap(bitmap)

            val input = processor.process(tensorBitmap)

            val outputsBuffer = module.process(input.tensorBuffer)

            val scores = outputsBuffer.outputFeature0AsTensorBuffer.floatArray

            var maxScore = -Float.MAX_VALUE
            var maxScoreIdx = -1
            for (i in scores.indices) {
                if (scores[i] > maxScore) {
                    maxScore = scores[i]
                    maxScoreIdx = i
                }
            }

            val className = ImageNetClasses.IMAGENET_CLASSES[maxScoreIdx] + " (score: " + maxScore + " )"
            callback(className)
        }
    }

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

    @Preview(showBackground = true)
    @Composable
    fun MainContentPreview() {
        First_testTheme {
            MainContent()
        }
    }
}
 */