package com.sadday.app

import android.os.Bundle
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Previene screenshots y oculta contenido en el app switcher (MASVS-STORAGE-2).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
