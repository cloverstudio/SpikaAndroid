package com.clover.studio.spikamessenger

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.clover.studio.spikamessenger.utils.Event
import com.clover.studio.spikamessenger.utils.helpers.Resource
import timber.log.Timber

open class BaseViewModel : ViewModel() {
    val tokenExpiredListener = MutableLiveData<Event<Boolean>>()

    fun setTokenExpiredTrue() {
        tokenExpiredListener.postValue(Event(true))
    }

    fun setTokenExpiredFalse() {
        tokenExpiredListener.postValue(Event(false))
    }

    fun <R> resolveResponseStatus(
        mutableLiveData: MutableLiveData<Event<Resource<R?>>>?,
        resource: Resource<R?>
    ) {
        when (resource.status) {
            Resource.Status.SUCCESS, Resource.Status.ERROR -> mutableLiveData?.postValue(
                Event(
                    resource
                )
            )

            Resource.Status.TOKEN_EXPIRED -> tokenExpiredListener.postValue(Event(true))
            Resource.Status.LOADING -> {
                mutableLiveData?.postValue(Event(resource))
            }

            Resource.Status.NEW_USER -> {
                mutableLiveData?.postValue(Event(resource))
            }
            else -> Timber.d("TODO")
        }
    }
}