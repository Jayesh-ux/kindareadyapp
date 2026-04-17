package com.bluemix.clients_lead.core.navigation

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.bluemix.clients_lead.core.network.SessionManager
import com.bluemix.clients_lead.features.Clients.presentation.ClientDetailScreen
import com.bluemix.clients_lead.features.Clients.presentation.ClientsScreen
import com.bluemix.clients_lead.features.Clients.presentation.CreateClientScreen
import com.bluemix.clients_lead.features.auth.presentation.screens.AuthScreen
import com.bluemix.clients_lead.features.auth.vm.SessionViewModel
import com.bluemix.clients_lead.features.map.presentation.MapScreen
import com.bluemix.clients_lead.features.settings.presentation.ProfileScreen
import com.bluemix.clients_lead.features.timesheet.presentation.ActivityScreen
import com.bluemix.clients_lead.features.admin.presentation.AdminDashboardScreen
import com.bluemix.clients_lead.features.admin.presentation.AdminJourneyScreen
import com.bluemix.clients_lead.features.admin.presentation.AdminUserManagementScreen
import com.bluemix.clients_lead.features.admin.presentation.AdminPinClientScreen
import com.bluemix.clients_lead.features.location.BlockingTrackingScreen
import com.bluemix.clients_lead.features.location.LocationTrackingStateManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.bluemix.clients_lead.features.*

/**
 * Main app navigation graph with gate pattern for auth routing.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navigationManager = remember(navController) {
        NavigationManager(navController)
    }

    val sessionVM: SessionViewModel = koinViewModel()
    val session by sessionVM.state.collectAsState()
    val isAdmin = session.user?.isAdmin ?: false
    val isSuperAdmin = session.user?.isSuperAdmin ?: false
    val isAdminOrSuperAdmin = isAdmin || isSuperAdmin  // ✅ Combined for both Admin and SuperAdmin

    // ✅ Reactive Tracking Launch
    val trackingStateManager: LocationTrackingStateManager = koinInject()
    LaunchedEffect(session.isAuthenticated) {
        if (session.isAuthenticated) {
            trackingStateManager.startTracking()
        }
    }

    // ✅ Inject SessionManager to observe invalidation
    val sessionManager: SessionManager = koinInject()
    val sessionInvalidated by sessionManager.sessionInvalidated.collectAsState()
    val context = LocalContext.current

    // ✅ Remember if we've already shown the logout message
    var hasShownLogoutMessage by remember { mutableStateOf(false) }

    // ✅ NEW: Observe session invalidation and force logout (with debounce)
    LaunchedEffect(sessionInvalidated) {
        if (sessionInvalidated && !hasShownLogoutMessage) {
            hasShownLogoutMessage = true

            Toast.makeText(
                context,
                "You've been logged out from another device",
                Toast.LENGTH_LONG
            ).show()

            // Navigate to auth and clear back stack
            navigationManager.navigateToAuth()

            // Reset the flag
            sessionManager.resetInvalidationFlag()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Gate,
        enterTransition = { fadeIn() + slideInHorizontally { it } },
        exitTransition = { fadeOut() + slideOutHorizontally { -it } },
        popEnterTransition = { fadeIn() + slideInHorizontally { -it } },
        popExitTransition = { fadeOut() + slideOutHorizontally { it } }
    ) {
        // -------- Gate/Splash Screen --------
        composable<Route.Gate> {
            SplashScreen(isReady = session.isReady)

            LaunchedEffect(session) {
                if (!session.isReady) return@LaunchedEffect

                if (session.isAuthenticated) {
                    navigationManager.navigateToMain()
                } else {
                    navigationManager.navigateToAuth()
                }
            }
        }

        // -------- Authentication --------
        composable<Route.Auth> {
            AuthScreen(
                onSignedIn = {
                    navigationManager.navigateToMain()
                }
            )
        }

        // -------- Protected Tab Screens --------
        composable<Route.Map> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                MainScaffold(
                    currentRoute = Route.Map,
                    navigationManager = navigationManager,
                    isAdmin = isAdmin,
                    isSuperAdmin = isSuperAdmin
                ) {
                    val bypassBlocking = isAdmin || isSuperAdmin  // ✅ Neither Admin nor SuperAdmin is blocked
                    if (!bypassBlocking) {
                        BlockingTrackingScreen {
                            MapScreen(
                                onNavigateToClientDetail = { clientId ->
                                    navigationManager.navigateToClientDetail(clientId)
                                },
                                onNavigateToAgentDetail = { agentId ->
                                    navigationManager.navigateToAgentDetail(agentId)
                                }
                            )
                        }
                    } else {
                        MapScreen(
                            onNavigateToClientDetail = { clientId ->
                                navigationManager.navigateToClientDetail(clientId)
                            },
                            onNavigateToAgentDetail = { agentId ->
                                navigationManager.navigateToAgentDetail(agentId)
                            }
                        )
                    }
                }
            }
        }

        composable<Route.Clients> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                MainScaffold(
                    currentRoute = Route.Clients,
                    navigationManager = navigationManager,
                    isAdmin = isAdmin,
                    isSuperAdmin = isSuperAdmin
                ) {
                    val bypassBlocking = isAdmin || isSuperAdmin  // ✅ Neither Admin nor SuperAdmin is blocked
                    if (!bypassBlocking) {
                        BlockingTrackingScreen {
                            ClientsScreen(
                                onNavigateToDetail = { clientId ->
                                    navigationManager.navigateToClientDetail(clientId)
                                },
                                onNavigateToCreateClient = {
                                    navigationManager.navigateToCreateClient()
                                }
                            )
                        }
                    } else {
                        ClientsScreen(
                            onNavigateToDetail = { clientId ->
                                navigationManager.navigateToClientDetail(clientId)
                            },
                            onNavigateToCreateClient = {
                                navigationManager.navigateToCreateClient()
                            }
                        )
                    }
                }
            }
        }

        composable<Route.Activity> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                MainScaffold(
                    currentRoute = Route.Activity,
                    navigationManager = navigationManager,
                    isAdmin = isAdmin,
                    isSuperAdmin = isSuperAdmin
                ) {
                    val bypassBlocking = isAdmin || isSuperAdmin  // ✅ Neither Admin nor SuperAdmin is blocked
                    if (!bypassBlocking) {
                        BlockingTrackingScreen {
                            ActivityScreen()
                        }
                    } else {
                        ActivityScreen()
                    }
                }
            }
        }

        composable<Route.Profile> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                MainScaffold(
                    currentRoute = Route.Profile,
                    navigationManager = navigationManager,
                    isAdmin = isAdmin,
                    isSuperAdmin = isSuperAdmin
                ) {
                    val bypassBlocking = isAdmin || isSuperAdmin  // ✅ Neither Admin nor SuperAdmin is blocked
                    if (!bypassBlocking) {
                        BlockingTrackingScreen {
                            ProfileScreen(
                                onNavigateToAuth = navigationManager::navigateToAuth
                            )
                        }
                    } else {
                        ProfileScreen(
                            onNavigateToAuth = navigationManager::navigateToAuth
                        )
                    }
                }
            }
        }

        composable<Route.AdminDashboard> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                MainScaffold(
                    currentRoute = Route.AdminDashboard,
                    navigationManager = navigationManager,
                    isAdmin = isAdmin,
                    isSuperAdmin = isSuperAdmin
                ) {
                    AdminDashboardScreen(
                        onNavigateToMap = { navigationManager.navigateToTab(Route.Map) },
                        onNavigateToReports = { agentId -> navigationManager.navigateToAdminJourney(agentId) },
                        onNavigateToUsers = { navigationManager.navigateToAdminUsers() },
                        onNavigateToClientServices = { navigationManager.navigateToAdminClientServices() },
                        onNavigateToBankAccount = { navigationManager.navigateToAdminBankAccount() },
                        onNavigateToSlotExpansion = { navigationManager.navigateToAdminSlotExpansion() },
                        onNavigateToPlanUsage = { navigationManager.navigateToAdminPlanUsage() },
                        onNavigateToAgentDetail = { agentId -> navigationManager.navigateToAgentDetail(agentId) },
                        onNavigateToMeetingLogs = { navigationManager.navigateToAdminMeetingLogs() },
                        onNavigateToPinClients = { navigationManager.navigateToAdminPinClients() }
                    )
                }
            }
        }

        composable<Route.AdminJourneyReports> { backStack ->
            val args = backStack.toRoute<Route.AdminJourneyReports>()
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                AdminJourneyScreen(
                    agentId = args.agentId,
                    onNavigateBack = navigationManager::navigateBack
                )
            }
        }

        composable<Route.AdminUserManagement> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                AdminUserManagementScreen(
                    onNavigateBack = navigationManager::navigateBack,
                    onNavigateToAgentDetail = { agentId -> navigationManager.navigateToAgentDetail(agentId) }
                )
            }
        }

        composable<Route.AdminClientServices> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                com.bluemix.clients_lead.features.admin.presentation.AdminClientServicesScreen(
                    onNavigateBack = navigationManager::navigateBack,
                    onNavigateToAddService = { navigationManager.navigateToAdminAddService() }
                )
            }
        }

        composable<Route.CreateClient> {
            CreateClientScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // -------- Detail Screen (No Bottom Bar) --------
        composable<Route.ClientDetail> { backStack ->
            val args = backStack.toRoute<Route.ClientDetail>()

            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                ClientDetailScreen(
                    clientId = args.clientId,
                    onNavigateBack = navigationManager::navigateBack
                )
            }
        }
        composable<Route.AdminAddService> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                com.bluemix.clients_lead.features.admin.presentation.AdminAddServiceScreen(
                    onNavigateBack = navigationManager::navigateBack
                )
            }
        }

        composable<Route.AdminAgentDetail> { backStack ->
            val args = backStack.toRoute<Route.AdminAgentDetail>()
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                com.bluemix.clients_lead.features.admin.presentation.AdminAgentDetailScreen(
                    agentId = args.agentId,
                    onNavigateBack = navigationManager::navigateBack,
                    onNavigateToReports = { navigationManager.navigateToAdminJourney(it) }
                )
            }
        }

        composable<Route.AdminBankAccount> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                com.bluemix.clients_lead.features.admin.presentation.AdminBankAccountScreen(
                    onNavigateBack = navigationManager::navigateBack
                )
            }
        }

        composable<Route.AdminSlotExpansion> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                com.bluemix.clients_lead.features.admin.presentation.AdminSlotExpansionScreen(
                    onNavigateBack = navigationManager::navigateBack
                )
            }
        }

        composable<Route.AdminPlanUsage> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                com.bluemix.clients_lead.features.admin.presentation.AdminPlanUsageScreen(
                    onNavigateBack = navigationManager::navigateBack,
                    onNavigateToUpgrade = { navigationManager.navigateToAdminSlotExpansion() }
                )
            }
        }

        composable<Route.AdminPinClients> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                AdminPinClientScreen(
                    onNavigateBack = navigationManager::navigateBack
                )
            }
        }

        composable<Route.AdminMeetingLogs> {
            ProtectedRoute(
                isAuthenticated = session.isAuthenticated,
                onNavigateToAuth = navigationManager::navigateToAuth
            ) {
                com.bluemix.clients_lead.features.admin.presentation.MeetingLogsScreen(
                    onNavigateBack = navigationManager::navigateBack
                )
            }
        }
    }
}

/**
 * Wrapper to protect routes from unauthenticated access
 */
@Composable
private fun ProtectedRoute(
    isAuthenticated: Boolean,
    onNavigateToAuth: () -> Unit,
    content: @Composable () -> Unit
) {
    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) {
            onNavigateToAuth()
        }
    }

    if (isAuthenticated) {
        content()
    }
}