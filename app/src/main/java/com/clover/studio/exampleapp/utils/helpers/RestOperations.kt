package com.clover.studio.exampleapp.utils.helpers

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import kotlinx.coroutines.Dispatchers

object RestOperations {

    @JvmStatic
    fun <T, A> performGetOperation(
        databaseQuery: () -> LiveData<T>,
        networkCall: suspend () -> Resource<A>,
        saveCallResult: suspend (A) -> Unit
    ): LiveData<Resource<T>> =
        liveData(Dispatchers.IO) {
            emit(Resource.loading())
            val source = databaseQuery.invoke().map { Resource.success(it) }
            emitSource(source)

            val responseStatus = networkCall.invoke()
            if (responseStatus.status == Resource.Status.SUCCESS) {
                saveCallResult(responseStatus.responseData!!)

            } else if (responseStatus.status == Resource.Status.ERROR) {
                emit(Resource.error(responseStatus.message!!))
                emitSource(source)
            }
        }

    @JvmStatic
    suspend fun <R> performRestOperation(
        networkCall: suspend () -> Resource<R>
    ): Resource<R> {
        val responseStatus = networkCall.invoke()
        return when (responseStatus.status) {
            Resource.Status.SUCCESS -> {
                Resource.success(responseStatus.responseData!!)
            }
            Resource.Status.TOKEN_EXPIRED -> {
                Resource.tokenExpired(responseStatus.message!!)
            }
            else -> {
                Resource.error(responseStatus.message!!)
            }
        }
    }
}