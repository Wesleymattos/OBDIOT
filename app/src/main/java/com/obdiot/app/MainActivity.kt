package com.obdiot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat



import android.app.AlertDialog
import android.net.Uri
import com.google.firebase.database.FirebaseDatabase
private val APP_VERSION = 1
class MainActivity : ComponentActivity() {

    private fun checkForUpdates() {

        FirebaseDatabase.getInstance()
            .getReference("app")
            .get()
            .addOnSuccessListener { snap ->

                val serverVersion =
                    snap.child("version").getValue(Int::class.java) ?: 1

                val apkUrl =
                    snap.child("apkUrl").getValue(String::class.java) ?: ""

                if (serverVersion > APP_VERSION) {

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


    @Volatile private var userName = "Usuário"
    @Volatile private var userIcon = "car"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkForUpdates()






        setContent {
            OBDIOTApp(
                onUserSet = { name, icon ->
                    userName = name
                    userIcon = icon
                },
                onStartService = {
                    requestPermissionsAndStartService()
                }
            )
        }
    }

    // =========================
    // PERMISSÕES
    // =========================
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->

            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true

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

    // =========================
    // INICIA SERVICE (SEGUNDO PLANO)
    // =========================
    private fun startServiceGPS() {

        val intent = Intent(this, OBDIOTService::class.java).apply {
            putExtra("name", userName)
            putExtra("icon", userIcon)
        }

        startForegroundService(intent)
    }
}

/* =========================================================
   UI SIMPLES (LOGIN + START SERVICE)
   ========================================================= */

@Composable
fun OBDIOTApp(
    onUserSet: (String, String) -> Unit,
    onStartService: () -> Unit
) {

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {

            var logged by remember { mutableStateOf(false) }
            var name by remember { mutableStateOf("") }
            var icon by remember { mutableStateOf("car") }

            if (logged) {

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text("📡 Rastreamento ativo em segundo plano")

                    Spacer(Modifier.height(20.dp))

                    Button(onClick = { logged = false }) {
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
                        "🚗 OBDIOT",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Seu nome") }
                    )

                    Spacer(Modifier.height(10.dp))

                    Row {

                        Button(onClick = {
                            icon = "car"
                            onUserSet(name, icon)
                        }) {
                            Text("🚗 Carro")
                        }

                        Spacer(Modifier.width(10.dp))

                        Button(onClick = {
                            icon = "walk"
                            onUserSet(name, icon)
                        }) {
                            Text("🚶 Pedestre")
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(onClick = {
                        onUserSet(name, icon)
                        logged = true

                        // 🔥 AQUI É O PONTO PRINCIPAL
                        onStartService()
                    }) {
                        Text("Entrar no sistema")
                    }
                }
            }
        }
    }
}