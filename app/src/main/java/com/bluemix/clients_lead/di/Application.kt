package com.bluemix.clients_lead.di

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.bluemix.clients_lead.features.location.LocationTrackerService
import com.bluemix.clients_lead.di.meetingModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber
import com.bluemix.clients_lead.BuildConfig

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ CRITICAL: Set global exception handler FIRST
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "💥 UNCAUGHT EXCEPTION in ${thread.name}")
            android.util.Log.e("App", "CRASH: ${throwable.javaClass.simpleName}: ${throwable.message}")
            // Let the default handler finish
            throw throwable
        }

        // ✅ CRITICAL: Initialize Timber FIRST
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // ✅ Test that Timber works
        Timber.d("🚀 App Started - Timber is working!")

        try {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@App)
                modules(
                    appModule,
                    authModule,
                    clientModule,
                    locationModule,
                    profileModule,
                    expenseModule,
                    meetingModule,
                    adminModule,
                    paymentModule
                )
            }
            Timber.d("✅ Koin initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error initializing Koin")
            throw e
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Location tracking channel
            val locationChannel = NotificationChannel(
                LocationTrackerService.LOCATION_CHANNEL,
                "Location Tracking",  // User-visible name
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows your current location while tracking is active"
                // Optional: Disable sound for this channel
                setSound(null, null)
            }

            // Get notification manager
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create the channel
            notificationManager.createNotificationChannel(locationChannel)

            // Add more channels here if needed in the future
            // notificationManager.createNotificationChannel(otherChannel)
        }
    }
}