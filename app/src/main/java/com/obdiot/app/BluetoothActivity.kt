package com.obdiot.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BluetoothScreen(
    onBack: () -> Unit
) {

    var devices by remember {
        mutableStateOf<List<BluetoothDevice>>(emptyList())
    }

    var selectedDevice by remember {
        mutableStateOf<BluetoothDevice?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Bluetooth OBD2",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {

                    val adapter = BluetoothAdapter.getDefaultAdapter()

                    try {

                        val bonded = adapter.bondedDevices

                        android.util.Log.d(
                            "OBDIOT",
                            "Pareados=${bonded.size}"
                        )

                        devices = bonded.toList()

                    } catch (e: Exception) {

                        android.util.Log.e(
                            "OBDIOT",
                            "ERRO",
                            e
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Listar dispositivos")
            }

            Button(
                onClick = {
                    onBack()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Voltar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        selectedDevice?.let {

            Text("Selecionado:")
            Text(it.name ?: "Sem nome")
            Text(it.address)

            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn {

            items(devices) { device ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {

                            selectedDevice = device
                        }
                ) {

                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {

                        Text(
                            device.name ?: "Sem nome"
                        )

                        Text(
                            device.address
                        )
                    }
                }
            }
        }
    }
}