package com.obdiot.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.FirebaseDatabase

import org.osmdroid.config.Configuration
import com.obdiot.app.MapScreen
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context

private const val APP_VERSION = 5

class MainActivity : ComponentActivity() {

    @Volatile
    private var userName = "Usuário"

    @Volatile
    private var userIcon = "car"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName

        checkForUpdates()

        setContent {
            OBDIOTApp(
                onUserSet = { name, icon ->
                    userName = name
                    userIcon = icon
                },
                onStartService = {
                    vibratePhone()
                    requestPermissionsAndStartService()
                }
            )
        }
    }

    private fun checkForUpdates() {

        FirebaseDatabase.getInstance()
            .getReference("app")
            .get()
            .addOnSuccessListener { snap ->

                val serverVersion =
                    snap.child("version").getValue(Int::class.java) ?: 1

                val apkUrl =
                    snap.child("apkUrl").getValue(String::class.java) ?: ""

                if (serverVersion > APP_VERSION && apkUrl.isNotEmpty()) {

                    AlertDialog.Builder(this)
                        .setTitle("Atualização disponível")
                        .setMessage("Existe uma nova versão do OBDIOT.")
                        .setPositiveButton("Atualizar") { _, _ ->

                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse(apkUrl)
                            )

                            startActivity(intent)
                        }
                        .setNegativeButton("Depois", null)
                        .show()
                }
            }
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val fine =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (fine) {
                startServiceGPS()
            }
        }

    private fun requestPermissionsAndStartService() {

        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        permissionLauncher.launch(permissions)
    }

    private fun startServiceGPS() {

        val intent = Intent(this, OBDIOTService::class.java).apply {
            putExtra("name", userName)
            putExtra("icon", userIcon)
        }

        startForegroundService(intent)
    }

    private fun vibratePhone() {

        val vibrator =
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    300,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )

        } else {

            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }
}

@Composable
fun OBDIOTApp(
    onUserSet: (String, String) -> Unit,
    onStartService: () -> Unit
) {

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize()
        ) {

            var logged by remember { mutableStateOf(false) }
            var currentScreen by remember {
                mutableStateOf("login")
            }

            var name by remember { mutableStateOf("") }
            var icon by remember { mutableStateOf("car") }

            when (currentScreen) {

                "map" -> {
                    MapScreen()
                    return@Surface
                }

                "obd" -> {
                    ObdScreen(
                        onBack = {
                            currentScreen = "login"
                        }
                    )
                    return@Surface
                }
            }

            if (logged) {

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text("📡 Rastreamento ativo em segundo plano")

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            logged = false
                        }
                    ) {
                        Text("Sair")
                    }
                }

            } else {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "🚗 OBDIOT",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = {
                            Text("Seu nome")
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row {

                        Button(
                            onClick = {
                                icon = "car"
                                onUserSet(name, icon)
                            }
                        ) {
                            Text("🚗 Carro")
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                icon = "walk"
                                onUserSet(name, icon)
                            }
                        ) {
                            Text("🚶 Pedestre")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onUserSet(name, icon)
                            logged = true
                            onStartService()

                            currentScreen = "map"
                        }
                    ){
                        Text("Entrar no sistema")

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                currentScreen = "obd"
                            }
                        ) {
                            Text("Abrir OBD-II")
                        }
                    }
                }
            }
        }
    }
}