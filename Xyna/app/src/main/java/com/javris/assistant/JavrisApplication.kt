package com.javris.assistant

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JavrisApplication : Application(), ComponentCallbacks2 {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        lateinit var instance: JavrisApplication
            private set
            
        fun getAppContext(): Context = instance.applicationContext
    }

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        
        super.onCreate()
        instance = this
        
        // Set up crash reporting
        setupCrashHandler()
        
        // Initialize components asynchronously
        applicationScope.launch(Dispatchers.Default) {
            initializeComponents()
        }
        
        // Set default night mode based on system
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log crash
            android.util.Log.e("Javris", "Uncaught exception in thread ${thread.name}", throwable)
            
            // Save crash report
            saveCrashReport(throwable)
            
            // Restart app if needed
            restartApp()
        }
    }
    
    private fun saveCrashReport(throwable: Throwable) {
        try {
            val crashTime = System.currentTimeMillis()
            val crashReport = buildString {
                append("Crash Report - $crashTime\n")
                append("Device: ${android.os.Build.MODEL}\n")
                append("Android: ${android.os.Build.VERSION.RELEASE}\n")
                append("Stack Trace:\n")
                append(throwable.stackTraceToString())
            }
            
            openFileOutput("crash_report.txt", Context.MODE_PRIVATE).use {
                it.write(crashReport.toByteArray())
            }
        } catch (e: Exception) {
            // Ignore if we can't save crash report
        }
    }
    
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .penaltyLog()
                .build()
        )
    }

    private fun initializeComponents() {
        // Initialize Python with optimized settings
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
            optimizePythonInterpreter()
        }
        
        // Initialize other components with memory optimizations
        initializeWithMemoryOptimizations()
    }
    
    private fun optimizePythonInterpreter() {
        val py = Python.getInstance()
        // Set aggressive garbage collection
        py.getModule("gc").callAttr("set_threshold", 700, 10, 5)
        // Disable debug features in production
        if (!BuildConfig.DEBUG) {
            py.getModule("sys").callAttr("setrecursionlimit", 1000)
        }
    }
    
    private fun initializeWithMemoryOptimizations() {
        // Set smaller cache sizes for image loading
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of available memory for cache
        
        // Initialize image loader with reduced memory usage
        val imageLoaderConfig = com.bumptech.glide.GlideBuilder()
            .setMemoryCache(com.bumptech.glide.load.engine.cache.LruResourceCache(cacheSize.toLong()))
            .setBitmapPool(com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool(cacheSize.toLong()))
            .setDefaultRequestOptions(
                com.bumptech.glide.request.RequestOptions()
                    .format(android.graphics.Bitmap.CompressFormat.JPEG)
                    .encodeQuality(80)
            )
        
        com.bumptech.glide.Glide.init(this, imageLoaderConfig)
        
        // Enable memory trimming when app goes to background
        registerActivityLifecycleCallbacks(AppLifecycleCallbacks())
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Clear all caches
                com.bumptech.glide.Glide.get(this).clearMemory()
                System.gc()
                Python.getInstance().getModule("gc").callAttr("collect")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                // Clear part of the cache
                com.bumptech.glide.Glide.get(this).trimMemory(level)
            }
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        // Clear all caches
        com.bumptech.glide.Glide.get(this).clearMemory()
        System.gc()
        Python.getInstance().getModule("gc").callAttr("collect")
    }
    
    private inner class AppLifecycleCallbacks : android.app.Application.ActivityLifecycleCallbacks {
        private var activeActivities = 0
        
        override fun onActivityStarted(activity: android.app.Activity) {
            activeActivities++
        }
        
        override fun onActivityStopped(activity: android.app.Activity) {
            activeActivities--
            if (activeActivities == 0) {
                // App went to background, trim memory
                applicationScope.launch(Dispatchers.Default) {
                    System.gc()
                    Python.getInstance().getModule("gc").callAttr("collect")
                }
            }
        }
        
        override fun onActivityCreated(activity: android.app.Activity, bundle: android.os.Bundle?) {}
        override fun onActivityResumed(activity: android.app.Activity) {}
        override fun onActivityPaused(activity: android.app.Activity) {}
        override fun onActivitySaveInstanceState(activity: android.app.Activity, bundle: android.os.Bundle) {}
        override fun onActivityDestroyed(activity: android.app.Activity) {}
    }
} 