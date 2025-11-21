package com.savushkin.scada.mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.androidbrowserhelper.trusted.TwaLauncher

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Обновляем URL на публичный адрес GitHub Pages
        val twaUrl = "https://savushkin-dev.github.io/scada-mobile/"

        val launcher = TwaLauncher(this)
        launcher.launch(twaUrl.toUri())
        
        finish()
    }
}
