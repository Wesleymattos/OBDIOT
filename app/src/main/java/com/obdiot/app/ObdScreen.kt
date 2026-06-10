package com.obdiot.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

@Composable
fun ObdScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter? = remember { BluetoothAdapter.getDefaultAdapter() }
    
    // Observa o estado global do ObdManager (Singleton)
    val obdData by ObdManager.state.collectAsState()
    val isConnected by ObdManager.isConnected.collectAsState()

    var showDevicePicker by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            showDevicePicker = true
        } else {
            Toast.makeText(context, "Permissões de Bluetooth negadas", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            val bondedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            pairedDevices = bondedDevices
            showDevicePicker = true
        } else {
            launcher.launch(permissions)
        }
    }

    if (showDevicePicker) {
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            title = { Text("Selecione o ELM327") },
            text = {
                LazyColumn {
                    items(pairedDevices) { device ->
                        Text(
                            text = "${device.name ?: "Desconhecido"} (${device.address})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    ObdManager.connect(device) { error ->
                                        Toast.makeText(context, "Erro: $error", Toast.LENGTH_SHORT).show()
                                    }
                                    showDevicePicker = false
                                }
                                .padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicePicker = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_app),
            contentDescription = "Logo",
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Diagnóstico OBD-II",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (isConnected) "🟢 Conectado (Segundo Plano Ativo)" else "🔴 Desconectado",
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Grade de Informações em "Janelas" (Cards)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DataCard("RPM do Motor", obdData.rpm, "RPM", Modifier.weight(1f))
                DataCard("Velocidade", obdData.speed, "km/h", Modifier.weight(1f))
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DataCard("Consumo Inst.", obdData.instantConsumption, "km/L", Modifier.weight(1f))
                DataCard("Consumo Médio", obdData.averageConsumption, "km/L", Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DataCard("Distância", String.format(Locale.US, "%.2f", obdData.distanceTraveled), "km", Modifier.weight(1f))
                DataCard("Combustível Usado", String.format(Locale.US, "%.3f", obdData.fuelUsed), "Litros", Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DataCard("Temp. Líquido", obdData.temp, "°C", Modifier.weight(1f))
                DataCard("Nível do Tanque", obdData.fuelLevel, "%", Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DataCard("Tensão Bateria", obdData.volt, "", Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f)) // Espaço vazio para manter o alinhamento
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!isConnected) {
            Button(
                onClick = { checkAndRequestPermissions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conectar ELM327")
            }
        } else {
            Button(
                onClick = { ObdManager.disconnect() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Desconectar")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voltar ao Mapa")
        }
    }
}

@Composable
fun DataCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(
                value, 
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
        }
    }
}
