package com.example.criminalintent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.UUID

class CrimeDetailViewModel : ViewModel() {

    private val crimeMutable = MutableLiveData<Crime>()
    val crimeLiveData: LiveData<Crime> get() = crimeMutable

    var crime: Crime? = null
        private set

    var isExisting = false
        private set

    var pendingContactId: String? = null

    private var loaded = false

    fun load(crimeId: UUID?) {
        if (loaded) return
        loaded = true
        if (crimeId == null) {
            publish(Crime())
        } else {
            CrimeRepository.get().loadCrime(crimeId) { existing ->
                isExisting = existing != null
                publish(existing ?: Crime())
            }
        }
    }

    private fun publish(newCrime: Crime) {
        crime = newCrime
        crimeMutable.value = newCrime
    }
}
