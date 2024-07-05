package com.example.first_test

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

// ROOM - Base de datos LOCAL basada en SQL
// https://developer.android.com/training/data-storage/room#kotlin

@Database(entities = [Usuario::class, Medicion::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun medicionDao(): MedicionDao
}

// @=============== Tabla Usuario ===============@

@Entity(tableName = "usuario")
data class Usuario(
    @PrimaryKey(autoGenerate = true) val usuario_id: Int,
    @ColumnInfo(name = "nombre") val nombre: String?,
    @ColumnInfo(name = "codigo") val codigo: String?,
    @ColumnInfo(name = "email")  val email: String?
)

@Dao // DAO: Data Access Object
interface UsuarioDao {
    @Query("SELECT * FROM usuario")
    fun getAll(): List<Usuario>

    @Query("SELECT * FROM usuario WHERE usuario_id IN (:usuarioIds)")
    fun loadAllByIds(usuarioIds: IntArray): List<Usuario>

    @Query("SELECT * FROM usuario WHERE nombre LIKE :nombre OR " +
            "codigo LIKE :codigo LIMIT 1")
    fun findByName(nombre: String, codigo: String): Usuario

    @Insert
    fun insertAll(vararg usuarios: Usuario)

    @Update
    fun update(usuario: Usuario): Int

    @Delete
    fun delete(usuario: Usuario): Int
}

// @=============== Tabla Mediciones ===============@

@Entity(tableName = "mediciones")
data class Medicion(
    @PrimaryKey(autoGenerate = true) val medicion_id: Int,
    val usuario_id: Int,
    val valor: Int,
)

@Dao
interface MedicionDao{

    @Insert
    fun insert(vararg mediciones: Medicion)

    @Update
    fun update(medicion: Medicion): Int

    @Delete
    fun delete(medicion: Medicion): Int

    @Query("SELECT * FROM mediciones")
    fun getAll(): List<Medicion>

    @Query("SELECT * FROM mediciones WHERE medicion_id IN (:mediciones_ids)")
    fun loadAllByIds(mediciones_ids: IntArray): List<Medicion>

    @Query("SELECT * FROM mediciones WHERE usuario_id LIKE :usuario_id LIMIT 1")
    fun findByUserId(usuario_id: Int): Medicion

}