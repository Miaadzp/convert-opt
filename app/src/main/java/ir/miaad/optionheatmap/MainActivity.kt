package ir.miaad.optionheatmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
        Log.i(TAG, "POST_NOTIFICATIONS permission result=$isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // شروع سرویس مستقل از مجوز اعلان است؛ مجوز فقط روی نمایش اعلان اثر دارد.
        startDataService()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppShell()
        }
    }

    private fun startDataService() {
        try {
            Log.i(TAG, "Requesting DataSyncService start")
            val intent = Intent(this, DataSyncService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } catch (error: Exception) {
            Log.e(TAG, "Could not start DataSyncService", error)
        }
    }

    @Suppress("unused")
    private fun stopDataService() {
        val intent = Intent(this, DataSyncService::class.java).apply {
            action = DataSyncService.ACTION_STOP
        }
        startService(intent)
    }

    private companion object {
        const val TAG = "OH-DIAG-Main"
    }
}
