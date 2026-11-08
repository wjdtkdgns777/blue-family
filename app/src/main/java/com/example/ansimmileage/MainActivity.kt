package com.example.ansimmileage

import android.Manifest
import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ansimmileage.ui.AnsimMileageApp
import com.example.ansimmileage.ui.AnsimMileageTheme

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Drive.watchSpeed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        if (Store.settings.consented) requestMissing()

        setContent {
            AnsimMileageTheme(still = Drive.driving) {
                AnsimMileageApp(
                    driving = Drive.driving,
                    onAgree = {
                        Store.update(Store.settings.copy(consented = true))
                        Drive.start(applicationContext)
                        requestMissing()
                    },
                    onLater = { moveTaskToBack(true) },
                )
            }
        }
    }

    private fun requestMissing() {
        val missing = listOf(Manifest.permission.RECORD_AUDIO, Car.PERMISSION_SPEED).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }
}
