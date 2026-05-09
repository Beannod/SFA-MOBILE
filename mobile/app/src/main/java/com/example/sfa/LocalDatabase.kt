package com.example.sfa

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════════════════════════
// Room Entities  (mirrors server models — store only what the mobile app uses)
// ═══════════════════════════════════════════════════════════════════════════════

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val customerType: String,
    val code: String,
    val contactPerson: String,
    val phone: String,
    val email: String,
    val address: String,
    val city: String,
    val state: String,
    val pincode: String,
    val latitude: Double?,
    val longitude: Double?,
    val creditLimit: Double,
    val outstandingBalance: Double,
    val assignedUserId: Int?,
    val assignedUserName: String,
    val createdByUserId: Int?,
    val createdByUserName: String,
    val territory: String,
    val isActive: Boolean,
    val approvalStatus: String,
    val isArchived: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

fun Customer.toEntity() = CustomerEntity(
    id, name, customerType, code, contactPerson, phone, email,
    address, city, state, pincode, latitude, longitude,
    creditLimit, outstandingBalance, assignedUserId, assignedUserName,
    createdByUserId, createdByUserName, territory, isActive, approvalStatus, isArchived
)

fun CustomerEntity.toModel() = Customer(
    id, name, customerType, code, contactPerson, phone, email,
    address, city, state, pincode, latitude, longitude,
    creditLimit, outstandingBalance, assignedUserId, assignedUserName,
    createdByUserId, createdByUserName, territory, isActive, approvalStatus, isArchived
)

// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val description: String,
    val itemNo: String,
    val quality: String,
    val code: String,
    val remarks: String,
    val imageUrl: String,
    val category: String,
    val size: String,
    val weight: Double?,
    val thickness: String,
    val finish: String,
    val shade: String,
    val type: String,
    val boxCoverage: Double?,
    val kgPerBox: Double?,
    val ratePerSqm: Double?,
    val piecesPerBox: Int?,
    val price: Double,
    val dealerPrice: Double?,
    val unit: String,
    val isNewArrival: Boolean,
    val isDiscontinued: Boolean,
    val isActive: Boolean,
    val isArchived: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)

fun Product.toEntity() = ProductEntity(
    id, name, description, itemNo, quality, code, remarks, imageUrl,
    category, size, weight, thickness, finish, shade, type,
    boxCoverage, kgPerBox, ratePerSqm, piecesPerBox,
    price, dealerPrice, unit, isNewArrival, isDiscontinued, isActive, isArchived
)

fun ProductEntity.toModel() = Product(
    id, name, description, itemNo, quality, code, remarks, imageUrl,
    category, size, weight, thickness, finish, shade, type,
    boxCoverage, kgPerBox, ratePerSqm, piecesPerBox,
    price, dealerPrice, unit, isNewArrival, isDiscontinued, isActive, isArchived
)

// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Int,
    val orderNumber: String,
    val customerId: Int,
    val customerName: String,
    val createdByUserId: Int,
    val status: String,
    val subTotal: Double,
    val discountPercent: Double,
    val discountAmount: Double,
    val totalAmount: Double,
    val remarks: String,
    val orderDate: String,
    val itemCount: Int,
    val cachedAt: Long = System.currentTimeMillis()
)

fun Order.toEntity() = OrderEntity(
    id, orderNumber, customerId, customerName, createdByUserId,
    status, subTotal, discountPercent, discountAmount, totalAmount,
    remarks, orderDate, itemCount
)

fun OrderEntity.toModel() = Order(
    id, orderNumber, customerId, customerName, createdByUserId,
    status, subTotal, discountPercent, discountAmount, totalAmount,
    remarks, orderDate, itemCount
)

// ─────────────────────────────────────────────────────────────────────────────
// Pending sync queue — offline writes waiting to be sent to the server
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val endpoint: String,         // e.g. "/api/customers"
    val method: String,           // POST, PUT, PATCH
    val body: String,             // JSON string
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

// ═══════════════════════════════════════════════════════════════════════════════
// DAOs
// ═══════════════════════════════════════════════════════════════════════════════

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAll(): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY name ASC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE assignedUserId = :userId ORDER BY name ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedByUser(userId: Int, limit: Int, offset: Int): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE assignedUserId = :userId ORDER BY name ASC")
    suspend fun getByAssignedUser(userId: Int): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Int): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CustomerEntity)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isActive = 1 AND (name LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') ORDER BY name ASC LIMIT :limit OFFSET :offset")
    suspend fun searchPaged(q: String, limit: Int, offset: Int): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Int): ProductEntity?

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE createdByUserId = :userId ORDER BY id DESC")
    suspend fun getByUser(userId: Int): List<OrderEntity>

    @Query("SELECT * FROM orders ORDER BY id DESC")
    suspend fun getAll(): List<OrderEntity>

    @Query("SELECT * FROM orders ORDER BY id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE createdByUserId = :userId ORDER BY id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedByUser(userId: Int, limit: Int, offset: Int): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: Int): OrderEntity?

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OrderEntity)

    @Query("DELETE FROM orders")
    suspend fun deleteAll()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueItem>

    @Insert
    suspend fun insert(item: SyncQueueItem)

    @Delete
    suspend fun delete(item: SyncQueueItem)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Int)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int

    // Live count — observed by ViewModel to drive the offline badge
    @Query("SELECT COUNT(*) FROM sync_queue")
    fun countFlow(): Flow<Int>
}

// ═══════════════════════════════════════════════════════════════════════════════
// Database
// ═══════════════════════════════════════════════════════════════════════════════

@Database(
    entities = [CustomerEntity::class, ProductEntity::class, OrderEntity::class, SyncQueueItem::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sfa_offline.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
