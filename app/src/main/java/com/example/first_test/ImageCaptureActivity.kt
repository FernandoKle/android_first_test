package com.example.first_test

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
import com.example.first_test.ui.theme.First_testTheme
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.IOException

class ImageCaptureActivity : ComponentActivity() {

    private var photoUri: Uri? = null
    private lateinit var module: Module

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        module = LiteModuleLoader.load(assetFilePath(this, "model.pt"))

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
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                it, TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )

            val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
            val scores = outputTensor.dataAsFloatArray

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