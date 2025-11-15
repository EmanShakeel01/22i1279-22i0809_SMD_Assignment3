package com.FEdev.i221279_i220809

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // ✅ Enable offline cache (works across whole app)
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
