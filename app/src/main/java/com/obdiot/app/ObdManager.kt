package com.obdiot.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.*

object ObdManager {
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var job: Job? = null

    private val _state = MutableStateFlow(ObdData())
    val state = _state.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    data class ObdData(
        val rpm: String = "--",
        val speed: String = "--",
        val temp: String = "--",
        val fuelLevel: String = "--",
        val volt: String = "--",
        val instantConsumption: String = "--",
        val averageConsumption: String = "--",
        val distanceTraveled: Double = 0.0,
        val fuelUsed: Double = 0.0
    )

    private var startTime: Long = 0
    private var userId: String? = null
    private val db = FirebaseDatabase.getInstance().getReference("users")

    fun setUserId(id: String) {
        this.userId = id
    }

    fun connect(device: BluetoothDevice, onConnectionError: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID)
                socket?.connect()
                _isConnected.value = true
                startTime = System.currentTimeMillis()
                startReading()
            } catch (e: Exception) {
                _isConnected.value = false
                onConnectionError(e.message ?: "Erro desconhecido")
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        _isConnected.value = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun startReading() {
        job = CoroutineScope(Dispatchers.IO).launch {
            val outStream = socket?.outputStream ?: return@launch
            val inStream = socket?.inputStream ?: return@launch

            try {
                sendComm(outStream, "ATZ")
                delay(1000)
                sendComm(outStream, "ATE0")
                delay(200)

                var lastTime = System.currentTimeMillis()

                while (isActive && _isConnected.value) {
                    val currentTime = System.currentTimeMillis()
                    val dt = (currentTime - lastTime) / 1000.0
                    lastTime = currentTime

                    val rpmVal = parseRpm(queryObd(outStream, inStream, "010C"))
                    val speedVal = parseSpeed(queryObd(outStream, inStream, "010D"))
                    val mafVal = parseMaf(queryObd(outStream, inStream, "0110"))
                    val tempVal = parseTemp(queryObd(outStream, inStream, "0105"))
                    val fuelLevelVal = parseFuelLevel(queryObd(outStream, inStream, "012F"))
                    val voltVal = parseVolt(queryObd(outStream, inStream, "ATRV"))

                    calculateMetrics(rpmVal, speedVal, mafVal, dt)

                    val currentData = _state.value.copy(
                        rpm = if (rpmVal >= 0) rpmVal.toString() else "--",
                        speed = if (speedVal >= 0) speedVal.toString() else "--",
                        temp = if (tempVal >= -40) tempVal.toString() else "--",
                        fuelLevel = if (fuelLevelVal >= 0) fuelLevelVal.toString() else "--",
                        volt = voltVal
                    )
                    _state.value = currentData
                    
                    // Envia para o Firebase se o userId estiver definido
                    userId?.let { id ->
                        db.child(id).child("obd").setValue(currentData)
                    }
                    
                    delay(500)
                }
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    private fun calculateMetrics(rpm: Int, speed: Int, maf: Double, dt: Double) {
        if (maf <= 0) return

        // Fluxo de combustível em Litros por Hora (MAF * 0.331 para Gasolina)
        val fuelFlowLh = maf * 0.331
        val fuelInThisStep = (fuelFlowLh / 3600.0) * dt
        
        val newFuelUsed = _state.value.fuelUsed + fuelInThisStep
        val newDistance = _state.value.distanceTraveled + (speed / 3600.0) * dt

        // Consumo Instantâneo (km/L) = Velocidade (km/h) / Fluxo (L/h)
        val instant = if (speed > 5 && fuelFlowLh > 0.1) {
            String.format("%.1f", speed / fuelFlowLh)
        } else "0.0"

        // Consumo Médio (km/L) = Distância Total (km) / Combustível Total (L)
        val avg = if (newFuelUsed > 0.01) {
            String.format("%.1f", newDistance / newFuelUsed)
        } else "--"

        _state.value = _state.value.copy(
            instantConsumption = instant,
            averageConsumption = avg,
            distanceTraveled = newDistance,
            fuelUsed = newFuelUsed
        )
    }

    private fun sendComm(os: OutputStream, command: String) {
        os.write((command + "\r").toByteArray())
        os.flush()
    }

    private fun queryObd(os: OutputStream, `is`: InputStream, command: String): String {
        try {
            sendComm(os, command)
            val buffer = ByteArray(1024)
            var bytes = `is`.read(buffer)
            if (bytes <= 0) return ""
            var fullResponse = String(buffer, 0, bytes)
            while (!fullResponse.contains(">")) {
                bytes = `is`.read(buffer)
                if (bytes <= 0) break
                fullResponse += String(buffer, 0, bytes)
            }
            return fullResponse.trim()
        } catch (_: Exception) { return "" }
    }

    private fun parseRpm(response: String): Int {
        return try {
            val clean = response.replace(" ", "").replace(">", "")
            val idx = clean.indexOf("410C")
            if (idx != -1) {
                val hex = clean.substring(idx + 4, idx + 8)
                hex.toInt(16) / 4
            } else -1
        } catch (_: Exception) { -1 }
    }

    private fun parseSpeed(response: String): Int {
        return try {
            val clean = response.replace(" ", "").replace(">", "")
            val idx = clean.indexOf("410D")
            if (idx != -1) {
                val hex = clean.substring(idx + 4, idx + 6)
                hex.toInt(16)
            } else -1
        } catch (_: Exception) { -1 }
    }

    private fun parseMaf(response: String): Double {
        return try {
            val clean = response.replace(" ", "").replace(">", "")
            val idx = clean.indexOf("4110")
            if (idx != -1) {
                val hex = clean.substring(idx + 4, idx + 8)
                hex.toInt(16) / 100.0
            } else 0.0
        } catch (_: Exception) { 0.0 }
    }

    private fun parseTemp(response: String): Int {
        return try {
            val clean = response.replace(" ", "").replace(">", "")
            val idx = clean.indexOf("4105")
            if (idx != -1) {
                val hex = clean.substring(idx + 4, idx + 6)
                hex.toInt(16) - 40
            } else -273
        } catch (_: Exception) { -273 }
    }

    private fun parseFuelLevel(response: String): Int {
        return try {
            val clean = response.replace(" ", "").replace(">", "")
            // A GM às vezes responde com 412F ou 41 2F. 
            // Procuro o 2F após o 41 (que é o modo de resposta)
            val idx = clean.indexOf("412F")
            if (idx != -1) {
                val hex = clean.substring(idx + 4, idx + 6)
                (hex.toInt(16) * 100) / 255
            } else {
                // Tenta procurar apenas o byte de dados se a resposta for curta
                if (clean.startsWith("412F")) {
                    val hex = clean.substring(4, 6)
                    (hex.toInt(16) * 100) / 255
                } else -1
            }
        } catch (_: Exception) { -1 }
    }

    private fun parseVolt(response: String): String {
        return try {
            // Resposta ATRV: "12.6V\r>"
            val clean = response.replace(">", "").trim()
            if (clean.isEmpty()) "--" else clean
        } catch (_: Exception) { "--" }
    }
}
