package com.example.bluefamily

import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.bluefamily.ui.BlueFamilyApp
import com.example.bluefamily.ui.BlueFamilyTheme

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestPermission()) { Drive.watchSpeed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        if (Store.settings.consented) requestMissing()

        setContent {
            BlueFamilyTheme(still = Drive.driving) {
                BlueFamilyApp(
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
        if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) permissions.launch(Car.PERMISSION_SPEED)
    }
}
