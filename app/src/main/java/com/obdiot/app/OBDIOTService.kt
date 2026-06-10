package com.obdiot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.database.*
import android.hardware.camera2.CameraManager

class OBDIOTService : Service() {

    private lateinit var vibrator: Vibrator
    private var isVibrating = false

    private lateinit var cameraManager: CameraManager
    private var cameraId: String = ""
    private var isFlashing = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val db = FirebaseDatabase.getInstance().getReference("users")
    private var userId = "user_" + System.currentTimeMillis()

    private val controlRef =
        FirebaseDatabase.getInstance().getReference("control")

    private val statusRef =
        FirebaseDatabase.getInstance().getReference("status")

    private var userName = "Usuário"
    private var userIcon = "car"

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        vibrator =
            getSystemService(VIBRATOR_SERVICE) as Vibrator


        cameraManager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraId =
            cameraManager.cameraIdList[0]
        listenCommands()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        userId = intent?.getStringExtra("userId") ?: userId
        userName = intent?.getStringExtra("name") ?: "Usuário"
        userIcon = intent?.getStringExtra("icon") ?: "car"

        startForegroundService()
        startGPS()

        return START_STICKY
    }

    private fun startForegroundService() {

        val channelId = "obdiot_location_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                channelId,
                "OBDIOT GPS",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("OBDIOT ativo")
                .setContentText("Rastreando localização em segundo plano")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build()

        startForeground(1, notification)
    }

    private fun startGPS() {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000
        ).build()

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                val loc = result.lastLocation ?: return

                val speed = loc.speed * 3.6
                val battery = getBattery()

                val data = mapOf(
                    "name" to userName,
                    "model" to Build.MODEL,
                    "icon" to userIcon,
                    "lat" to loc.latitude,
                    "lng" to loc.longitude,
                    "speed" to speed,
                    "battery" to battery,
                    "timestamp" to System.currentTimeMillis()
                )

                db.child(userId).updateChildren(data)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            mainLooper
        )
    }

    private fun listenCommands() {

        controlRef.child("vibrador")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val vibrar =
                        snapshot.getValue(Boolean::class.java) ?: false

                    if (vibrar && !isVibrating) {

                        isVibrating = true

                        statusRef.child("vibradorAtivo")
                            .setValue(true)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                            vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                    5000,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )

                        } else {

                            @Suppress("DEPRECATION")
                            vibrator.vibrate(5000)
                        }

                        Handler(Looper.getMainLooper())
                            .postDelayed({

                                isVibrating = false

                                controlRef.child("vibrador")
                                    .setValue(false)

                                statusRef.child("vibradorAtivo")
                                    .setValue(false)

                            }, 5000)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })


        controlRef.child("flashlight")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val flash =
                        snapshot.getValue(Boolean::class.java) ?: false

                    if (flash && !isFlashing) {

                        isFlashing = true

                        statusRef.child("flashAtivo")
                            .setValue(true)

                        startFlashBlink()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }


    private fun startFlashBlink() {

        Thread {

            try {

                repeat(10) {

                    cameraManager.setTorchMode(
                        cameraId,
                        true
                    )

                    Thread.sleep(300)

                    cameraManager.setTorchMode(
                        cameraId,
                        false
                    )

                    Thread.sleep(300)
                }

            } catch (_: Exception) {
            }

            isFlashing = false

            controlRef.child("flashlight")
                .setValue(false)

            statusRef.child("flashAtivo")
                .setValue(false)

        }.start()
    }
    private fun getBattery(): Int {

        val bm =
            getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        return bm.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?) = null
}