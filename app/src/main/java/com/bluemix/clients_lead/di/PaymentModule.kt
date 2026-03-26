package com.bluemix.clients_lead.di

import com.bluemix.clients_lead.data.repository.PaymentRepositoryImpl
import com.bluemix.clients_lead.domain.repository.PaymentRepository
import com.bluemix.clients_lead.features.admin.vm.AdminBankAccountViewModel
import com.bluemix.clients_lead.features.admin.vm.AdminSlotExpansionViewModel
import com.bluemix.clients_lead.features.admin.vm.AdminPlanUsageViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val paymentModule = module {
    single<PaymentRepository> { PaymentRepositoryImpl(get()) }
    
    viewModel { AdminBankAccountViewModel(get(), get()) }
    viewModel { AdminSlotExpansionViewModel(get()) }
    viewModel { AdminPlanUsageViewModel(get()) }
}
