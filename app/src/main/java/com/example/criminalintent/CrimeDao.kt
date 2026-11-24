package com.example.criminalintent

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID

@Dao
interface CrimeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveCrime(crime: Crime)

    @Delete
    fun deleteCrime(crime: Crime)

    @Query("SELECT * FROM crime")
    fun getCrimes(): LiveData<List<Crime>>

    @Query("SELECT * FROM crime WHERE id = :id")
    fun getCrime(id: UUID): Crime?
}
