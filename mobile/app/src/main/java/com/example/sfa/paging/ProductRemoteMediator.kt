package com.example.sfa.paging

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.sfa.*
import com.example.sfa.network.ApiService
import java.io.IOException

/**
 * RemoteMediator for the product list.
 *
 * The server returns all matching products in a single response (no server-side
 * pagination yet), so APPEND and PREPEND immediately signal end of pagination.
 * On REFRESH the full result set is fetched and the Room cache is replaced.
 *
 * @param queryParams  Map of query params forwarded to GET /api/products
 *                     (e.g. category, discontinued, search).
 */
@OptIn(ExperimentalPagingApi::class)
class ProductRemoteMediator(
    private val context: Context,
    private val db: AppDatabase,
    private val api: ApiService,
    private val queryParams: Map<String, String>
) : RemoteMediator<Int, ProductEntity>() {

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ProductEntity>
    ): MediatorResult {
        if (loadType == LoadType.APPEND || loadType == LoadType.PREPEND) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        if (!isOnline()) {
            return MediatorResult.Success(endOfPaginationReached = false)
        }

        return try {
            val products = api.getProducts(queryParams)
            db.withTransaction {
                // Keep local cache aligned with the latest active query.
                if (loadType == LoadType.REFRESH) {
                    db.productDao().deleteAll()
                }
                if (products.isNotEmpty()) {
                    db.productDao().insertAll(products.map { it.toEntity() })
                }
            }
            MediatorResult.Success(endOfPaginationReached = true)
        } catch (e: IOException) {
            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
