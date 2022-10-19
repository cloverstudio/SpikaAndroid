package com.clover.studio.exampleapp.ui.main.chat

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.asLiveData
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.Message
import com.clover.studio.exampleapp.data.models.junction.RoomWithUsers
import com.clover.studio.exampleapp.databinding.ActivityChatScreenBinding
import com.clover.studio.exampleapp.ui.main.SingleRoomData
import com.clover.studio.exampleapp.ui.main.SingleRoomFetchFailed
import com.clover.studio.exampleapp.ui.onboarding.startOnboardingActivity
import com.clover.studio.exampleapp.utils.*
import com.clover.studio.exampleapp.utils.dialog.DialogError
import com.clover.studio.exampleapp.utils.extendables.BaseActivity
import com.clover.studio.exampleapp.utils.extendables.DialogInteraction
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject


fun startChatScreenActivity(fromActivity: Activity, roomData: String) =
    fromActivity.apply {
        val intent = Intent(fromActivity as Context, ChatScreenActivity::class.java)
        intent.putExtra(Const.Navigation.ROOM_DATA, roomData)
        startActivity(intent)
    }

fun replaceChatScreenActivity(fromActivity: Activity, roomData: String) =
    fromActivity.apply {
        val intent = Intent(fromActivity as Context, ChatScreenActivity::class.java)
        intent.putExtra(Const.Navigation.ROOM_DATA, roomData)
        startActivity(intent)
        finish()
    }

@AndroidEntryPoint
class ChatScreenActivity : BaseActivity() {
    var roomWithUsers: RoomWithUsers? = null
    private var mutedRooms: MutableList<String> = ArrayList()

    private lateinit var bindingSetup: ActivityChatScreenBinding
    private val viewModel: ChatViewModel by viewModels()
    private var handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable {
        Timber.d("Ending handler")
        bindingSetup.cvNotification.cvRoot.visibility = View.GONE
    }

    @Inject
    lateinit var uploadDownloadManager: UploadDownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingSetup = ActivityChatScreenBinding.inflate(layoutInflater)

        val view = bindingSetup.root
        setContentView(view)

        // Fetch room data sent from previous activity
        val gson = Gson()
        roomWithUsers = gson.fromJson(
            intent.getStringExtra(Const.Navigation.ROOM_DATA),
            RoomWithUsers::class.java
        )

        Timber.d("chatScreen ${roomWithUsers.toString()}")
        initializeObservers()
    }

    private fun initializeObservers() {
        viewModel.tokenExpiredListener.observe(this, EventObserver { tokenExpired ->
            if (tokenExpired) {
                DialogError.getInstance(this,
                    getString(R.string.warning),
                    getString(R.string.session_expired),
                    null,
                    getString(R.string.ok),
                    object : DialogInteraction {
                        override fun onFirstOptionClicked() {
                            // ignore
                        }

                        override fun onSecondOptionClicked() {
                            viewModel.setTokenExpiredFalse()
                            startOnboardingActivity(this@ChatScreenActivity, false)
                        }
                    })
            }
        })

        viewModel.userSettingsListener.observe(this, EventObserver {
            when (it) {
                is UserSettingsFetched -> {
                    for (setting in it.settings) {
                        mutedRooms.add(setting.key)
                    }
                }

                UserSettingsFetchFailed -> Timber.d("Failed to fetch user settings")
                else -> Timber.d("Other error")
            }
        })

        viewModel.roomDataListener.observe(this, EventObserver {
            when (it) {
                is SingleRoomData -> {
                    val gson = Gson()
                    val roomData = gson.toJson(it.roomData.roomWithUsers)
                    replaceChatScreenActivity(this, roomData)
                }
                SingleRoomFetchFailed -> Timber.d("Failed to fetch room data")
                else -> Timber.d("Other error")
            }
        })

        viewModel.roomNotificationListener.observe(this, EventObserver {
            when (it) {
                is RoomNotificationData -> {
                    val myUserId = viewModel.getLocalUserId()
                    var isRoomMuted = false
                    for (key in mutedRooms) {
                        if (key.contains(it.message.roomId.toString())) {
                            isRoomMuted = true
                            break
                        }
                    }

                    if (myUserId == it.message.fromUserId || roomWithUsers?.room?.roomId == it.message.roomId || isRoomMuted) return@EventObserver
                    runOnUiThread {
                        val animator =
                            ValueAnimator.ofInt(bindingSetup.cvNotification.pbTimeout.max, 0)
                        animator.duration = 5000
                        animator.addUpdateListener { animation ->
                            bindingSetup.cvNotification.pbTimeout.progress =
                                animation.animatedValue as Int
                        }
                        animator.start()

                        if (it.roomWithUsers.room.type.equals(Const.JsonFields.GROUP)) {
                            Glide.with(this@ChatScreenActivity)
                                .load(it.roomWithUsers.room.avatarUrl?.let { avatarUrl ->
                                    Tools.getFileUrl(
                                        avatarUrl
                                    )
                                })
                                .into(bindingSetup.cvNotification.ivUserImage)
                            bindingSetup.cvNotification.tvTitle.text = it.roomWithUsers.room.name
                            for (user in it.roomWithUsers.users) {
                                if (user.id != myUserId && user.id == it.message.fromUserId) {
                                    val content: String =
                                        if (it.message.type != Const.JsonFields.TEXT) {
                                            user.displayName + ": " + getString(
                                                R.string.generic_shared,
                                                it.message.type.toString()
                                                    .replaceFirstChar { type -> type.uppercase() })
                                        } else {
                                            user.displayName + ": " + it.message.body?.text.toString()
                                        }

                                    bindingSetup.cvNotification.tvMessage.text =
                                        content
                                    break
                                }
                            }
                        } else {
                            for (user in it.roomWithUsers.users) {
                                if (user.id != myUserId && user.id == it.message.fromUserId) {
                                    Glide.with(this@ChatScreenActivity)
                                        .load(user.avatarUrl?.let { avatarUrl ->
                                            Tools.getFileUrl(
                                                avatarUrl
                                            )
                                        })
                                        .into(bindingSetup.cvNotification.ivUserImage)
                                    val content: String =
                                        if (it.message.type != Const.JsonFields.TEXT) {
                                            getString(
                                                R.string.generic_shared,
                                                it.message.type.toString()
                                                    .replaceFirstChar { type -> type.uppercase() })
                                        } else {
                                            it.message.body?.text.toString()
                                        }

                                    bindingSetup.cvNotification.tvTitle.text = user.displayName
                                    bindingSetup.cvNotification.tvMessage.text =
                                        content
                                    break
                                }
                            }
                        }


                        bindingSetup.cvNotification.cvRoot.visibility = View.VISIBLE

                        val roomId = it.message.roomId
                        bindingSetup.cvNotification.cvRoot.setOnClickListener {
                            roomId?.let { roomId -> viewModel.getSingleRoomData(roomId) }
                        }

                        runnable.let { runnable -> handler.removeCallbacks(runnable) }

                        handler = Handler(Looper.getMainLooper())
                        Timber.d("Starting handler")
                        handler.postDelayed(runnable, 5000)
                    }
                }
                is RoomWithUsersFailed -> Timber.d("Failed to fetch room with users")
                else -> Timber.d("Other error")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.getUserSettings()
        viewModel.getPushNotificationStream(object : SSEListener {
            override fun newMessageReceived(message: Message) {
                Timber.d("Message received")
                message.roomId?.let { viewModel.getRoomWithUsers(it, message) }
            }
        }).asLiveData(Dispatchers.IO).observe(this) {
            Timber.d("Observing SSE")
        }
    }

    override fun onBackPressed() {
        val fragment =
            this.supportFragmentManager.findFragmentById(R.id.main_chat_container) as? NavHostFragment
        val currentFragment =
            fragment?.childFragmentManager?.fragments?.get(0) as? ChatOnBackPressed

        // Check why this returns null if upload is not in progress
        currentFragment?.onBackPressed()?.takeIf { !it }.let {
            Timber.d("Boolean: $it")
            if (it == null) {
                super.onBackPressed()
            }
        }
    }


}

interface ChatOnBackPressed {
    fun onBackPressed(): Boolean
}