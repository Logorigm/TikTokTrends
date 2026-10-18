package com.trendscout.app

import android.content.Context
import com.trendscout.app.data.db.AppDatabase
import com.trendscout.app.data.prefs.SecretStore
import com.trendscout.app.data.prefs.SettingsRepository
import com.trendscout.app.data.remote.FeedClient
import com.trendscout.app.data.remote.LlmClient
import com.trendscout.app.data.remote.Net
import com.trendscout.app.data.remote.OEmbedClient
import com.trendscout.app.data.repo.AiService
import com.trendscout.app.data.repo.FeedSyncService
import com.trendscout.app.data.repo.LibraryRepository
import com.trendscout.app.data.repo.VideoLookupService

/** Manual dependency container: one instance per process. New sources plug in here. */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db: AppDatabase = AppDatabase.create(app)
    val net = Net(app)
    val settings = SettingsRepository(app)
    val secrets = SecretStore(app)
    val library = LibraryRepository(db)
    val lookup = VideoLookupService(OEmbedClient(net), library)
    val feedSync = FeedSyncService(FeedClient(net), settings, secrets, library)
    val ai = AiService(LlmClient(net), settings, secrets)
}
