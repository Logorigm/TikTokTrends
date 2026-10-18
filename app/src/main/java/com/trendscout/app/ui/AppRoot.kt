package com.trendscout.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trendscout.app.AppContainer
import com.trendscout.app.R
import com.trendscout.app.TrendScoutApp
import com.trendscout.app.ui.compare.CompareScreen
import com.trendscout.app.ui.detail.TrendDetailScreen
import com.trendscout.app.ui.detail.VideoDetailScreen
import com.trendscout.app.ui.feed.FeedScreen
import com.trendscout.app.ui.ideas.IdeaDetailScreen
import com.trendscout.app.ui.ideas.IdeaGeneratorScreen
import com.trendscout.app.ui.library.CollectionScreen
import com.trendscout.app.ui.library.LibraryScreen
import com.trendscout.app.ui.search.AddTrendScreen
import com.trendscout.app.ui.search.AddVideoScreen
import com.trendscout.app.ui.search.SearchScreen
import com.trendscout.app.ui.settings.AboutDataScreen
import com.trendscout.app.ui.settings.SettingsScreen
import com.trendscout.app.ui.theme.Palette

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as TrendScoutApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

object Routes {
    const val FEED = "feed"
    const val SEARCH = "search"
    const val COMPARE = "compare"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ADD_TREND = "add_trend"

    fun video(id: String) = "video/" + Uri.encode(id)
    fun trend(id: String) = "trend/" + Uri.encode(id)
    fun addVideo(url: String? = null) = "add_video?url=" + Uri.encode(url ?: "")
    fun ideas(trendId: String?, videoId: String?) = "ideas?trendId=" + Uri.encode(trendId ?: "") + "&videoId=" + Uri.encode(videoId ?: "")
    fun idea(id: Long) = "idea/$id"
    fun collection(id: Long) = "collection/$id"
}

/** Navigation actions shared by screens. */
class Nav(private val c: NavHostController) {
    fun back() { c.popBackStack() }
    fun video(id: String) = c.navigate(Routes.video(id))
    fun trend(id: String) = c.navigate(Routes.trend(id))
    fun addVideo(url: String? = null) = c.navigate(Routes.addVideo(url))
    fun addTrend() = c.navigate(Routes.ADD_TREND)
    fun ideas(trendId: String?, videoId: String?) = c.navigate(Routes.ideas(trendId, videoId))
    fun idea(id: Long) = c.navigate(Routes.idea(id))
    fun collection(id: Long) = c.navigate(Routes.collection(id))
    fun about() = c.navigate(Routes.ABOUT)
    fun settings() = tab(Routes.SETTINGS)
    fun tab(route: String) = c.navigate(route) {
        popUpTo(c.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.FEED, R.string.tab_trends, Icons.Outlined.TrendingUp),
    Tab(Routes.SEARCH, R.string.tab_search, Icons.Outlined.Search),
    Tab(Routes.COMPARE, R.string.tab_compare, Icons.Outlined.CompareArrows),
    Tab(Routes.LIBRARY, R.string.tab_library, Icons.Outlined.BookmarkBorder),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Outlined.Settings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(sharedLink: String?, onSharedLinkConsumed: () -> Unit) {
    val controller = rememberNavController()
    val nav = Nav(controller)
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(sharedLink) {
        if (sharedLink != null) {
            nav.addVideo(sharedLink)
            onSharedLinkConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = Palette.Surface) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.tab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Bg,
                                indicatorColor = Palette.Cyan,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = Routes.FEED, modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
            composable(Routes.FEED) { FeedScreen(nav) }
            composable(Routes.SEARCH) { SearchScreen(nav) }
            composable(Routes.COMPARE) { CompareScreen(nav) }
            composable(Routes.LIBRARY) { LibraryScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.ABOUT) { AboutDataScreen(nav) }
            composable(Routes.ADD_TREND) { AddTrendScreen(nav) }
            composable("video/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                VideoDetailScreen(nav, it.arguments?.getString("id").orEmpty())
            }
            composable("trend/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                TrendDetailScreen(nav, it.arguments?.getString("id").orEmpty())
            }
            composable(
                "add_video?url={url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" }),
            ) { AddVideoScreen(nav, it.arguments?.getString("url").orEmpty()) }
            composable(
                "ideas?trendId={trendId}&videoId={videoId}",
                arguments = listOf(
                    navArgument("trendId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("videoId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                IdeaGeneratorScreen(
                    nav,
                    it.arguments?.getString("trendId")?.takeIf { s -> s.isNotEmpty() },
                    it.arguments?.getString("videoId")?.takeIf { s -> s.isNotEmpty() },
                )
            }
            composable("idea/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                IdeaDetailScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("collection/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                CollectionScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
        }
    }
}
