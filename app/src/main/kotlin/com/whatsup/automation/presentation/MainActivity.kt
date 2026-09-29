package com.whatsup.automation.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.whatsup.automation.data.security.SessionKeystore
import com.whatsup.automation.presentation.navigation.ModernBottomNavBar
import com.whatsup.automation.presentation.navigation.Screen
import com.whatsup.automation.presentation.screens.dashboard.DashboardScreen
import com.whatsup.automation.presentation.screens.dashboard.DashboardViewModel
import com.whatsup.automation.presentation.screens.logs.LogsScreen
import com.whatsup.automation.presentation.screens.logs.LogsViewModel
import com.whatsup.automation.presentation.screens.pairing.PairingScreen
import com.whatsup.automation.presentation.screens.pairing.PairingViewModel
import com.whatsup.automation.presentation.screens.rules.RulesScreen
import com.whatsup.automation.presentation.screens.rules.RulesViewModel
import com.whatsup.automation.presentation.screens.groups.GroupsScreen
import com.whatsup.automation.presentation.screens.groups.GroupsViewModel
import com.whatsup.automation.presentation.screens.statuses.StatusesScreen
import com.whatsup.automation.presentation.screens.statuses.StatusesViewModel
import com.whatsup.automation.presentation.theme.DarkBgPrimary
import com.whatsup.automation.presentation.theme.WhatsUpTheme
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.service.ServicePermissionManager
import com.whatsup.automation.service.WhatsAppForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.whatsup.automation.data.local.contacts.ContactsSyncManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * النشاط الرئيسي للتطبيق — مهيأ بـ Hilt و Jetpack Compose مع دعم كامل للاتجاه من اليمين لليسار (RTL).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionKeystore: SessionKeystore
    @Inject
    lateinit var whatsAppEngine: WhatsAppEngine

    @Inject
    lateinit var contactsSyncManager: ContactsSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // تشغيل الخدمة الأمامية وتحديث حالة الاتصال الحقيقية
        WhatsAppForegroundService.start(this)
        whatsAppEngine.refreshRealConnectionState()
        
        lifecycleScope.launch {
            contactsSyncManager.syncContacts()
        }

        setContent {
            WhatsUpTheme {
                // فرض الاتجاه من اليمين إلى اليسار (RTL) للغة العربية
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val context = LocalContext.current

                    // إعداد طلب الأذونات الحيوية (جهات الاتصال والإشعارات) تلقائياً عند التشغيل
                    val permissionsToRequest = remember {
                        buildList {
                            add(Manifest.permission.READ_CONTACTS)
                            add(Manifest.permission.WRITE_CONTACTS)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        val hasAll = permissionsToRequest.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        if (!hasAll) {
                            permissionLauncher.launch(permissionsToRequest)
                        }

                        // فحص وطلب استثناء تحسين استهلاك البطارية لضمان الحصانة الدائمة في الخلفية
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                                try {
                                    val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(batteryIntent)
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    val navController = rememberNavController()

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = DarkBgPrimary,
                        bottomBar = {
                            ModernBottomNavBar(navController = navController)
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Dashboard.route
                            ) {
                                composable(Screen.Dashboard.route) {
                                    val viewModel: DashboardViewModel = hiltViewModel()
                                    DashboardScreen(viewModel = viewModel)
                                }

                                composable(Screen.Pairing.route) {
                                    val viewModel: PairingViewModel = hiltViewModel()
                                    PairingScreen(viewModel = viewModel)
                                }

                                composable(Screen.Rules.route) {
                                    val viewModel: RulesViewModel = hiltViewModel()
                                    RulesScreen(viewModel = viewModel)
                                }

                                composable(Screen.Statuses.route) {
                                    val viewModel: StatusesViewModel = hiltViewModel()
                                    StatusesScreen(viewModel = viewModel)
                                }

                                composable(Screen.Logs.route) {
                                    val viewModel: LogsViewModel = hiltViewModel()
                                    LogsScreen(viewModel = viewModel)
                                }

                                composable(Screen.Groups.route) {
                                    val viewModel: GroupsViewModel = hiltViewModel()
                                    GroupsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
