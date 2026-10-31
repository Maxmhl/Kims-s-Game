package de.max.gedaechtnis.core

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.max.gedaechtnis.R
import de.max.gedaechtnis.home.HomeScreen
import de.max.gedaechtnis.home.HomeTarget
import de.max.gedaechtnis.home.HomeViewModel
import de.max.gedaechtnis.home.ProfileScreen
import de.max.gedaechtnis.home.ProfileViewModel
import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.kim.ui.DistractScreen
import de.max.gedaechtnis.kim.ui.KimViewModel
import de.max.gedaechtnis.kim.ui.LevelScreen
import de.max.gedaechtnis.kim.ui.MemorizeScreen
import de.max.gedaechtnis.kim.ui.Phase
import de.max.gedaechtnis.kim.ui.RecallScreen
import de.max.gedaechtnis.kim.ui.ResultScreen
import de.max.gedaechtnis.leitner.ui.CardListScreen
import de.max.gedaechtnis.leitner.ui.DeckScreen
import de.max.gedaechtnis.leitner.ui.DeckViewModel
import de.max.gedaechtnis.leitner.ui.ReviewScreen
import de.max.gedaechtnis.leitner.ui.ReviewViewModel
import de.max.gedaechtnis.stats.StatsScreen
import de.max.gedaechtnis.stats.StatsViewModel
import androidx.compose.ui.platform.LocalConfiguration

object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val DECKS = "decks"
    const val PROFILE = "profile"
    const val KIM_LEVEL = "kim/level"
    const val KIM_MEMORIZE = "kim/memorize"
    const val KIM_DISTRACT = "kim/distract"
    const val KIM_RECALL = "kim/recall"
    const val KIM_RESULT = "kim/result"
    const val REVIEW = "review/{deckId}"
    const val CARDS = "cards"
    fun review(deckId: Long) = "review/$deckId"
}

private val RoundRoutes = setOf(Routes.KIM_MEMORIZE, Routes.KIM_DISTRACT, Routes.KIM_RECALL)

private data class NavItem(val route: String, val label: Int, val icon: Int)

private val NavItems = listOf(
    NavItem(Routes.HOME, R.string.nav_start, R.drawable.ui_home),
    NavItem(Routes.STATS, R.string.nav_stats, R.drawable.ui_bar_chart),
    NavItem(Routes.DECKS, R.string.nav_decks, R.drawable.ui_style),
    NavItem(Routes.PROFILE, R.string.nav_profile, R.drawable.ui_person),
)

@Composable
fun AppRoot(kimVm: KimViewModel, balancing: Balancing) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val state by kimVm.state.collectAsStateWithLifecycle()
    val width = LocalConfiguration.current.screenWidthDp

    // Phase der Kim-Runde bestimmt den Bildschirm.
    LaunchedEffect(state.phase) {
        val target = when (state.phase) {
            Phase.MEMORIZE -> Routes.KIM_MEMORIZE
            Phase.DISTRACT -> Routes.KIM_DISTRACT
            Phase.RECALL -> Routes.KIM_RECALL
            Phase.RESULT -> Routes.KIM_RESULT
            Phase.IDLE -> null
        }
        val current = nav.currentDestination?.route
        if (target != null && current != target) {
            val replace = current in RoundRoutes || current == Routes.KIM_RESULT
            nav.navigate(target) {
                launchSingleTop = true
                if (replace && current != null) popUpTo(current) { inclusive = true }
            }
        } else if (target == null && current in RoundRoutes) {
            // Abbruch oder ungewertete Runde
            if (!nav.popBackStack(Routes.KIM_LEVEL, false)) nav.popBackStack(Routes.HOME, false)
        }
    }

    val topLevel = route in NavItems.map { it.route }
    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Box(Modifier.weight(1f).statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        val vm: HomeViewModel = hiltViewModel()
                        HomeScreen(
                            vm, balancing,
                            onSettings = { nav.navigateTop(Routes.PROFILE) },
                            onContinue = { target ->
                                when (target) {
                                    HomeTarget.KIM -> kimVm.startRound(width)
                                    HomeTarget.CARDS -> nav.navigate(Routes.review(0))
                                    HomeTarget.DAILY -> kimVm.startRound(width, daily = true)
                                }
                            },
                            onOpenKim = { nav.navigate(Routes.KIM_LEVEL) },
                            onOpenLeitner = { nav.navigateTop(Routes.DECKS) },
                        )
                    }
                    composable(Routes.STATS) { StatsScreen(hiltViewModel<StatsViewModel>()) }
                    composable(Routes.DECKS) {
                        DeckScreen(
                            hiltViewModel<DeckViewModel>(), onBack = { nav.navigateTop(Routes.HOME) },
                            onStartReview = { nav.navigate(Routes.review(it)) },
                            onManageCards = { nav.navigate(Routes.CARDS) },
                        )
                    }
                    composable(Routes.CARDS) { CardListScreen(hiltViewModel<DeckViewModel>(), onBack = { nav.popBackStack() }) }
                    composable(Routes.PROFILE) { ProfileScreen(hiltViewModel<ProfileViewModel>()) }
                    composable(Routes.KIM_LEVEL) { LevelScreen(kimVm, balancing, onBack = { nav.popBackStack() }) }
                    composable(Routes.KIM_MEMORIZE) { BackHandler { }; MemorizeScreen(kimVm) }
                    composable(Routes.KIM_DISTRACT) { BackHandler { }; DistractScreen(kimVm) }
                    composable(Routes.KIM_RECALL) { BackHandler { }; RecallScreen(kimVm) }
                    composable(Routes.KIM_RESULT) {
                        BackHandler { kimVm.toStart(); nav.popBackStack(Routes.HOME, false) }
                        ResultScreen(kimVm, onToStart = { nav.popBackStack(Routes.HOME, false) })
                    }
                    composable(Routes.REVIEW, arguments = listOf(navArgument("deckId") { type = NavType.LongType })) {
                        ReviewScreen(hiltViewModel<ReviewViewModel>(), onClose = { nav.popBackStack() })
                    }
                }
            }
        }
        if (topLevel) BottomBar(route, nav::navigateTop) else Box(Modifier.navigationBarsPadding())
    }
}

private fun androidx.navigation.NavController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(AppColors.SurfaceNav)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(AppColors.Outline))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp)) {
            NavItems.forEach { item ->
                val selected = item.route == current
                val color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).clickable { if (!selected) onSelect(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(painterResource(item.icon), null, tint = color, modifier = Modifier.size(24.dp))
                    Text(stringResource(item.label), style = AppText.Body12.copy(color = color))
                }
            }
        }
    }
}
