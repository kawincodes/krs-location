package com.krstechsolutions.krslocation.tile

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.krstechsolutions.krslocation.LocationPrefs
import com.krstechsolutions.krslocation.MainActivity
import com.krstechsolutions.krslocation.MockLocationService
import com.krstechsolutions.krslocation.MockLocationUtils
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.model.LatLng

/** Quick Settings tile that starts or stops mocking at the last used position. */
class MockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // The Intent overload is only used below API 34.
    override fun onClick() {
        super.onClick()
        if (MockLocationService.isRunning) {
            MockLocationService.stop(this)
            refresh(runningOverride = false)
            return
        }
        val ready = MockLocationUtils.isSelectedMockLocationApp(this) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (ready) {
            MockLocationService.startAtLastPosition(this)
            refresh(runningOverride = true)
        } else {
            // Setup is needed; open the app so the user can finish it.
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun refresh(runningOverride: Boolean? = null) {
        val tile = qsTile ?: return
        val running = runningOverride ?: MockLocationService.isRunning
        val prefs = LocationPrefs(this)
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) {
                getString(R.string.tile_subtitle_on)
            } else {
                LatLng(prefs.latitude, prefs.longitude).format(3)
            }
        }
        tile.updateTile()
    }
}
