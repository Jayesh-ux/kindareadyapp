package com.bluemix.clients_lead.di

import com.bluemix.clients_lead.core.common.utils.DefaultDispatchers
import com.bluemix.clients_lead.core.common.utils.DispatcherProvider
import com.bluemix.clients_lead.core.network.ApiClientProvider
import com.bluemix.clients_lead.core.network.ApiEndpoints
import com.bluemix.clients_lead.core.network.SessionManager
import com.bluemix.clients_lead.core.network.TokenStorage
import com.bluemix.clients_lead.domain.repository.AuthRepository
import com.bluemix.clients_lead.data.repository.AuthRepositoryImpl

import com.bluemix.clients_lead.features.auth.vm.SessionViewModel
import com.bluemix.clients_lead.features.map.vm.MapViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * AppModule: ONLY app-level singletons that cross all features.
 * Repository bindings live in their own feature modules (ClientModule, LocationModule, etc.)
 * to avoid duplicate Koin bindings which cause undefined runtime behavior.
 */
val appModule = module {

    single<DispatcherProvider> { DefaultDispatchers }

    // Token + Session (shared across all features)
    single { TokenStorage(androidContext()) }
    single { SessionManager(get()) }

    // Shared HttpClient (single instance used by all repositories)
    single {
        ApiClientProvider.create(
            baseUrl = ApiEndpoints.BASE_URL,
            tokenStorage = get(),
            sessionManager = get()
        )
    }

    // Auth repository (app-level, needed for session restore)
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), androidContext()) }

    // Use cases: InsertLocationLog is provided by locationModule (canonical owner)

    // Session ViewModel
    viewModel {
        SessionViewModel(
            get(), get()
        )
    }

    // Map ViewModel — dependencies provided by clientModule + locationModule + authModule
    viewModel {
        MapViewModel(
            get(), // GetClientsWithLocation (from clientModule)
            get(), // GetCurrentUserId (from authModule)
            get(), // LocationTrackingStateManager (from locationModule)
            createQuickVisit = get(),     // from clientModule
            updateClientAddress = get(),   // from clientModule
            updateClientLocation = get(),  // from clientModule (NEW)
            getTeamLocations = get(),      // from clientModule
            observeAuthState = get(),      // from authModule
            searchRemoteClients = get(),   // from clientModule (NEW)
            insertLocationLogUseCase = get(),     // ✅ NEW
            createClient = get(),          // ✅ NEW: For seeding test data
            signOut = get(),                // ✅ NEW: For logout functionality
            context = androidContext(),
            getLocationLogsByDateRange = get(), // ✅ NEW: For "Whole Day" overlay
            getLiveAgents = get(),           // ✅ NEW Phase 3
            getDailySummary = get()          // ✅ NEW Phase 3
        )
    }
}
