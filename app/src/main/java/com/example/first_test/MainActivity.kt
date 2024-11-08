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
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import com.example.first_test.ml.NumAn
import com.example.first_test.ml.NumDig
import com.example.first_test.ui.theme.First_testTheme
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.model.Model
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var listOfTextDetectionResults: List<Result>

    var progress = 0f
    var photoUri: Uri? = null

    private lateinit var module: NumAn
    private lateinit var moduleDig: NumDig
    private lateinit var processor: ImageProcessor
    private lateinit var rotProcessor: ImageProcessor

    private var classes = listOf(",", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    private val input_w = 640
    private val input_h = 640

    private var analogScore = 0f
    private var digitalScore = 0f
    private var bigScore = 0f
    private var isLastAnalog = false
    private var correctionAngle = 0

    private var finalMedicion = ""
    private var finalCodigoSocio = ""
    private var finalCodigoMedidor = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            First_testTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SplashScreen(
                        {
                            showMainScreen()
                        },
                        speed = 0.05f
                    )
                }
            }
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
                Log.e("APLICACION", "ERROR LOADING", e)
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.shutdown()
        module.close()
        moduleDig.close()
    }

    private fun showMainScreen() {
        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SocioInput()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DynamicColorTopAppBar(modifier: Modifier = Modifier) {
        val infiniteTransition = rememberInfiniteTransition()
        val rainbow = listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Blue,
        )
        val color by infiniteTransition.animateColor(
            initialValue = rainbow.first(),
            targetValue = rainbow.last(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000),
                repeatMode = RepeatMode.Restart
            ), label = ""
        )
        /*
        LaunchedEffect(Unit) {
            while (true) {
                delay(50)
                backgroundColor = Color(
                    (0..255).random(),
                    (0..255).random(),
                    (0..255).random()
                )
            }
        }
        */

        TopAppBar(
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.icon),
                            contentDescription = "Logo",
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "Watt A Meter",
                            modifier = Modifier.offset(x= (-20).dp),
                            fontSize = 30.sp,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    /*
                    Divider(
                        color = Color.DarkGray,
                        thickness = 2.dp,
                        modifier = Modifier.offset(-10.dp)
                    )
                    */
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.DarkGray
                // containerColor = color
            ),
            modifier = modifier
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun navBar(modifier: Modifier) {
        TopAppBar(
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.icon), // Asegúrate de tener icon.png en res/drawable
                            contentDescription = "Logo",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            modifier = modifier
        )
    }


    @Composable
    fun NavigateButton(
        modifier: Modifier = Modifier,
        activity: KClass<out Activity>,
        text: String
    ) {
        val context: Context = LocalContext.current

        Box(modifier = modifier) {
            Button(
                onClick = {
                    val intent = Intent(context, activity.java)
                    ContextCompat.startActivity(context, intent, null)
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text(text)
            }
        }
    }

    @Composable
    fun ProgressBar(){
        Column {
            Text("Progreso", modifier = Modifier
                .align(Alignment.CenterHorizontally),
                fontSize = 24.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                color = Color.Green,
                //trackColor = Color.Cyan,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
            )
        }
    }

    @Composable
    fun SocioInput() {

        val context = LocalContext.current

        finalCodigoSocio = obtenerString(context, "socio") ?: finalCodigoSocio
        finalCodigoMedidor = obtenerString(context, "codigo") ?: finalCodigoMedidor

        var codigoSocio by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(finalCodigoSocio)
            )
        }
        var codigoMedidor by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(finalCodigoMedidor)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            /*
            Image(
                painter = painterResource(id = R.drawable.background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            */

            Box {
                //navBar(Modifier)
                DynamicColorTopAppBar()
            }

            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (100).dp)
                //.height(140.dp)
                //.background(color = Color.Blue)
            ){
                ProgressBar()
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {

                Row() {
                    TextField(
                        value = codigoSocio,
                        onValueChange = { newValue ->
                            codigoSocio = newValue
                            finalCodigoSocio = codigoSocio.annotatedString.toString()
                        },
                        label = { Text("Código de Socio") },
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .clip(RoundedCornerShape(50.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(70.dp)
                            .offset(y = (-7).dp),
                        onClick = {
                            progress += 0.05f
                            /*TODO: QR*/
                            guardarString(context, "socio", finalCodigoSocio)
                            guardarString(context, "codigo", finalCodigoMedidor)
                        }
                    ) {
                        Icon(Icons.Rounded.QrCode, "QR is for pussys", modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = codigoMedidor,
                    onValueChange = { newValue ->
                        codigoMedidor = newValue
                        finalCodigoMedidor = codigoMedidor.annotatedString.toString()
                    },
                    label = { Text("Código de Medidor") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-40).dp, y = (-16).dp)
            ) {
                Button(
                    onClick = {

                        progress = 0.20f

                        finalCodigoMedidor = codigoMedidor.annotatedString.toString()
                        finalCodigoSocio = codigoSocio.annotatedString.toString()

                        guardarString(context, "socio", finalCodigoSocio)
                        guardarString(context, "codigo", finalCodigoMedidor)

                        setContent {
                            First_testTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    MedidorInput()
                                }
                            }
                        }
                    }
                ) {
                    Row {
                        Text("Continuar")
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, "you are deprecated")
                    }
                }
            }
        }
    }

    @Composable
    fun NumberSliderPicker(
        valueRange: IntRange,
        onValueChange: (Int) -> Unit
    ) {
        var selectedValue by remember { mutableIntStateOf(valueRange.first) }

        Column {
            Text(text = "Seleccionado: $selectedValue")

            AndroidView(
                modifier = Modifier,
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = valueRange.first
                        maxValue = valueRange.last
                        wrapSelectorWheel = true // Permite rotar cíclicamente
                        setOnValueChangedListener { _, _, newValue ->
                            selectedValue = newValue
                            onValueChange(newValue)
                        }
                    }
                },
                update = { picker ->
                    picker.value = selectedValue
                }
            )
        }
    }

    @Composable
    fun MedidorInput() {

        val context = LocalContext.current

        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var paintedBitmap by remember { mutableStateOf<Bitmap?>(null) }

        var detected by remember  { mutableStateOf(false) }
        var doneOCR by remember  { mutableStateOf(false) }
        var isRunning by remember { mutableStateOf(false) }

        finalMedicion = obtenerString(context, "medicion") ?: finalMedicion

        var medicion by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(finalMedicion)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            /*
            Image(
                painter = painterResource(id = R.drawable.background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            */

            Box {
                //navBar(Modifier)
                DynamicColorTopAppBar()
            }

            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (100).dp)
            ){
                ProgressBar()
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .offset(y = (150).dp),
                verticalArrangement = Arrangement.Center
            ) {

                /*
                NumberSliderPicker(
                    valueRange = 0..9,
                    onValueChange = { newValue ->
                        // Acción cuando cambia el valor
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                */

                Row() {
                    TextField(
                        value = medicion,
                        onValueChange = { newValue ->
                            medicion = newValue
                            val newS = newValue.annotatedString.toString()
                            if (newS != finalMedicion){
                                finalMedicion = newS
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Valor del medidor [KWh]") },
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .clip(RoundedCornerShape(50.dp))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK) {
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

                            bitmap?.let {

                                Toast.makeText(
                                    context,
                                    "Analizando...",
                                    Toast.LENGTH_SHORT
                                ).show()

                                isRunning = true
                                doneOCR = false

                                executor.execute() {
                                    val time = measureTimeMillis {

                                        paintedBitmap = detectObjectsAndPaint(
                                            it,
                                            tryNoiseReduction = true,
                                            tryToCorrect = true,
                                            onBitmapChange = {
                                                newBitmap -> bitmap = newBitmap
                                            }
                                        )
                                        isRunning = false
                                    }
                                    detected = true
                                    Log.d("INFERENCIA", "Tomo: $time [ms]")

                                    if (isLastAnalog){
                                        finalMedicion = doOCR(listOfTextDetectionResults)
                                    }
                                    else {
                                        finalMedicion = doOCRDigital(listOfTextDetectionResults)
                                    }
                                    guardarString(context, "medicion", finalMedicion)

                                    doneOCR = true
                                    isRunning = false
                                    medicion = TextFieldValue(finalMedicion)
                                    progress = 0.70f
                                }
                            } ?: run {
                                Toast.makeText(context, "Primero analice una imagen", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    val requestPermissionLauncher =
                        rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (!isGranted) {
                                Toast.makeText(context, "No se pudo obtener permiso para utilizar la camara", Toast.LENGTH_SHORT).show()
                            }
                        }

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(70.dp)
                            .offset(y = (-7).dp),
                        onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context,
                                    "android.permission.CAMERA"
                                ) -> {
                                    Log.d("IMAGEN", "Hay Permiso de camara")
                                    openImageChooser(context, launcher) { uri -> photoUri = uri }
                                }
                                else -> {
                                    requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Image, "Piss off")
                    }

                }
                if (isRunning){
                    Spacer(modifier = Modifier.height(30.dp))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(50.dp),
                        color = Color(255,0,255)
                    )
                }
                if (detected){
                    paintedBitmap?.let {
                        Spacer(modifier = Modifier.height(30.dp))
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxHeight(0.8f)
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    detected = !detected
                                }
                        )
                    }
                }
                else{
                    bitmap?.let {
                        Spacer(modifier = Modifier.height(30.dp))
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxHeight(0.8f)
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (paintedBitmap != null) {
                                        detected = !detected
                                    }
                                },
                        )
                    }
                }

            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-40).dp, y = (-16).dp)
            ) {
                Button(
                    onClick = {

                        progress = 0.90f
                        guardarString(context, "medicion", finalMedicion)

                        setContent {
                            First_testTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    ResumenyEnvio()
                                }
                            }
                        }
                    }
                ) {
                    Row {
                        Text("Continuar")
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, "you are deprecated")
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 30.dp, y = (-16).dp)
            ) {
                Button(
                    onClick = {
                        setContent {
                            First_testTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    SocioInput()
                                }
                            }
                        }
                    }
                ) {
                    Row {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "you are deprecated")
                    }
                }
            }

        }

    }

    @Composable
    fun ResumenyEnvio() {

        val context = LocalContext.current
        //val scope = rememberCoroutineScope()

        //finalCodigoSocio = obtenerString(context, "socio") ?: finalCodigoSocio
        //finalCodigoMedidor = obtenerString(context, "codigo") ?: finalCodigoMedidor

        var medicion by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(finalMedicion)
            )
        }
        var codigoSocio by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(finalCodigoSocio)
            )
        }
        var codigoMedidor by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(finalCodigoMedidor)
            )
        }

        /** TEST **/
        var texto_ip_servidor by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue("192.168.1.9")
            )
        }
        /** END TEST **/

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            Box {
                DynamicColorTopAppBar()
            }

            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (100).dp)
            ){
                ProgressBar()
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {

                TextField(
                    value = codigoSocio,
                    onValueChange = { newValue ->
                        codigoSocio = newValue
                        finalCodigoSocio = newValue.annotatedString.toString()
                    },
                    label = { Text("Código de Socio") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                )

                Spacer(modifier = Modifier.height(30.dp))
                TextField(
                    value = codigoMedidor,
                    onValueChange = { newValue ->
                        codigoMedidor = newValue
                        finalCodigoMedidor = newValue.annotatedString.toString()
                    },
                    label = { Text("Código de Medidor") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                )

                Spacer(modifier = Modifier.height(30.dp))
                TextField(
                    value = medicion,
                    onValueChange = { newValue ->
                        medicion = newValue
                        val newS = newValue.annotatedString.toString()
                        if (newS != finalMedicion){
                            finalMedicion = newS
                        }
                    },
                    label = { Text("Valor del medidor [KWh]") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                )

                /** TEST **/
                Spacer(modifier = Modifier.height(30.dp))
                TextField(
                    value = texto_ip_servidor,
                    onValueChange = { newValue ->
                        texto_ip_servidor = newValue
                    },
                    label = { Text("IP del servidor (TEST)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50.dp))
                )
                /** END TEST **/

                Spacer(modifier = Modifier.height(60.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                    modifier = Modifier
                        //.background(Color.Green, shape = RoundedCornerShape(12.dp))
                        .align(Alignment.CenterHorizontally)
                        .height(60.dp)
                        .fillMaxWidth(0.8f),
                    onClick = {

                        /** Codigo para enviar datos al servidor **/

                        val socio = codigoSocio.annotatedString.toString()
                        val codigo = codigoMedidor.annotatedString.toString()
                        val valorMedicion = medicion.annotatedString.toString()
                        val dir_ip = texto_ip_servidor.annotatedString.toString()

                        Toast.makeText(context,
                            "Enviando datos a http://${dir_ip}:8000/recibir-medicion",
                            Toast.LENGTH_SHORT).show()

                        executor.execute {

                            try {
                                val json = JSONObject().apply {
                                    put("socio", socio)
                                    put("codigo", codigo)
                                    put("medicion", valorMedicion)
                                }

                                val client = OkHttpClient()
                                val mediaType = "application/json; charset=utf-8".toMediaType()
                                val body = json.toString().toRequestBody(mediaType)

                                val request = Request.Builder()
                                    .url("http://${dir_ip}:8000/recibir-medicion")
                                    .post(body)
                                    .build()

                                client.newCall(request).execute().use { response ->
                                    if (response.isSuccessful) {
                                        Log.d("Solicitud", "JSON enviado exitosamente: ${response.body?.string()}")
                                    } else {
                                        Log.e("Solicitud", "Error en la solicitud: ${response.code}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("Solicitud", "Error al enviar JSON", e)
                            }
                        }

                        /** FIN Codigo para enviar datos al servidor **/

                        setContent {
                            progress = 1.00f
                            First_testTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    ExitoAlEnviar()
                                }
                            }
                        }
                    }
                ) {
                    Row {
                        Text("Confirmar y Enviar")
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(Icons.Rounded.Check, "send me in")
                    }
                }

            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 30.dp, y = (-16).dp)
            ) {
                Button(
                    onClick = {
                        setContent {
                            First_testTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    MedidorInput()
                                }
                            }
                        }
                    }
                ) {
                    Row {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "YAG deprecated")
                    }
                }
            }
        }
    }

    @Composable
    fun ExitoAlEnviar() {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            Box {
                DynamicColorTopAppBar()
            }

            Box(modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (100).dp)
            ){
                ProgressBar()
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {

                Text("Éxito al Enviar",
                    color = Color.Green,
                    fontSize = 30.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(60.dp))

                Button(
                    //colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                    modifier = Modifier
                        //.background(Color.Green, shape = RoundedCornerShape(12.dp))
                        .align(Alignment.CenterHorizontally)
                        .height(60.dp)
                        .fillMaxWidth(0.8f),
                    onClick = {
                        setContent {
                            progress = 0.00f
                            First_testTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    SocioInput()
                                }
                            }
                        }
                    }
                ) {
                    Row {
                        Text("Volver a Inicio")
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(Icons.Rounded.Home, "gotta go back")
                    }
                }

            }

        }
    }

    fun openImageChooser(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        onCameraUriCreated: (Uri) -> Unit
    ) {
        val photoUri = createImageFileUri(context)

        Log.d("IMAGEN", "algo")
        onCameraUriCreated(photoUri)

        val pickPhotoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }

        val chooserIntent = Intent.createChooser(pickPhotoIntent, "Selecciona una imagen o toma una foto")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent, pickPhotoIntent))

        Log.d("IMAGEN", "Ejecutando chooser")
        launcher.launch(chooserIntent)
    }

    private fun detectObjectsAndPaint(
        inputBitmap: Bitmap,
        onBitmapChange: (Bitmap) -> Unit = {},
        tryToCorrect: Boolean = true,
        tryNoiseReduction: Boolean = false,
        isAnalog: Boolean = false,
        isDigital: Boolean = false,
    ) : Bitmap {

        lateinit var bitmap: Bitmap

        /** Correccion para 16:9 usando un recorte **/
        if (tryToCorrect) {
            val squareSize = minOf(inputBitmap.width, inputBitmap.height)
            val xOffset = (inputBitmap.width - squareSize) / 2
            val yOffset = (inputBitmap.height - squareSize) / 2

            bitmap = Bitmap.createBitmap(inputBitmap, xOffset, yOffset, squareSize, squareSize)
            onBitmapChange(bitmap)
        }
        else {
            bitmap = inputBitmap
        }

        /** PREPROCESADO DEL MODELO **/
        val tensorBitmap = TensorImage.fromBitmap(bitmap)

        val input = processor.process(tensorBitmap)
        var outputs: FloatArray? = null

        /** INFERENCIA **/
        if (!isAnalog and !isDigital) {
            val outputsAn = module.process(input.tensorBuffer).outputFeature0AsTensorBuffer.floatArray
            val outputsDig = moduleDig.process(input.tensorBuffer).outputFeature0AsTensorBuffer.floatArray

            val aN = getDetectionScore(outputsAn)
            val dN = getDetectionScore(outputsDig)

            val aScore = aN[0]
            val dScore = dN[0]
            val aCumulativeScore = aN[1]
            val dCumulativeScore = dN[1]

            analogScore = aScore
            digitalScore = dScore

            if (aCumulativeScore > dCumulativeScore){
                isLastAnalog = true
                Log.d("MODELO", "Detecto ANALOGICO (score: ${aScore})")
                outputs = outputsAn
            }
            else {
                isLastAnalog = false
                Log.d("MODELO", "Detecto DIGITAL (score: ${dScore})")
                outputs = outputsDig
            }
        }
        else if (isAnalog) {
            outputs = module.process(input.tensorBuffer).outputFeature0AsTensorBuffer.floatArray
        }
        else if (isDigital) {
            outputs = moduleDig.process(input.tensorBuffer).outputFeature0AsTensorBuffer.floatArray
        }

        /** PROCESAR RESULTADOS **/
        val imgScaleX = bitmap.width * 1.0f
        val imgScaleY = bitmap.height * 1.0f

        if (outputs == null){
            Log.d("MODELO", "Error en la inferencia")
            return bitmap
        }

        val results = outputsToNMSPredictions(outputs, imgScaleX, imgScaleY)

        listOfTextDetectionResults = results
        Log.d("MODELO", "${results.size} detecciones, Score: ${bigScore}")


        /** APLICAR CORRECCIONES **/
        if (tryNoiseReduction and (results.size <= 1)) {

            Log.d("MODELO", "Intentando reduccion de ruido")

            val newBitmap = aplicarReduccionDeRuido(bitmap)
            onBitmapChange(newBitmap)

            return detectObjectsAndPaint(newBitmap,
                tryToCorrect = true,
                tryNoiseReduction = false
            )
        }

        /** APLICAR RECORTE Y ROTACION **/
        if (tryToCorrect and  (results.size > 1)){

            /** CALCULAR ROTACION **/
            val detections = results.sortedBy{ it.rect.left }
            val p1 = detections.first()//.rect
            val p2 = detections.last()//.rect

            val y2 = p2.y.toDouble()
            val y1 = p1.y.toDouble()
            val x2 = p2.x.toDouble()
            val x1 = p1.x.toDouble()

            val angle = Math.toDegrees(atan2(y2 - y1, x2 - x1))

            Log.d("MODELO", "Rotacion: ${angle}")

            if (bigScore < 0.78f){

                Log.d("MODELO", "Intentando ZOOM")

                val r1 = p1.rect.toRect()
                val r2 = p2.rect.toRect()

                val left = r1.left
                val top = minOf(r1.top, r2.top)
                val width = r2.right - r1.left
                val bottom = maxOf(r1.bottom, r2.bottom)
                val height = bottom - top
                val sSize = maxOf( maxOf(width, height), bitmap.width / 4)

                lateinit var newBitmap: Bitmap

                /** RECORTE **/
                if (((top + sSize) <= bitmap.height) and ((left + sSize) <= bitmap.width)){
                    newBitmap = Bitmap.createBitmap(bitmap,
                        maxOf(0, left - sSize/2),
                        maxOf(0, top - sSize/2),
                        minOf(sSize + sSize/2, bitmap.width),
                        minOf(sSize + sSize/2, bitmap.height)
                    )
                }
                else {
                    newBitmap = Bitmap.createBitmap(bitmap,
                        maxOf(0, left - sSize/2),
                        maxOf(0, top - sSize/2),
                        minOf(sSize + sSize/2, bitmap.width) - maxOf(0, left - sSize/2),
                        minOf(sSize + sSize/2, bitmap.height) - maxOf(0, top - sSize/2)
                    )
                }

                /** ROTACION **/
                if (abs(angle) > 3) {
                    Log.d("MODELO", "Intentando corregir rotacion (${angle}°)")

                    newBitmap = newBitmap.rotateBitmapPure(angle.toFloat())

                    //val cx = ( (p2.x + p1.x) / 2 ).toDouble()
                    //val cy = ( (p2.y + p1.y) / 2 ).toDouble()
                    //newBipmap = newBipmap.rotateBitmapAroundAxis(angle, cx, cy)
                }
                onBitmapChange(newBitmap)

                if (isLastAnalog) {
                    return detectObjectsAndPaint(newBitmap , tryToCorrect = false, isAnalog = true )
                }
                else {
                    return detectObjectsAndPaint(newBitmap , tryToCorrect = false, isDigital = true )
                }
            }
        }

        return paintDetectionResultsAndText(bitmap)
    }

    private fun outputsToNMSPredictions(outputs: FloatArray, imgScaleX: Float, imgScaleY: Float): List<Result> {
        val results = mutableListOf<Result>()
        val outSize = 16
        val numPredictions = outputs.size / outSize
        var cumulativeScore = 0f
        var count = 0

        for (i in 0 until numPredictions) {

            val prediction : FloatArray = outputs.sliceArray((i*outSize)until (outSize+i*outSize))
            val clasesScores: FloatArray = prediction.sliceArray(5 until outSize)

            val score = prediction[4]

            if (score > 0.5f) {

                cumulativeScore += score
                count += 1

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
                        esDecimal = false,
                        x = x,
                        y = y
                    )
                )
            }
        }

        bigScore = cumulativeScore / count.toFloat()

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


    @Preview(showBackground = true)
    @Composable
    fun ResumenPreview() {
        First_testTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ResumenyEnvio()
            }
        }
    }

    /*
    @Preview(showBackground = true)
    @Composable
    fun SocioInputPreview() {
        First_testTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                SocioInput()
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MedidorInputPreview() {
        First_testTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MedidorInput()
            }
        }
    }
*/
    /** FIN NO AGREGAR {} **/
}

/*
@Composable
fun ExpandTest() {

    val context = LocalContext.current
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    Card(
        modifier = Modifier.offset(x=100.dp, y=500.dp),
        //elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ){
        var expanded by remember { mutableStateOf(false) }
        Column(
            Modifier
                .clickable { expanded = !expanded }
        ) {
            /*
            Image(
                painter =  painterResource(id = R.drawable.cat_bye),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(5.dp)
                    .size(width = 160.dp, height = 200.dp)
            )
            */
            AsyncImage(
                model = "https://media.tenor.com/pKUHUVvLjsgAAAAM/tokyo-mew-mew-mew-ichigo.gif",
                //model = ImageRequest.Builder(context)
                //    .data("https://media.tenor.com/pKUHUVvLjsgAAAAM/tokyo-mew-mew-mew-ichigo.gif")
                //    .crossfade(true)
                //    .build(),
                imageLoader = imageLoader, // Necesario para GIFs
                contentDescription = null,
                placeholder = painterResource(id = R.drawable.cat_bye),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(5.dp)
                    .size(width = 160.dp, height = 200.dp)
            )
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = "Gil el que lo lea",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black,
                )
            }
        }
    }
}
 */
/*
Row (
    horizontalArrangement = Arrangement.Center,
){
    //NavigateButton(
    //    activity = TextDetectionOnly::class,
    //    text = "OCR"
    //)
    NavigateButton(
        activity = TestDB::class,
        text = "DB"
    )
}
Row(
    horizontalArrangement = Arrangement.Center
){
    //NavigateButton(
    //    activity = DetectarMedidoresActivity::class,
    //    text = "MED"
    //)
    NavigateButton(
        activity = YoloOCR::class,
        text = "YoloOCR"
    )
}
Spacer(modifier = Modifier.height(16.dp))
Text(
    text = processedText,
    style = MaterialTheme.typography.bodyLarge,
    color = Color.Green,
)
}
ExpandTest()
 */

/*
@Composable
fun LoadImageFromUrl(imageUrl: String) {
    val painter = // Imagen por defecto en caso de error
        rememberAsyncImagePainter(
            ImageRequest.Builder(LocalContext.current).data(data = imageUrl).apply(block = fun ImageRequest.Builder.() {
                crossfade(true)
                error(R.drawable.cat_bye) // Imagen por defecto en caso de error
            }).build()
        )

    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentScale = ContentScale.Crop
    )
}

@Preview(showBackground = true)
@Composable
fun LoadImageFromUrlPreview() {
    First_testTheme {
        LoadImageFromUrl("http://192.168.18.19:8000/static/logo_CELO.png")
    }
}

data class VocabEntry(val token: String, val index: Int)

fun loadVocabJson(context: Context, fileName: String): Map<String, Int> {
    val assetManager = context.assets
    val inputStream = assetManager.open(fileName)
    val reader = InputStreamReader(inputStream)
    val vocabType = object : TypeToken<Map<String, Int>>() {}.type
    return Gson().fromJson(reader, vocabType)
}

class Tokenizer(private val vocab: Map<String, Int>) {
    private val reverseVocab = vocab.entries.associate { it.value to it.key }

    fun tokenize(text: String): List<Int> {
        return text.toCharArray().map { char ->
            vocab[char.toString()] ?: vocab["<unk>"]!!
        }
    }

    fun detokenize(tokens: List<Int>): String {
        return tokens.joinToString(" ") { token ->
            reverseVocab[token] ?: "<unk>"
        }
    }
}

@Composable
fun TokenizerScreen(tokenizer: Tokenizer) {
    var inputText by remember { mutableStateOf("") }
    var tokenizedText by remember { mutableStateOf<List<Int>>(emptyList()) }
    var detokenizedText by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter text to tokenize") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            tokenizedText = tokenizer.tokenize(inputText)
            detokenizedText = tokenizer.detokenize(tokenizedText)
        }) {
            Text("Tokenize and Detokenize")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Tokenized: $tokenizedText")
        Text("Detokenized: $detokenizedText")
    }
}

@Composable
fun TokenizerApp() {
    val context = LocalContext.current
    val vocab by remember { mutableStateOf(loadVocabJson(context, "vocab.json")) }
    val tokenizer = remember(vocab) { Tokenizer(vocab) }

    TokenizerScreen(tokenizer)
}

@Preview
@Composable
fun PreviewTokenizerApp() {
    First_testTheme {
        TokenizerApp()
    }
}
*/