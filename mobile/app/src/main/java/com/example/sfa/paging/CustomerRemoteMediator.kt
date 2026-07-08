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
 * RemoteMediator for the customer list.
 *
 * Strategy:
 *  - REFRESH  → if online, fetch all matching customers from the server and
 *               replace the Room cache; if offline, skip network and let
 *               Room serve stale data (endOfPaginationReached = false).
 *  - APPEND / PREPEND → always endOfPaginationReached because the server
 *               returns the full result set in one call.
 */
@OptIn(ExperimentalPagingApi::class)
class CustomerRemoteMediator(
    private val context: Context,
    private val db: AppDatabase,
    private val api: ApiService,
    private val assignedUserId: Int?,
    private val managerId: Int?
) : RemoteMediator<Int, CustomerEntity>() {

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, CustomerEntity>
    ): MediatorResult {
        if (loadType == LoadType.APPEND || loadType == LoadType.PREPEND) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        // REFRESH
        if (!isOnline()) {
            // Serve cached data; don't signal end so next online REFRESH will re-fetch
            return MediatorResult.Success(endOfPaginationReached = false)
        }

        return try {
            var customers = api.getCustomers(
                assignedUserId = assignedUserId,
                managerId = managerId
            )

            // Fallback: if scoped query returns empty, pull full list so UI is not blank.
            if (customers.isEmpty() && (assignedUserId != null || managerId != null)) {
                customers = api.getCustomers(
                    assignedUserId = null,
                    managerId = null
                )
            }

            db.customerDao().deleteAll()
            db.customerDao().insertAll(customers.map { it.toEntity() })
            MediatorResult.Success(endOfPaginationReached = true)
        } catch (e: IOException) {
            // Network error — serve cache
            MediatorResult.Success(endOfPaginationReached = false)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
