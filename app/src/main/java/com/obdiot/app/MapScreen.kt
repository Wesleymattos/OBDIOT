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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource

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

    // 🗺 MAPA
    val mapView = remember { MapView(context) }
    val markers = remember { mutableMapOf<String, Marker>() }

    // 📊 UI STATE (CORRIGIDO)
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var selectedUserName by remember { mutableStateOf("Nenhum") }

    var speed by remember { mutableStateOf(0.0) }
    var battery by remember { mutableStateOf(0) }

    var firstLoad by remember { mutableStateOf(true) }
    var showBluetooth by remember { mutableStateOf(false) }

    var bluetoothConnected by remember {
        mutableStateOf(false)
    }

    if (showBluetooth) {

        BluetoothScreen(
            onBack = {
                showBluetooth = false
            }
        )

        return
    }

    // ================= MAP INIT =================
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

    // ================= ICON SCALE =================
    fun resizeDrawable(drawable: android.graphics.drawable.Drawable?, size: Int)
            : android.graphics.drawable.Drawable? {

        if (drawable == null) return null

        val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, false)

        return android.graphics.drawable.BitmapDrawable(context.resources, scaled)
    }

    // ================= UPDATE USER =================
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

        // 📍 primeira centralização
        if (firstLoad) {
            mapView.controller.setCenter(pos)
            firstLoad = false
        }

        val marker = markers[id] ?: Marker(mapView).apply {

            setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )

            setOnMarkerClickListener { _, _ ->

                selectedUserId = id
                selectedUserName = name
                speed = speedValue
                battery = batteryValue

                mapView.controller.animateTo(pos)
                true
            }

            mapView.overlays.add(this)
            markers[id] = this
        }

        // 🔁 MOVE EM TEMPO REAL
        marker.position = pos
        marker.title = name

        // 🚗 / 👤 ICON
        val rawIcon = when (iconType) {
            "car" -> ContextCompat.getDrawable(context, R.drawable.car)
            else -> ContextCompat.getDrawable(context, R.drawable.walk)
        }

        marker.icon = resizeDrawable(rawIcon, 80)

        // ================= UI UPDATE REALTIME =================
        if (id == selectedUserId) {
            selectedUserName = name
            speed = speedValue
            battery = batteryValue
        }
    }

    // ================= FIREBASE REALTIME =================
    DisposableEffect(Unit) {

        val listener = object : ChildEventListener {

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                updateUser(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                updateUser(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val id = snapshot.key ?: return
                markers[id]?.let {
                    mapView.overlays.remove(it)
                    markers.remove(id)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {}
        }

        usersRef.addChildEventListener(listener)

        onDispose {
            usersRef.removeEventListener(listener)
        }
    }

    // ================= UI =================
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ===== TOP PANEL =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {

                Text("Usuário: $selectedUserName")
                Text("Velocidade: ${String.format("%.1f", speed)} km/h")
                Text("Bateria: $battery%")

            }

            Image(
                painter = painterResource(
                    id = if (bluetoothConnected)
                        R.drawable.bluetooth_blue
                    else
                        R.drawable.bluetooth_gray
                ),
                contentDescription = "Bluetooth",
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        showBluetooth = true
                    }
            )
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
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
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
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("LANTERNA TOGGLE", color = Color.White)
            }
        }
    }
}