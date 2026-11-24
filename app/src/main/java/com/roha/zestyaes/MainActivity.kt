package com.roha.zestyaes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.roha.zestyaes.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val keyStore = KeyStore()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Defaults
        binding.etGameName.setText("FF Advance")
        binding.etPackage.setText("com.dts.freefireadv")

        requestBatteryExemption()
        requestNotificationPermission()
        loadSavedKeys()
        checkRoot()

        binding.btnForceRoot.setOnClickListener {
            lifecycleScope.launch {
                binding.tvStatus.text = "Requesting root..."
                val ok = withContext(Dispatchers.IO) { HookEngine.requestRoot() }
                binding.tvRootStatus.text = if (ok) "Root: GRANTED ✓" else "Root: DENIED ✗"
                binding.tvStatus.text = if (ok) "Root ready" else "Open Magisk/KernelSU → allow this app"
            }
        }

        binding.btnStart.setOnClickListener {
            val pkg = binding.etPackage.text.toString().trim()
            val name = binding.etGameName.text.toString().trim().ifEmpty { "Game" }
            if (pkg.isEmpty()) {
                Toast.makeText(this, "Package required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            HookEngine.targetPackage = pkg
            HookEngine.targetName = name
            startExtractor()
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, ExtractorService::class.java))
            binding.tvStatus.text = "Stopped"
        }

        binding.btnRefresh.setOnClickListener {
            loadSavedKeys()
            checkRoot()
        }

        binding.btnClear.setOnClickListener {
            keyStore.clear()
            binding.tvKeys.text = "No keys yet"
        }
    }

    private fun startExtractor() {
        lifecycleScope.launch {
            binding.tvStatus.text = "Checking root..."
            val ok = withContext(Dispatchers.IO) { HookEngine.requestRoot() }
            if (!ok) {
                binding.tvStatus.text = "Root not granted. Tap FORCE ROOT first"
                Toast.makeText(this@MainActivity, "Grant Superuser first", Toast.LENGTH_LONG).show()
                return@launch
            }
            val i = Intent(this@MainActivity, ExtractorService::class.java).apply {
                action = ExtractorService.ACTION_START
            }
            ContextCompat.startForegroundService(this@MainActivity, i)
            binding.tvStatus.text = "Running... Open game → Login"
            Toast.makeText(this@MainActivity, "Extractor started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRoot() {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { HookEngine.suOk() }
            binding.tvRootStatus.text = if (ok) "Root: GRANTED ✓" else "Root: NOT GRANTED ✗"
        }
    }

    private fun loadSavedKeys() {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { keyStore.readAll() }
            binding.tvKeys.text = text.ifEmpty { "No keys yet" }
        }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) { }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
