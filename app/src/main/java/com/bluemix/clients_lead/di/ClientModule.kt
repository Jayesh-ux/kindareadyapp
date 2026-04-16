package com.bluemix.clients_lead.di

import com.bluemix.clients_lead.data.repository.ClientRepositoryImpl
import com.bluemix.clients_lead.data.repository.OCRRepository
import com.bluemix.clients_lead.data.repository.QuickVisitRepositoryImpl
import com.bluemix.clients_lead.domain.repository.IClientRepository
import com.bluemix.clients_lead.domain.repository.IQuickVisitRepository
import com.bluemix.clients_lead.domain.usecases.*
import com.bluemix.clients_lead.features.Clients.vm.ClientDetailViewModel
import com.bluemix.clients_lead.features.Clients.vm.ClientsViewModel
import com.bluemix.clients_lead.features.Clients.vm.MissingClientsViewModel
import com.bluemix.clients_lead.features.admin.vm.AdminPinClientViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val clientModule = module {

    // Repository
    single<IClientRepository> {
        ClientRepositoryImpl(
            httpClient = get()
        )
    }
    single<IQuickVisitRepository> {
        QuickVisitRepositoryImpl(httpClient = get())
    }

    // Use Cases
    factory {
        GetAllClients(repository = get())
    }

    factory {
        GetClientById(repository = get())
    }

    factory {
        GetClientsWithLocation(repository = get())
    }

    factory {
        SearchClients(repository = get())
    }

    factory { CreateQuickVisit(repository = get()) }

    // ✅ NEW: Remote search use case
    factory {
        SearchRemoteClients(repository = get())
    }
    factory { UpdateClientAddress(repository = get()) }
    factory { UpdateClientLocation(clientRepository = get()) }
    factory { CreateClient(repository = get()) }
    factory { GetTeamLocations(repository = get()) }
    factory { UpdateUserStatus(repository = get()) }
    factory { GetClientServices(repository = get()) }
    factory { AddClientService(repository = get()) }
    factory { AcceptClientService(repository = get()) }
    factory { RetryGeocoding(repository = get()) }
    factory { SelfHealDatabase(repository = get()) }
    factory { AdminPinClientLocation(repository = get()) }
    factory { GetLiveAgents(repository = get()) }
    factory { GetDailySummary(repository = get()) }
    factory { OCRRepository() }

    // ViewModels
    viewModel {
        ClientsViewModel(
            getAllClients = get(),
            searchRemoteClients = get(),
            tokenStorage = get(),
            getCurrentUserId = get(),
            locationTrackingStateManager = get(),
            context = androidContext(),
            createClient = get(),
            sessionManager = get(),
            observeAuthState = get(),
            httpClient = get()
        )
    }

    // ✅ ClientDetailViewModel - updated with tag location
    viewModel {
        ClientDetailViewModel(
            getClientById = get(),
            clientRepository = get()
        )
    }

    // ✅ Missing Clients ViewModel
    viewModel {
        MissingClientsViewModel(
            clientRepository = get()
        )
    }

    // Phase 2: Admin Pin Client ViewModel
    viewModel {
        AdminPinClientViewModel(
            getAllClients = get(),
            adminPinClientLocation = get()
        )
    }
}