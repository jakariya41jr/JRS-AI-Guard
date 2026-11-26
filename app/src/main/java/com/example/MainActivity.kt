package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.export.ExportFormat
import com.example.security.GuardOperatingMode
import com.example.ui.MainViewModel
import com.example.ui.UiToastEvent
import com.example.ui.components.JrsShieldLogo
import com.example.ui.components.QuickActionControlSheet
import com.example.ui.screens.AiGuardScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.ScannerAuditScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.theme.JrsAiGuardTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable object GuardRoute
@Serializable object TimelineRoute
@Serializable object ScannerRoute
@Serializable object NetworkRoute
@Serializable object AiChatRoute

private data class NavDest(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JrsAiGuardTheme {
                JrsAiGuardRootApp()
            }
        }
    }
}

@Composable
private fun JrsAiGuardRootApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val vaultConfig by viewModel.vaultConfig.collectAsStateWithLifecycle()
    val timelineEvents by viewModel.timelineEvents.collectAsStateWithLifecycle()
    val networkSessions by viewModel.networkSessions.collectAsStateWithLifecycle()
    val appTrustList by viewModel.appTrustList.collectAsStateWithLifecycle()
    val scanHistory by viewModel.scanHistory.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val aiInsights by viewModel.aiInsights.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val integrityReport by viewModel.integrityReport.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isScanningTelemetry.collectAsStateWithLifecycle()
    val isMalwareScanning by viewModel.isMalwareScanning.collectAsStateWithLifecycle()
    val lastMalwareScan by viewModel.lastMalwareScan.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val quickActionTarget by viewModel.quickActionTarget.collectAsStateWithLifecycle()
    val isAiBusy by viewModel.isAiBusy.collectAsStateWithLifecycle()
    val githubRelease by viewModel.githubRelease.collectAsStateWithLifecycle()
    val isCheckingRelease by viewModel.isCheckingRelease.collectAsStateWithLifecycle()

    var showSplash by remember { mutableStateOf(true) }
    var pendingExport by remember { mutableStateOf<Pair<ExportFormat, String>?>(null) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.second.toByteArray())
                }
            }
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* handled gracefully */ }

    LaunchedEffect(Unit) {
        delay(950)
        showSplash = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiToastEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is UiToastEvent.ShareIntent -> {
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(event.intent, "Share JRS AI Guard Report")
                        )
                    }
                }
                is UiToastEvent.ExportReady -> {
                    pendingExport = event.format to event.content
                    createDocLauncher.launch("jrs_ai_guard_report.${event.format.extension}")
                }
            }
        }
    }

    val destinations = remember {
        listOf(
            NavDest(GuardRoute, "Guard", Icons.Rounded.Shield, Icons.Outlined.Shield, "nav_guard"),
            NavDest(TimelineRoute, "Timeline", Icons.Rounded.History, Icons.Outlined.History, "nav_timeline"),
            NavDest(ScannerRoute, "Scanner", Icons.Rounded.BugReport, Icons.Outlined.BugReport, "nav_scanner"),
            NavDest(NetworkRoute, "Network", Icons.Rounded.Router, Icons.Outlined.Router, "nav_network"),
            NavDest(AiChatRoute, "AI Guard", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai")
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(ObsidianVoid, ObsidianDeep, ObsidianVoid))
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 700.dp

            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = ObsidianDeep.copy(alpha = 0.96f),
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentDest?.hasRoute(dest.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NeonCyan,
                                        selectedTextColor = NeonCyan,
                                        indicatorColor = NeonCyan.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = ObsidianDeep.copy(alpha = 0.95f),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(Modifier.height(12.dp))
                            JrsShieldLogo(size = 40.dp)
                            Spacer(Modifier.height(16.dp))
                            destinations.forEach { dest ->
                                val selected = currentDest?.hasRoute(dest.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = NeonCyan,
                                        selectedTextColor = NeonCyan,
                                        indicatorColor = NeonCyan.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = GuardRoute,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable<GuardRoute> {
                            DashboardScreen(
                                integrityReport = integrityReport,
                                vaultConfig = vaultConfig,
                                events = timelineEvents,
                                apps = appTrustList,
                                networkSessions = networkSessions,
                                isRefreshing = isRefreshing,
                                onRefresh = viewModel::refreshAll,
                                onToggleWatchdog = { enabled ->
                                    viewModel.toggleWatchdog(
                                        enabled,
                                        vaultConfig.screenOffSensorAlert,
                                        vaultConfig.rootRealtimeWatch
                                    )
                                },
                                onGrantUsageAccess = viewModel::openUsageAccessSettings,
                                onCaptureSnapshot = { viewModel.captureSnapshotNow() },
                                onQuickActions = viewModel::openQuickActionsForPackage,
                                onOpenCategory = { cat ->
                                    viewModel.selectCategoryFilter(cat)
                                    navigateTo(TimelineRoute)
                                },
                                onOpenOfficialWebsite = viewModel::openOfficialWebsite
                            )
                        }

                        composable<TimelineRoute> {
                            TimelineScreen(
                                events = timelineEvents,
                                selectedCategory = selectedCategory,
                                onSelectCategory = viewModel::selectCategoryFilter,
                                onQuickActions = viewModel::openQuickActionsForPackage
                            )
                        }

                        composable<ScannerRoute> {
                            ScannerAuditScreen(
                                apps = appTrustList,
                                scanHistory = scanHistory,
                                lastScan = lastMalwareScan,
                                isScanning = isMalwareScanning,
                                integrityReport = integrityReport,
                                snapshots = snapshots,
                                vaultConfig = vaultConfig,
                                onScanPickedFile = viewModel::scanPickedFileUri,
                                onScanInstalledApp = viewModel::scanInstalledAppApk,
                                onOpenQuickActions = viewModel::openQuickActionsForPackage,
                                onCaptureSnapshot = { viewModel.captureSnapshotNow() },
                                onShareReport = viewModel::shareExportReport,
                                onSaveReportFile = viewModel::prepareExportFile
                            )
                        }

                        composable<NetworkRoute> {
                            val isRoot = integrityReport?.rootStatus?.mode == GuardOperatingMode.ROOTED_ACTIVE
                            NetworkScreen(
                                sessions = networkSessions,
                                isRootActive = isRoot,
                                onToggleBlock = viewModel::toggleNetworkBlock,
                                onQuickActions = viewModel::openQuickActionsForPackage
                            )
                        }

                        composable<AiChatRoute> {
                            AiGuardScreen(
                                vaultConfig = vaultConfig,
                                chatMessages = chatMessages,
                                aiInsights = aiInsights,
                                isAiBusy = isAiBusy,
                                githubRelease = githubRelease,
                                isCheckingRelease = isCheckingRelease,
                                onSendChat = viewModel::sendChatMessage,
                                onClearChat = viewModel::clearChat,
                                onSaveGeminiKey = viewModel::saveGeminiKey,
                                onTestGeminiKey = viewModel::testGeminiKey,
                                onUpdateSchedule = viewModel::updateAiSchedule,
                                onRunDigestNow = viewModel::runAiDigestNow,
                                onCheckGitHubRelease = viewModel::checkGitHubRelease,
                                onDownloadReleaseApk = viewModel::downloadReleaseApk,
                                onOpenOfficialWebsite = viewModel::openOfficialWebsite
                            )
                        }
                    }
                }
            }
        }

        quickActionTarget?.let { target ->
            val isRoot = integrityReport?.rootStatus?.mode == GuardOperatingMode.ROOTED_ACTIVE
            QuickActionControlSheet(
                target = target,
                isRootActive = isRoot,
                onDismiss = viewModel::dismissQuickActions,
                onForceStop = viewModel::performForceStop,
                onFreeze = viewModel::performFreeze,
                onRevokePerm = viewModel::performRevokePermission,
                onUpdateTrust = viewModel::updateAppTrust
            )
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianVoid)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    JrsShieldLogo(size = 84.dp)
                    Text(
                        text = "JRS AI Guard",
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${BuildConfig.APPLICATION_ID} • Md Jakariya Hasan",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan
                    )
                }
            }
        }
    }
}
