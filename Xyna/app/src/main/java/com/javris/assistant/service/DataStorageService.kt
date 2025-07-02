package com.javris.assistant.service

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.drive.Drive
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class DataStorageService(private val context: Context) {
    
    private val gson = Gson()
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_storage",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    var isGDriveEnabled: Boolean
        get() = preferences.getBoolean("gdrive_enabled", false)
        set(value) = preferences.edit().putBoolean("gdrive_enabled", value).apply()
    
    suspend fun saveData(key: String, data: Any) {
        val jsonData = gson.toJson(data)
        
        // Always save locally first
        saveLocally(key, jsonData)
        
        // If GDrive is enabled, sync to cloud
        if (isGDriveEnabled) {
            try {
                saveToGDrive(key, jsonData)
            } catch (e: Exception) {
                // Handle sync failure
            }
        }
    }
    
    suspend fun <T> getData(key: String, type: Class<T>): T? {
        // Try GDrive first if enabled
        if (isGDriveEnabled) {
            try {
                val cloudData = getFromGDrive(key)
                if (cloudData != null) {
                    return gson.fromJson(cloudData, type)
                }
            } catch (e: Exception) {
                // Fall back to local data
            }
        }
        
        // Get local data
        return getLocally(key, type)
    }
    
    private suspend fun saveLocally(key: String, data: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(key, data).apply()
        
        // Also save to file for larger data
        val file = File(context.filesDir, "${key}.json")
        file.writeText(data)
    }
    
    private suspend fun <T> getLocally(key: String, type: Class<T>): T? = withContext(Dispatchers.IO) {
        try {
            // Try encrypted prefs first
            val prefsData = encryptedPrefs.getString(key, null)
            if (!prefsData.isNullOrEmpty()) {
                return@withContext gson.fromJson(prefsData, type)
            }
            
            // Try file storage
            val file = File(context.filesDir, "${key}.json")
            if (file.exists()) {
                val data = file.readText()
                return@withContext gson.fromJson(data, type)
            }
        } catch (e: Exception) {
            // Handle error
        }
        return@withContext null
    }
    
    private suspend fun saveToGDrive(key: String, data: String) = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext
        val driveClient = Drive.getDriveClient(context, account)
        val driveResourceClient = Drive.getDriveResourceClient(context, account)
        
        // Implement Google Drive save logic
        // This is a placeholder - actual implementation would need proper Drive API usage
    }
    
    private suspend fun getFromGDrive(key: String): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        val driveClient = Drive.getDriveClient(context, account)
        val driveResourceClient = Drive.getDriveResourceClient(context, account)
        
        // Implement Google Drive retrieval logic
        // This is a placeholder - actual implementation would need proper Drive API usage
        return@withContext null
    }
    
    fun getBackupStatus(): Flow<BackupStatus> = flow {
        while (true) {
            val status = if (isGDriveEnabled) {
                try {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null) {
                        BackupStatus.SYNCED
                    } else {
                        BackupStatus.NOT_CONFIGURED
                    }
                } catch (e: Exception) {
                    BackupStatus.ERROR
                }
            } else {
                BackupStatus.DISABLED
            }
            emit(status)
            kotlinx.coroutines.delay(30000) // Check every 30 seconds
        }
    }
    
    enum class BackupStatus {
        DISABLED,
        NOT_CONFIGURED,
        SYNCING,
        SYNCED,
        ERROR
    }
} 