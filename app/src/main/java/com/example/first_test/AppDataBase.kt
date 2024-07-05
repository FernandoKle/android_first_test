package com.example.first_test

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ROOM - Base de datos LOCAL basada en SQL
// https://developer.android.com/training/data-storage/room#kotlin

@Database(entities = [Usuario::class, Medicion::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun medicionDao(): MedicionDao
}

// @=============== Tabla Usuario ===============@

@Entity(tableName = "usuario")
data class Usuario(
    @PrimaryKey(autoGenerate = true) val uid: Int? = null,
    @ColumnInfo(name = "nombre") val nombre: String,
    @ColumnInfo(name = "codigo") val codigo: String,
    @ColumnInfo(name = "email")  val email: String?
)

@Dao // DAO: Data Access Object
interface UsuarioDao {
    @Query("SELECT * FROM usuario")
    suspend fun getAll(): List<Usuario>

    @Query("SELECT * FROM usuario LIMIT 1")
    suspend fun getOne(): Usuario

    @Query("SELECT * FROM usuario WHERE uid IN (:ids)")
    suspend fun loadAllByIds(ids: IntArray): List<Usuario>

    @Query("SELECT * FROM usuario WHERE nombre LIKE :nombre OR " +
            "codigo LIKE :codigo LIMIT 1")
    suspend fun findByName(nombre: String, codigo: String): Usuario

    @Query("SELECT COUNT(*) FROM usuario")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg usuarios: Usuario)

    @Update
    suspend fun update(usuario: Usuario): Int

    @Delete
    suspend fun delete(usuario: Usuario): Int
}

// @=============== Tabla Mediciones ===============@

@Entity(tableName = "medicion")
data class Medicion(
    @PrimaryKey(autoGenerate = true) val mid: Int? = null,
    val uid: Int,
    val valor: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MedicionDao{

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg mediciones: Medicion)

    @Update
    suspend fun update(medicion: Medicion): Int

    @Delete
    suspend fun delete(medicion: Medicion): Int

    @Query("SELECT * FROM medicion")
    suspend fun getAll(): List<Medicion>

    @Query("SELECT COUNT(*) FROM medicion")
    suspend fun getCount(): Int

    @Query("SELECT * FROM medicion")
    fun getAllFlow(): Flow<List<Medicion>>

    @Query("SELECT * FROM medicion WHERE mid IN (:ids)")
    suspend fun loadAllByIds(ids: IntArray): List<Medicion>

    @Query("SELECT * FROM medicion WHERE uid LIKE :uid")
    suspend fun findByUserId(uid: Int): List<Medicion>

}