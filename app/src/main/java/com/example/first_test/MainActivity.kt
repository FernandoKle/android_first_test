package com.example.first_test

//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import com.github.mezhevikin.httprequest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.first_test.ui.theme.First_testTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import kotlin.reflect.KClass

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            First_testTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    //Greeting("Fernando")
                    TextInputScreen()
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
            text = "Hello ${name}! \nPrimer intento de hacer una app para Android",
            modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    First_testTheme {
        Greeting("Android")
    }
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
                //val intent = Intent(context, ModelActivity::class.java)
                //val intent = Intent(context, ImageCaptureActivity::class.java)
                //val intent = Intent(context, YoloActivity::class.java)
                val intent = Intent(context, activity.java)
                startActivity(context, intent, null)
            },
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text)
        }
    }
}

@Composable
fun TextInputScreen() {
    // Con rememberSaveable el valor se mantiene al cambiar las pantallas pero no al cerrar la app
    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    // var textState by remember { mutableStateOf(TextFieldValue("")) }
    var processedText by remember { mutableStateOf("") }

    fun processText(input: String): String {
        return input.uppercase()  // Función de procesamiento, por ejemplo, convertir a mayúsculas
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
            /*.background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Blue, Color.Green)
                )
            )*/,
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = textState,
                onValueChange = { newValue ->
                    textState = newValue
                    processedText = processText(newValue.text)
                },
                label = { Text("Ingrese texto") },
                modifier = Modifier.fillMaxWidth()
            )
            Row (
                horizontalArrangement = Arrangement.Center,
            ){
                NavigateButton(
                    activity = ImageCaptureActivityTF::class,
                    text = "Mobilenet V3"
                )
                NavigateButton(
                    activity = YoloActivityTF::class,
                    text = "YOLO"
                )
            }
            Row (
                horizontalArrangement = Arrangement.Center,
            ){
                NavigateButton(
                    activity = TextDetectionOnly::class,
                    text = "OCR"
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
    }
}

@Preview(showBackground = true)
@Composable
fun TextInputScreenPreview() {
    First_testTheme {
        TextInputScreen()
    }
}

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

@Preview(showBackground = true)
@Composable
fun ExpandedTestPreview() {
    First_testTheme {
        ExpandTest()
    }
}

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