package com.obdiot.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class UserMapData(val id: String, val name: String, val pos: GeoPoint)

@Composable
fun MapScreen(onBack: () -> Unit) {

    val context = LocalContext.current

    // 🔥 Firebase
    val vibRef = remember { FirebaseDatabase.getInstance().getReference("control/vibrador") }
    val flashRef = remember { FirebaseDatabase.getInstance().getReference("control/flashlight") }
    val usersRef = remember { FirebaseDatabase.getInstance().getReference("users") }

    // 🗺 MAPA
    val mapView = remember { MapView(context) }
    val markers = remember { mutableMapOf<String, Marker>() }

    // 📊 UI STATE
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var selectedUserObd by remember { mutableStateOf<Map<String, Any>?>(null) }
    var firstLoad by remember { mutableStateOf(true) }
    
    // Lista de usuários para a "gaveta"
    var showUserList by remember { mutableStateOf(false) }
    val userList = remember { mutableStateListOf<UserMapData>() }

    // ================= MAP INIT =================
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
            Configuration.getInstance().userAgentValue = context.packageName
            mapView.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(19.0)
            }
        }
    )

    // ================= ICON SCALE =================
    fun resizeDrawable(drawable: android.graphics.drawable.Drawable?, size: Int): android.graphics.drawable.Drawable? {
        if (drawable == null) return null
        val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, false)
        return android.graphics.drawable.BitmapDrawable(context.resources, scaled)
    }

    // ================= UPDATE USER =================
    @Suppress("UNCHECKED_CAST")
    fun updateUser(child: DataSnapshot) {
        val id = child.key ?: return
        val name = child.child("name").getValue(String::class.java) ?: "Sem nome"
        val lat = child.child("lat").getValue(Double::class.java)
        val lng = child.child("lng").getValue(Double::class.java)
        val speedValue = child.child("speed").getValue(Double::class.java) ?: 0.0
        val batteryValue = child.child("battery").getValue(Int::class.java) ?: 0
        val iconType = child.child("icon").getValue(String::class.java) ?: "walk"

        if (lat == null || lng == null) return
        val pos = GeoPoint(lat, lng)

        // Atualiza lista lateral
        val index = userList.indexOfFirst { it.id == id }
        if (index != -1) {
            userList[index] = UserMapData(id, name, pos)
        } else {
            userList.add(UserMapData(id, name, pos))
        }

        // Se for o usuário selecionado, atualiza o estado de OBD apenas se os dados existirem no snapshot
        if (id == selectedUserId) {
            val obdSnap = child.child("obd")
            if (obdSnap.exists()) {
                val newData = obdSnap.value as? Map<String, Any>
                if (selectedUserObd != newData) {
                    selectedUserObd = newData
                }
            }
            // Não resetamos para null aqui para evitar que a caixinha suma durante 
            // atualizações rápidas que podem vir incompletas por algum motivo do Firebase
        }

        // 📍 primeira centralização
        if (firstLoad) {
            mapView.controller.setCenter(pos)
            firstLoad = false
        }

        val marker = markers[id] ?: Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ ->
                selectedUserId = id
                mapView.controller.animateTo(pos)
                showInfoWindow() // Força abrir ao clicar
                true
            }
            mapView.overlays.add(this)
            markers[id] = this
        }

        marker.position = pos
        marker.title = name
        
        // Balão simples (GPS + Bateria) para evitar poluição e pisca-pisca
        val newSnippet = "${String.format("%.1f", speedValue)} km/h (GPS) | Bat: $batteryValue%"

        if (marker.snippet != newSnippet) {
            marker.snippet = newSnippet
            // Mantém aberto se já estiver aberto ou se for o selecionado
            if (id == selectedUserId || marker.isInfoWindowShown) {
                marker.showInfoWindow()
            }
        }

        val rawIcon = when (iconType) {
            "car" -> ContextCompat.getDrawable(context, R.drawable.car)
            else -> ContextCompat.getDrawable(context, R.drawable.walk)
        }
        marker.icon = resizeDrawable(rawIcon, 80)
    }

    // ================= FIREBASE REALTIME =================
    DisposableEffect(Unit) {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) = updateUser(snapshot)
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = updateUser(snapshot)
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val id = snapshot.key ?: return
                markers[id]?.let { mapView.overlays.remove(it) }
                markers.remove(id)
                userList.removeAll { it.id == id }
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        usersRef.addChildEventListener(listener)
        onDispose { usersRef.removeEventListener(listener) }
    }

    // ================= UI =================
    Box(modifier = Modifier.fillMaxSize()) {
        
        // ===== TOP PANEL (Gaveta de Usuários) =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp)
                .background(Color.White.copy(alpha = 0.9f))
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Usuários: ${userList.size}", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showUserList = !showUserList }) {
                    Text(if (showUserList) "Fechar" else "Ver Lista")
                }
            }

            if (showUserList) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(userList) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUserId = user.id
                                    mapView.controller.animateTo(user.pos)
                                    showUserList = false
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(user.name)
                            Text("📍 Ver", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    }
                }
            }
        }

        // ===== DIREITA (Vibrar e Lanterna) =====
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { vibRef.get().addOnSuccessListener { snap -> vibRef.setValue(snap.value != true) } },
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Vib") }

            Button(
                onClick = { flashRef.get().addOnSuccessListener { snap -> flashRef.setValue(snap.value != true) } },
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                contentPadding = PaddingValues(0.dp)
            ) { Text("Flash", color = Color.White) }
        }

        // ===== BAIXO (Voltar) =====
        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(70.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(0.dp)
        ) { Text("Sair") }

        // ===== PAINEL OBD (Quadrado em baixo) =====
        selectedUserObd?.let { obd ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .padding(bottom = 80.dp) // Acima do botão Sair
                    .width(220.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📊 Diagnóstico Completo", style = MaterialTheme.typography.titleSmall, color = Color.DarkGray)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RPM:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${obd["rpm"] ?: "--"}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Velocidade:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${obd["speed"] ?: "--"} km/h", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Consumo Inst:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${obd["instantConsumption"] ?: "--"} km/L", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Consumo Médio:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${obd["averageConsumption"] ?: "--"} km/L", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Distância:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        val dist = obd["distanceTraveled"]?.toString()?.toDoubleOrNull() ?: 0.0
                        Text(String.format("%.2f km", dist), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Comb. Usado:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        val fuel = obd["fuelUsed"]?.toString()?.toDoubleOrNull() ?: 0.0
                        Text(String.format("%.3f L", fuel), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Temp. Motor:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${obd["temp"] ?: "--"} °C", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Nível Tanque:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${obd["fuelLevel"] ?: "--"} %", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tensão Bat.:", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${obd["volt"] ?: "--"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
