package com.example.first_test

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.first_test.ui.theme.First_testTheme
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TestDB : ComponentActivity() {

    private val dbExecutor: ExecutorService = Executors.newFixedThreadPool(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            First_testTheme {
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

        // Codigo para iniciar cosas aca
    }

    override fun onDestroy() {
        super.onDestroy()

        dbExecutor.shutdown()
    }

    private fun showMainScreen() {
        setContent {
            First_testTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    fun MainScreen(){

        val context = LocalContext.current

        val coro = rememberCoroutineScope()

        val db = Room.databaseBuilder(
            context = this,
            klass = AppDatabase::class.java,
            name = "test_db"
        ).setQueryExecutor(dbExecutor).setTransactionExecutor(dbExecutor).build()

        val usuario = db.usuarioDao()
        val medicion = db.medicionDao()

        var valorMedicionState: TextFieldValue by rememberSaveable(
            stateSaver = TextFieldValue.Saver
        ) { mutableStateOf(
            TextFieldValue("")
        ) }

        var mediciones by remember {
            mutableStateOf<List<Medicion>>(emptyList())
        }

        val sdf = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())

        LaunchedEffect(Unit) {
            mediciones = medicion.getAll()
        }

        Box (
            modifier = Modifier.fillMaxSize()
        ){

            /** Barra superior - navegacion **/
            Column (
                modifier = Modifier
                    .background(Color.DarkGray)
                    //.fillMaxHeight(0.10f)
                    .align(alignment = Alignment.TopCenter)
                    .fillMaxWidth()
            ) {

                Row {
                    Text(text = "Mediciones", modifier = Modifier.padding(10.dp))
                }
            }

            /** Lista de elementos **/
            LazyColumn (
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 50.dp)
                    .fillMaxHeight(0.80f)
            ) {

                items(mediciones){med ->

                    Card(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth()
                    ){

                        var expanded by remember { mutableStateOf(false) }

                        Column (
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                        ) {
                            Row (modifier = Modifier.padding(10.dp)){
                                Text(text = "Valor: ${med.valor} [KWh]")
                            }

                            AnimatedVisibility(visible = expanded){
                                Column {
                                    Row (modifier = Modifier.padding(10.dp)){
                                        val date = Date(med.timestamp)
                                        val hora = sdf.format(date)

                                        Text(text = "mId: ${med.mid}")
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = "uId: ${med.uid}")
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = "Hora: $hora")
                                    }

                                    var editarExpanded by remember { mutableStateOf(false) }

                                    var editarValorMedicionState: TextFieldValue by remember { mutableStateOf(
                                        TextFieldValue("")
                                    ) }

                                    Row {

                                        // Eliminar
                                        Button(onClick = {
                                            coro.launch {
                                                Log.d("DB", "Eliminando medicion ${med.valor}")

                                                medicion.delete(med)

                                                mediciones = medicion.getAll()
                                            }
                                        },
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Icon(Icons.Rounded.Delete, "Eliminar", tint = Color.Red)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Editar
                                        Button(onClick = { editarExpanded = !editarExpanded },
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Icon(Icons.Rounded.Edit, "Editar", tint = Color.Green)
                                        }
                                    }
                                    AnimatedVisibility(visible = editarExpanded){

                                        Row {
                                            TextField(
                                                value = editarValorMedicionState,
                                                onValueChange = { new -> editarValorMedicionState = new },
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType =  KeyboardType.Number
                                                ),
                                                label = { BasicText(text = "Ingrese Nuevo Valor") },
                                                modifier = Modifier
                                                    .width(250.dp)
                                                    .padding(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))

                                            Button(
                                                modifier = Modifier.padding(20.dp),
                                                onClick = {
                                                    Log.d("DB", "Editando: ${editarValorMedicionState.annotatedString.toString()}")

                                                    coro.launch {

                                                        try {
                                                            val valor = editarValorMedicionState.annotatedString.toString().toInt()

                                                            medicion.update(
                                                                Medicion(
                                                                    mid = med.mid,
                                                                    uid = med.uid,
                                                                    valor = valor
                                                                )
                                                            )

                                                        }
                                                        catch (e: NumberFormatException) {
                                                            Toast.makeText(
                                                                context,
                                                                "No es un numero!",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }

                                                        mediciones = medicion.getAll()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Rounded.Done, "Listo")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // CARD END
                    }
                }
                // FIN Lista de Items
            }

            /** Barra Inferior - agregar elementos **/
            Column (
                modifier = Modifier
                    .background(Color.Blue)
                    .align(alignment = Alignment.BottomCenter)
                    //.height(100.dp)
                    .fillMaxWidth()
                //.fillMaxHeight(0.15f)
            ){
                Row {
                    TextField(
                        value = valorMedicionState,
                        onValueChange = { new -> valorMedicionState = new },
                        keyboardOptions = KeyboardOptions(
                            keyboardType =  KeyboardType.Number
                        ),
                        label = { BasicText(text = "Ingrese Medicion") },
                        modifier = Modifier
                            .width(250.dp)
                            .padding(20.dp)
                    )
                    Button(
                        modifier = Modifier.padding(20.dp),
                        onClick = {
                            Log.d("DB", "Agregando: ${valorMedicionState.annotatedString.toString()}")

                            coro.launch {

                                if (usuario.getCount() == 0){
                                    usuario.insertAll(
                                        Usuario(
                                            nombre = "test",
                                            codigo = "test",
                                            email = null
                                        )
                                    )
                                }

                                try {
                                    val valor = valorMedicionState.annotatedString.toString().toInt()

                                    medicion.insert(
                                        Medicion(
                                            uid = usuario.getOne().uid!!,
                                            valor = valor
                                        )
                                    )

                                }
                                catch (e: NumberFormatException) {
                                    Toast.makeText(
                                        context,
                                        "No es un numero!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                mediciones = medicion.getAll()
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Add, "Agregar")
                    }
                }
            }

        }

    }


}

