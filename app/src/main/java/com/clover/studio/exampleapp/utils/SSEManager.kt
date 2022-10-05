@file:Suppress("BlockingMethodInNonBlockingContext")

package com.clover.studio.exampleapp.utils

import com.clover.studio.exampleapp.BuildConfig
import com.clover.studio.exampleapp.data.models.networking.StreamingResponse
import com.clover.studio.exampleapp.data.repositories.SSERepositoryImpl
import com.clover.studio.exampleapp.data.repositories.SharedPreferencesRepository
import com.google.gson.Gson
import kotlinx.coroutines.*
import okio.IOException
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class SSEManager @Inject constructor(
    private val repo: SSERepositoryImpl,
    private val sharedPrefs: SharedPreferencesRepository
) {
    private var job: Job? = null

    suspend fun startSSEStream() {
        val url =
            BuildConfig.SERVER_URL + Const.Networking.API_SSE_STREAM + "?accesstoken=" + sharedPrefs.readToken()

        openConnectionAndFetchEvents(url)
    }

    private suspend fun openConnectionAndFetchEvents(url: String) {
        if (job != null) {
            job?.cancel()
        }

        job = CoroutineScope(Dispatchers.IO).launch {
            Timber.d("Opening connection for SSE events")
            Timber.d("URL check $url")
            try {
                // Gets HttpURLConnection. Blocking function.  Should run in background
                val conn = (URL(url).openConnection() as HttpURLConnection).also {
                    it.setRequestProperty(
                        "Accept",
                        "text/event-stream"
                    ) // set this Header to stream
                    it.doInput = true // enable inputStream
                }

                if (!sharedPrefs.isFirstSSELaunch()) {
                    Timber.d("Syncing data")
                    repo.syncMessageRecords()
                    repo.syncMessages()
                }

                repo.syncUsers()
                repo.syncRooms()

                // Fetch local timestamps for syncing later. This will handle potential missing data
                // in between calls. After this, open the connection to the SSE
                conn.connect() // Blocking function. Should run in background

                val inputReader = conn.inputStream.bufferedReader()

                sharedPrefs.writeFirstSSELaunch()

                // run while the coroutine is active
                while (isActive) {
                    val line =
                        inputReader.readLine() // Blocking function. Read stream until \n is found
                    Timber.d(line)
                    when {
                        line.startsWith("message:") -> { // get event name
                            Timber.d("Copy message event $line")
                        }
                        line.startsWith("data:") -> { // get data
                            Timber.d("Copy data event $line")

                            var response: StreamingResponse? = null
                            try {
                                val jsonObject = JSONObject("{$line}")
                                val gson = Gson()
                                response =
                                    gson.fromJson(
                                        jsonObject.toString(),
                                        StreamingResponse::class.java
                                    )
                            } catch (ex: Exception) {
                                Tools.checkError(ex)
                            }

                            if (response != null) {
                                Timber.d("Response type: ${response.data?.type}")
                                when (response.data?.type) {
                                    Const.JsonFields.NEW_MESSAGE -> {
                                        response.data?.message?.let { repo.writeMessages(it) }
                                        response.data?.message?.id?.let {
                                            repo.sendMessageDelivered(
                                                it
                                            )
                                        }
                                    }
                                    Const.JsonFields.UPDATE_MESSAGE -> {
                                        response.data?.message?.let { repo.writeMessages(it) }
                                    }
                                    Const.JsonFields.DELETE_MESSAGE -> {
                                        response.data?.message?.let { repo.deleteMessage(it) }
                                    }
                                    Const.JsonFields.NEW_MESSAGE_RECORD -> {
                                        response.data?.messageRecord?.let {
                                            repo.writeMessageRecord(
                                                it
                                            )
                                        }
                                    }
                                    Const.JsonFields.DELETE_MESSAGE_RECORD -> {
                                        response.data?.messageRecord?.let {
                                            repo.deleteMessageRecord(
                                                it
                                            )
                                        }
                                    }
                                    Const.JsonFields.USER_UPDATE -> {
                                        response.data?.user?.let { repo.writeUser(it) }
                                    }
                                    Const.JsonFields.NEW_ROOM -> {
                                        response.data?.room?.let { repo.writeRoom(it) }
                                    }
                                    Const.JsonFields.UPDATE_ROOM -> {
                                        response.data?.room?.let { repo.writeRoom(it) }
                                    }
                                    Const.JsonFields.DELETE_ROOM -> {
                                        response.data?.room?.let { repo.deleteRoom(it) }
                                    }
                                }
                            }
                        }
                        line.isEmpty() -> { // empty line, finished block. Emit the event
                            Timber.d("Emitting event")
                        }
                    }
                }
            } catch (ex: Exception) {
                if (ex is IOException) {
                    Timber.d("IOException ${ex.message} ${ex.localizedMessage}")
                    openConnectionAndFetchEvents(url)
                }
                Tools.checkError(ex)
            }
        }
    }
}