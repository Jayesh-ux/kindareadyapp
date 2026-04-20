package com.bluemix.clients_lead.domain.usecases

import com.bluemix.clients_lead.core.common.utils.AppResult
import com.bluemix.clients_lead.domain.model.UserProfile
import com.bluemix.clients_lead.domain.model.LocationTrackingPreference
import com.bluemix.clients_lead.domain.repository.IProfileRepository
import timber.log.Timber

/**
 * Get user profile
 */
class GetUserProfile(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(userId: String): AppResult<UserProfile> {
        Timber.d("🔍 GetUserProfile: Getting profile for userId: $userId")
        val result = repository.getProfile(userId)
        when (result) {
            is AppResult.Success -> Timber.d("✅ GetUserProfile: Success - ${result.data}")
            is AppResult.Error -> Timber.e("❌ GetUserProfile: Error - ${result.error.message}")
        }
        return result
    }
}

/**
 * Update user profile
 */
class UpdateUserProfile(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(
        userId: String,
        fullName: String?,
        department: String?,
        workHoursStart: String?,
        workHoursEnd: String?
    ): AppResult<UserProfile> {
        Timber.d("🔍 UpdateUserProfile: Updating profile for userId: $userId")
        return repository.updateProfile(userId, fullName, department, workHoursStart, workHoursEnd)
    }
}

/**
 * Create user profile
 */
class CreateUserProfile(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(
        userId: String,
        email: String?,
        fullName: String?
    ): AppResult<UserProfile> {
        Timber.d("🔍 CreateUserProfile: Creating profile for userId: $userId")
        return repository.createProfile(userId, email, fullName)
    }
}

/**
 * Get location tracking preference
 */
class GetLocationTrackingPreference(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(): LocationTrackingPreference {
        Timber.d("🔍 GetLocationTrackingPreference: Getting preference")
        val pref = repository.getLocationTrackingPreference()
        Timber.d("✅ GetLocationTrackingPreference: isEnabled = ${pref.isEnabled}")
        return pref
    }
}

/**
 * Save location tracking preference
 */
class SaveLocationTrackingPreference(
    private val repository: IProfileRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        Timber.d("🔍 SaveLocationTrackingPreference: Saving enabled = $enabled")
        repository.saveLocationTrackingPreference(enabled)
        Timber.d("✅ SaveLocationTrackingPreference: Saved successfully")
    }
}
