package com.savushkin.scada.mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.androidbrowserhelper.trusted.TwaLauncher

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // URL PWA приложения
        // DEV: локальный сервер (запустите "npm run dev" в папке pwa-app)
        // PROD: замените на https://your-domain.com/
        val twaUrl = "http://127.0.0.1:8000/"

        // TwaLauncher управляет подключением к сервису Custom Tabs 
        // и запуском Trusted Web Activity.
        val launcher = TwaLauncher(this)
        launcher.launch(twaUrl.toUri())
        
        // После запуска TWA в Chrome, наша активность больше не нужна.
        finish()
    }
}
