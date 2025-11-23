package com.FEdev.i221279_i220809.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Utility class for checking network connectivity status
 */
object NetworkUtils {
    
    private const val TAG = "NetworkUtils"
    
    /**
     * Check if device has internet connection
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Check if device is connected to WiFi
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Check if device is connected to mobile data
     */
    fun isMobileConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    
    /**
     * Get network type as string
     */
    fun getNetworkType(context: Context): String {
        if (!isNetworkAvailable(context)) return "NONE"
        
        return when {
            isWifiConnected(context) -> "WIFI"
            isMobileConnected(context) -> "MOBILE"
            else -> "OTHER"
        }
    }
    
    /**
     * Log network status
     */
    fun logNetworkStatus(context: Context) {
        val networkType = getNetworkType(context)
        val isOnline = isNetworkAvailable(context)
        
        Log.d(TAG, "🌐 Network Status: $networkType, Online: $isOnline")
    }
}