package com.greenlight.app.data.firebase

import android.content.Context
import com.greenlight.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions

/**
 * Must match REGION in functions/src/config.ts. Keep Firestore, Functions and this value in
 * the same region (see README step 6).
 */
const val FUNCTIONS_REGION = "africa-south1"

/** Holds the Firebase singletons, optionally pointed at the local Emulator Suite (debug builds only). */
class FirebaseEnv private constructor(
    val auth: FirebaseAuth,
    val firestore: FirebaseFirestore,
    val functions: FirebaseFunctions,
    val usingEmulators: Boolean,
) {
    companion object {
        private const val PREFS = "dev_env"
        private const val KEY_EMULATORS = "use_emulators"
        private const val KEY_HOST = "emulator_host"
        const val DEFAULT_EMULATOR_HOST = "10.0.2.2" // Android emulator -> host machine

        /** Returns null when google-services.json was not supplied at build time. */
        fun create(context: Context): FirebaseEnv? {
            if (FirebaseApp.getApps(context).isEmpty() && FirebaseApp.initializeApp(context) == null) return null
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val useEmulators = BuildConfig.DEV_TOOLS && prefs.getBoolean(KEY_EMULATORS, false)
            val host = prefs.getString(KEY_HOST, DEFAULT_EMULATOR_HOST) ?: DEFAULT_EMULATOR_HOST

            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()
            val functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
            // Offline persistence lets swipes queue while offline and sync when connectivity returns.
            firestore.firestoreSettings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings { })
            }
            if (useEmulators) {
                auth.useEmulator(host, 9099)
                firestore.useEmulator(host, 8080)
                functions.useEmulator(host, 5001)
            }
            return FirebaseEnv(auth, firestore, functions, useEmulators)
        }

        fun emulatorPrefs(context: Context): Pair<Boolean, String> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_EMULATORS, false) to (prefs.getString(KEY_HOST, DEFAULT_EMULATOR_HOST) ?: DEFAULT_EMULATOR_HOST)
        }

        /** Takes effect after an app restart (Firebase can only be pointed at emulators before first use). */
        fun setEmulatorPrefs(context: Context, enabled: Boolean, host: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_EMULATORS, enabled).putString(KEY_HOST, host).commit()
        }
    }
}
