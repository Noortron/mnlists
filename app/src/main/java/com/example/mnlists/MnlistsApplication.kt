package com.example.mnlists

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt requires the application to be annotated so the
 * dependency graph can be generated and attached to the Android lifecycle.
 */
@HiltAndroidApp
class MnlistsApplication : Application()
