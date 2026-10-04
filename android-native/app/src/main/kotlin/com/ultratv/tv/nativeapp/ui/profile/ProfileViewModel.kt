package com.ultratv.tv.nativeapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.profile.StartupMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    private val parental: ParentalStore,
) : ViewModel() {
    val profiles: StateFlow<List<ProfileEntity>> = repo.profiles
    val current: StateFlow<ProfileEntity?> = repo.current
    val startupMode: StateFlow<StartupMode> = repo.startupMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupMode.ALWAYS_ASK)
    val parentalPinSet: StateFlow<Boolean> = parental.pinHash.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun select(id: Long) = viewModelScope.launch { repo.select(id) }
    fun requestSwitch() = repo.requestSwitch()
    fun create(name: String, color: Int, kids: Boolean) = viewModelScope.launch { repo.create(name, color, kids) }
    fun rename(id: Long, name: String) = viewModelScope.launch { repo.rename(id, name) }
    fun setColor(id: Long, color: Int) = viewModelScope.launch { repo.setColor(id, color) }
    fun setKids(id: Long, kids: Boolean) = viewModelScope.launch { repo.setKids(id, kids) }
    fun setPin(id: Long, pin: String?) = viewModelScope.launch { repo.setPin(id, pin) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun setStartupMode(m: StartupMode) = viewModelScope.launch { repo.setStartupMode(m) }
    suspend fun verifyPin(id: Long, pin: String) = repo.verifyPin(id, pin)
    suspend fun checkParentalPin(pin: String) = parental.check(pin)
}

/** Porte d'entrée du démarrage : faut-il afficher « Qui regarde ? » (null = chargement). */
@HiltViewModel
class ProfileGateViewModel @Inject constructor(repo: ProfileRepository) : ViewModel() {
    val needsSelection: StateFlow<Boolean?> = repo.needsSelection.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
