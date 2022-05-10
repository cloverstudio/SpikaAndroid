package com.clover.studio.exampleapp.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.asLiveData
import com.clover.studio.exampleapp.databinding.ActivityMainBinding
import com.clover.studio.exampleapp.utils.EventObserver
import com.clover.studio.exampleapp.utils.extendables.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

fun startMainActivity(fromActivity: Activity) = fromActivity.apply {
    startActivity(Intent(fromActivity as Context, MainActivity::class.java))
    finish()
}

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var bindingSetup: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingSetup = ActivityMainBinding.inflate(layoutInflater)
        val view = bindingSetup.root
        setContentView(view)

        initializeObservers()

        viewModel.getRoomsRemote()
    }

    private fun initializeObservers() {
        viewModel.getPushNotificationStream().asLiveData(Dispatchers.IO).observe(this) {
            Timber.d("Message $it")
        }

        viewModel.roomsListener.observe(this, EventObserver {
            when (it) {
                is RoomsFetched -> viewModel.getMessagesRemote()
                is RoomFetchFail -> Timber.d("Failed to fetch rooms")
                else -> Timber.d("Other error")
            }
        })

        viewModel.messagesListener.observe(this, EventObserver {
            when (it) {
                is MessagesFetched -> Timber.d("Successfully fetched messages")
                is MessagesFetchFail -> Timber.d("Failed to fetch messages")
                else -> Timber.d("Other error")
            }
        })
    }
}