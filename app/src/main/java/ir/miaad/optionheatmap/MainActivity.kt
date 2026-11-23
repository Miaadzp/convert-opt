package ir.miaad.optionheatmap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            val status = remember { mutableStateOf("سرویس در حال اجرا است") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "زنجیره آپشن Heatmap")
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = status.value)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = {
                    startDataService()
                    status.value = "سرویس دوباره شروع شد"
                }) {
                    Text("شروع مجدد سرویس")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    stopDataService()
                    status.value = "سرویس متوقف شد"
                }) {
                    Text("توقف سرویس")
                }
            }
        }
    }

    private fun startDataService() {
        val intent = Intent(this, DataSyncService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopDataService() {
        val intent = Intent(this, DataSyncService::class.java).apply {
            action = DataSyncService.ACTION_STOP
        }
        startService(intent)
    }
}
