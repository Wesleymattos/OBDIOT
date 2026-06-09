package com.obdiot.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ObdScreen(
    onBack: () -> Unit
) {

    var connected by remember { mutableStateOf(false) }

    var rpm by remember { mutableStateOf("--") }
    var speed by remember { mutableStateOf("--") }
    var temp by remember { mutableStateOf("--") }
    var fuel by remember { mutableStateOf("--") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),

        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "🚗 Diagnóstico OBD-II",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            if (connected)
                "🟢 Conectado"
            else
                "🔴 Desconectado"
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text("RPM: $rpm")
                Text("Velocidade: $speed km/h")
                Text("Temperatura: $temp °C")
                Text("Combustível: $fuel %")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {

                connected = true

                rpm = "2500"
                speed = "80"
                temp = "92"
                fuel = "65"
            }
        ) {
            Text("Conectar ELM327")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                connected = false
            }
        ) {
            Text("Desconectar")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onBack
        ) {
            Text("Voltar")
        }
    }
}