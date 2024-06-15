package com.example.first_test

//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import com.github.mezhevikin.httprequest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import coil.compose.rememberImagePainter
import com.example.first_test.ui.theme.First_testTheme

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
fun NavigateButton(modifier: Modifier = Modifier) {
    val context: Context = LocalContext.current

    Box(modifier = modifier) {
        Button(
            onClick = {
                //val intent = Intent(context, ModelActivity::class.java)
                val intent = Intent(context, ImageCaptureActivity::class.java)
                startActivity(context, intent, null)
            },
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Jugar con mobilenet V2")
        }
    }
}

@Composable
fun TextInputScreen() {
    var textState by remember { mutableStateOf(TextFieldValue("")) }
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
            NavigateButton(
                modifier = Modifier.offset(x=60.dp, y= 330.dp)
            )
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
            Image(
                painter =  painterResource(id = R.drawable.cat_bye),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(5.dp)
                    .size(width = 160.dp, height = 200.dp)
            )
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = "Gil el que lo lee",
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

/*
@Composable
fun BackgroundImageExample() {
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
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = remember { mutableStateOf("") }.value,
                onValueChange = {},
                label = { Text("Enter your name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hello, Compose!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BackgroundImageExamplePreview() {
    First_testTheme {
        BackgroundImageExample()
    }
}
*/

@Composable
fun LoadImageFromUrl(imageUrl: String) {
    val painter = rememberImagePainter(
        data = imageUrl,
        builder = {
            crossfade(true)
            error(R.drawable.cat_bye) // Imagen por defecto en caso de error
        }
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
        LoadImageFromUrl("http://127.0.0.1:8000/static/logo_CELO.png")
    }
}