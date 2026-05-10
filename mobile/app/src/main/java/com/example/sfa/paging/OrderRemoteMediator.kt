package com.example.sfa.paging

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.sfa.*
import com.example.sfa.network.ApiService
import java.io.IOException

/**
 * RemoteMediator for the order list.
 *
 * On REFRESH, fetches all relevant orders from the server (filtered by
 * userId / managerId) and replaces the Room cache. Paging 3 then drives
 * display from Room's PagingSource, optionally filtered by status in SQL.
 */
@OptIn(ExperimentalPagingApi::class)
class OrderRemoteMediator(
    private val context: Context,
    private val db: AppDatabase,
    private val api: ApiService,
    private val userId: Int,
    private val managerId: Int?
) : RemoteMediator<Int, OrderEntity>() {

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, OrderEntity>
    ): MediatorResult {
        if (loadType == LoadType.APPEND || loadType == LoadType.PREPEND) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        if (!isOnline()) {
            return MediatorResult.Success(endOfPaginationReached = false)
        }

        return try {
            var orders = api.getOrders(
                createdByUserId = if (managerId == null) userId else null,
                managerId = managerId
            )

            // Fallback: if scoped query returns empty, pull full list so UI is not blank.
            if (orders.isEmpty() && (userId != 0 || managerId != null)) {
                orders = api.getOrders(
                    createdByUserId = null,
                    managerId = null
                )
            }

            db.orderDao().deleteAll()
            db.orderDao().insertAll(orders.map { it.toEntity() })
            MediatorResult.Success(endOfPaginationReached = true)
        } catch (e: IOException) {
            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
