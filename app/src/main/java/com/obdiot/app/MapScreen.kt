package com.obdiot.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

import com.google.firebase.database.*

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

@Composable
fun MapScreen() {

    val context = LocalContext.current

    // 🔥 Firebase
    val vibRef = remember {
        FirebaseDatabase.getInstance().getReference("control/vibrador")
    }

    val flashRef = remember {
        FirebaseDatabase.getInstance().getReference("control/flashlight")
    }

    val usersRef = remember {
        FirebaseDatabase.getInstance().getReference("users")
    }

    // 🗺️ MAPA
    val mapView = remember { MapView(context) }
    val markers = remember { mutableMapOf<String, Marker>() }

    // 📊 UI STATE
    var selectedUser by remember { mutableStateOf("Nenhum") }
    var speed by remember { mutableStateOf(0.0) }
    var battery by remember { mutableStateOf(0) }

    var firstLoad by remember { mutableStateOf(true) }

    // ================= MAPA =================
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {

            Configuration.getInstance().load(
                context,
                PreferenceManager.getDefaultSharedPreferences(context)
            )

            Configuration.getInstance().userAgentValue = context.packageName

            mapView.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(19.0)
            }
        }
    )

    // ================= FUNÇÃO DE ESCALA =================
    fun resizeDrawable(drawable: Drawable?, size: Int): Drawable? {
        if (drawable == null) return null

        val bitmap = (drawable as BitmapDrawable).bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, false)

        return BitmapDrawable(context.resources, scaled)
    }

    // ================= FIREBASE =================
    DisposableEffect(Unit) {

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                for (child in snapshot.children) {

                    val id = child.key ?: continue

                    val name = child.child("name").getValue(String::class.java) ?: "Sem nome"
                    val lat = child.child("lat").getValue(Double::class.java)
                    val lng = child.child("lng").getValue(Double::class.java)

                    val speedValue = child.child("speed").getValue(Double::class.java) ?: 0.0
                    val batteryValue = child.child("battery").getValue(Int::class.java) ?: 0

                    val iconType = child.child("icon").getValue(String::class.java) ?: "walk"

                    if (lat == null || lng == null) continue

                    val pos = GeoPoint(lat, lng)

                    // 📍 centraliza primeira vez
                    if (firstLoad) {
                        mapView.controller.setCenter(pos)
                        firstLoad = false
                    }

                    // ================= MARKER =================
                    val marker = markers[id] ?: Marker(mapView).apply {

                        setAnchor(
                            Marker.ANCHOR_CENTER,
                            Marker.ANCHOR_BOTTOM
                        )

                        setOnMarkerClickListener { _, _ ->
                            selectedUser = name
                            speed = speedValue
                            battery = batteryValue

                            mapView.controller.animateTo(pos)
                            true
                        }

                        mapView.overlays.add(this)
                        markers[id] = this
                    }

                    marker.position = pos
                    marker.title = name

                    // ================= ICON RESIZE =================
                    val rawIcon = when (iconType) {
                        "car" -> ContextCompat.getDrawable(context, R.drawable.car)
                        else -> ContextCompat.getDrawable(context, R.drawable.walk)
                    }

                    marker.icon = resizeDrawable(rawIcon, 80) // 👈 TAMANHO AQUI
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        usersRef.addValueEventListener(listener)

        onDispose {
            usersRef.removeEventListener(listener)
        }
    }

    // ================= UI OVERLAY =================
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ===== TOP PANEL ======
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f))
                .padding(10.dp)
        ) {
            Text("Usuário: $selectedUser")
            Text("Velocidade: ${String.format("%.1f", speed)} km/h")
            Text("Bateria: $battery%")
        }

        // ===== BOTÕES =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {

            Button(
                onClick = {
                    vibRef.get().addOnSuccessListener { snap ->
                        vibRef.setValue(snap.value != true)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VIBRADOR TOGGLE")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    flashRef.get().addOnSuccessListener { snap ->
                        flashRef.setValue(snap.value != true)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("LANTERNA TOGGLE", color = Color.White)
            }
        }
    }
}