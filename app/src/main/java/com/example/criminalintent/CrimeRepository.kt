package com.example.criminalintent

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.room.Room
import java.util.UUID
import java.util.concurrent.Executors

private const val DATABASE_NAME = "crime-room-database"

class CrimeRepository private constructor(context: Context) {

    private val database: CrimeDatabase = Room.databaseBuilder(
        context.applicationContext,
        CrimeDatabase::class.java,
        DATABASE_NAME
    ).build()

    private val crimeDao = database.crimeDao()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCrimes(): LiveData<List<Crime>> = crimeDao.getCrimes()

    fun loadCrime(id: UUID, onLoaded: (Crime?) -> Unit) {
        executor.execute {
            val crime = crimeDao.getCrime(id)
            mainHandler.post { onLoaded(crime) }
        }
    }

    fun saveCrime(crime: Crime) {
        executor.execute { crimeDao.saveCrime(crime) }
    }

    fun deleteCrime(crime: Crime) {
        executor.execute { crimeDao.deleteCrime(crime) }
    }

    companion object {
        private var INSTANCE: CrimeRepository? = null

        fun initialize(context: Context) {
            if (INSTANCE == null) INSTANCE = CrimeRepository(context)
        }

        fun get(): CrimeRepository =
            INSTANCE ?: throw IllegalStateException("CrimeRepository must be initialized")
    }
}
