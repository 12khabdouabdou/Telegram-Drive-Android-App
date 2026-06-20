package com.telegramdrive.app.widget

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.telegramdrive.app.data.repository.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile that toggles auto-backup on/off.
 * Lets users pause/resume backups without opening the app — mirrors the
 * "pause backup" affordance in Google Photos' notification.
 */
@AndroidEntryPoint
class BackupToggleTileService : TileService() {

    @Inject lateinit var prefs: PreferencesRepository

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val enabled = prefs.autoBackupEnabled.first()
            updateTile(enabled)
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val now = prefs.autoBackupEnabled.first()
            prefs.setAutoBackupEnabled(!now)
            updateTile(!now)
        }
    }

    private fun updateTile(enabled: Boolean) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(com.telegramdrive.app.R.string.qs_tile_label)
            updateTile()
        }
    }
}
