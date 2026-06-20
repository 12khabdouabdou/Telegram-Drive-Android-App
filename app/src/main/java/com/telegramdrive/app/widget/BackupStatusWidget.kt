package com.telegramdrive.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.telegramdrive.app.MainActivity
import com.telegramdrive.app.R
import com.telegramdrive.app.data.repository.PreferencesRepository
import dagger.hilt.android.EntryPointAccessors

/**
 * Material 3 backup-status widget. Shows current auto-backup state and the
 * count of items queued. Tapping opens the app at the Backup tab.
 */
class BackupStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = EntryPointAccessors.fromApplication(context.applicationContext, PrefsEntryPoint::class.java).prefs()
        val enabled = kotlinx.coroutines.flow.first(prefs.autoBackupEnabled)

        provideContent {
            GlanceTheme {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Text(
                        text = context.getString(R.string.widget_backup_title),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium)
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    val status = if (!enabled) context.getString(R.string.widget_backup_paused)
                    else context.getString(R.string.widget_backup_uptodate)
                    Text(
                        text = status,
                        style = TextStyle(color = GlanceTheme.colors.primary)
                    )
                }
            }
        }
    }
}

class BackupStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BackupStatusWidget()
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PrefsEntryPoint {
    fun prefs(): PreferencesRepository
}
