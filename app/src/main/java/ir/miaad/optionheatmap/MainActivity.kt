package ir.miaad.optionheatmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ir.miaad.optionheatmap.core.service.DataSyncService

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startDataService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // درخواست مجوز اعلان در اندروید ۱۳ به بالا
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startDataService()
            }
        } else {
            startDataService()
        }

        setContent {
            AppShell()
        }
    }

    private fun startDataService() {
        val intent = Intent(this, DataSyncService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    @Suppress("unused")
    private fun stopDataService() {
        val intent = Intent(this, DataSyncService::class.java).apply {
            action = DataSyncService.ACTION_STOP
        }
        startService(intent)
    }
}
