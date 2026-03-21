package com.bluemix.clients_lead.di

import com.bluemix.clients_lead.features.admin.vm.AdminDashboardViewModel
import com.bluemix.clients_lead.features.admin.vm.AdminJourneyViewModel
import com.bluemix.clients_lead.features.admin.vm.AdminUserManagementViewModel
import com.bluemix.clients_lead.features.admin.vm.AdminClientServicesViewModel
import com.bluemix.clients_lead.features.admin.vm.AdminAddServiceViewModel
import com.bluemix.clients_lead.domain.usecases.GetDashboardStats
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext

val adminModule = module {
    factory { GetDashboardStats(get()) }
    viewModel { AdminDashboardViewModel(get(), get(), get()) }
    viewModel { AdminJourneyViewModel(androidContext(), get(), get(), get()) }
    viewModel { AdminUserManagementViewModel(get(), get()) }
    viewModel { AdminClientServicesViewModel(get()) }
    viewModel { AdminAddServiceViewModel(get(), get(), get()) }
    viewModel { (agentId: String) -> com.bluemix.clients_lead.features.admin.vm.AdminAgentDetailViewModel(agentId, get(), get(), get()) }
}
