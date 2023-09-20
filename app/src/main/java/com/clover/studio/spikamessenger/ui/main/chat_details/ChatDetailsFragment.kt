package com.clover.studio.spikamessenger.ui.main.chat_details

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.clover.studio.spikamessenger.BuildConfig
import com.clover.studio.spikamessenger.MainApplication
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.FileData
import com.clover.studio.spikamessenger.data.models.entity.User
import com.clover.studio.spikamessenger.data.models.junction.RoomWithUsers
import com.clover.studio.spikamessenger.data.repositories.SharedPreferencesRepository
import com.clover.studio.spikamessenger.databinding.FragmentChatDetailsBinding
import com.clover.studio.spikamessenger.ui.main.chat.ChatViewModel
import com.clover.studio.spikamessenger.utils.Const
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.UploadDownloadManager
import com.clover.studio.spikamessenger.utils.dialog.ChooserDialog
import com.clover.studio.spikamessenger.utils.dialog.DialogError
import com.clover.studio.spikamessenger.utils.extendables.BaseFragment
import com.clover.studio.spikamessenger.utils.extendables.DialogInteraction
import com.clover.studio.spikamessenger.utils.helpers.FilesHelper
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.clover.studio.spikamessenger.utils.helpers.UploadService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ChatDetailsFragment : BaseFragment() {

    @Inject
    lateinit var uploadDownloadManager: UploadDownloadManager

    @Inject
    lateinit var sharedPrefs: SharedPreferencesRepository

    private val viewModel: ChatViewModel by activityViewModels()
    private val args: ChatDetailsFragmentArgs by navArgs()
    private lateinit var adapter: ChatDetailsAdapter
    private var currentPhotoLocation: Uri = Uri.EMPTY
    private var roomUsers: MutableList<User> = ArrayList()
    private lateinit var roomWithUsers: RoomWithUsers
    private var roomId: Int? = null
    private var isAdmin = false
    private var localUserId: Int? = 0

    private var userName = ""
    private var avatarFileId = 0L
    private var newAvatarFileId = 0L

    private var bindingSetup: FragmentChatDetailsBinding? = null
    private val binding get() = bindingSetup!!

    private var allUsers = false
    private var modifiedList: List<User> = mutableListOf()

    private lateinit var fileUploadService: UploadService

    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                val bitmap =
                    Tools.handleSamplingAndRotationBitmap(requireActivity(), it, false)
                val bitmapUri = Tools.convertBitmapToUri(requireActivity(), bitmap!!)

                Glide.with(this)
                    .load(bitmapUri)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(binding.ivPickAvatar)

                binding.clSmallCameraPicker.visibility = View.VISIBLE
                currentPhotoLocation = bitmapUri
                updateGroupImage()
            } else {
                Timber.d("Gallery error")
            }
        }

    private val takePhotoContract =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                val bitmap =
                    Tools.handleSamplingAndRotationBitmap(
                        requireActivity(),
                        currentPhotoLocation,
                        false
                    )
                val bitmapUri = Tools.convertBitmapToUri(requireActivity(), bitmap!!)

                Glide.with(this).load(bitmap).centerCrop().into(binding.ivPickAvatar)
                binding.clSmallCameraPicker.visibility = View.VISIBLE
                currentPhotoLocation = bitmapUri
                updateGroupImage()
            } else {
                Timber.d("Photo error")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fetch room data sent from previous fragment
        roomWithUsers = args.roomWithUsers
        localUserId = viewModel.getLocalUserId()
        isAdmin = roomWithUsers.users.any { user ->
            user.id == localUserId && viewModel.isUserAdmin(roomWithUsers.room.roomId, user.id)
        }
        roomId = roomWithUsers.room.roomId
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingSetup = FragmentChatDetailsBinding.inflate(inflater, container, false)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(roomWithUsers)
        if (savedInstanceState == null) {
            setAvatarAndUsername(roomWithUsers.room.avatarFileId!!)
        }

        initializeObservers()
        handleUserStatusViews(isAdmin)
    }

    private fun handleUserStatusViews(isAdmin: Boolean) = with(binding) {
        if (!isAdmin) {
            tvGroupName.isClickable = false
            ivDone.isFocusable = false
            ivPickAvatar.isClickable = false
            ivPickAvatar.isFocusable = false
            ivAddMember.visibility = View.GONE
        }
    }

    private fun initializeViews(roomWithUsers: RoomWithUsers) = with(binding) {
        setupAdapter(isAdmin, roomWithUsers.room.type.toString())
        clMemberList.visibility = View.VISIBLE
        userName = roomWithUsers.room.name.toString()
        avatarFileId = roomWithUsers.room.avatarFileId!!

        tvMembersNumber.text =
            getString(R.string.number_of_members, roomWithUsers.users.size)

        if (isAdmin) {
            tvDelete.visibility = View.VISIBLE
            ivAddMember.visibility = View.VISIBLE
        }

        if (!roomWithUsers.room.roomExit) {
            tvExitGroup.visibility = View.VISIBLE
        } else {
            tvExitGroup.visibility = View.GONE
        }

        binding.chatHeader.tvTitle.text = roomWithUsers.room.type

        initializeListeners(roomWithUsers)
    }

    private fun initializeListeners(roomWithUsers: RoomWithUsers) = with(binding) {
        ivAddMember.setOnClickListener {
            val userIds = ArrayList<Int>()
            for (user in roomWithUsers.users) {
                userIds.add(user.id)
            }

            findNavController().navigate(
                ChatDetailsFragmentDirections.actionChatDetailsFragmentToNewRoomFragment(
                    userIds.stream().mapToInt { i -> i }.toArray(),
                    roomWithUsers.room.roomId
                )
            )
        }

        tvGroupName.setOnClickListener {
            if (roomWithUsers.room.type.toString() == Const.JsonFields.GROUP && isAdmin) {
                etEnterGroupName.visibility = View.VISIBLE
                ivDone.visibility = View.VISIBLE
                tvGroupName.visibility = View.INVISIBLE
            }
        }

        ivDone.setOnClickListener {
            val roomName = etEnterGroupName.text.toString()
            val jsonObject = JsonObject()
            if (roomName.isNotEmpty()) {
                jsonObject.addProperty(Const.JsonFields.NAME, roomName)
            }

            if (newAvatarFileId != 0L) {
                jsonObject.addProperty(Const.JsonFields.AVATAR_FILE_ID, newAvatarFileId)
            }

            viewModel.updateRoom(jsonObject, roomWithUsers.room.roomId, 0)

            ivDone.visibility = View.GONE
            etEnterGroupName.visibility = View.INVISIBLE
            tvGroupName.visibility = View.VISIBLE

            if (newAvatarFileId != 0L) {
                setAvatarAndUsername(newAvatarFileId)
            }
        }

        flNotes.setOnClickListener {
            val action = roomId?.let { id ->
                ChatDetailsFragmentDirections.actionChatDetailsFragmentToNotesFragment(
                    id
                )
            }
            if (action != null) {
                findNavController().navigate(action)
            }
        }

        ivPickAvatar.setOnClickListener {
            if ((Const.JsonFields.GROUP == roomWithUsers.room.type) && isAdmin) {
                ChooserDialog.getInstance(requireContext(),
                    getString(R.string.placeholder_title),
                    null,
                    getString(R.string.choose_from_gallery),
                    getString(R.string.take_photo),
                    object : DialogInteraction {
                        override fun onFirstOptionClicked() {
                            chooseImage()
                        }

                        override fun onSecondOptionClicked() {
                            takePhoto()
                        }
                    })
            }
        }

        binding.chatHeader.ivArrowBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        // Rooms can only be deleted by room admins.
        tvDelete.setOnClickListener {
            if (isAdmin) {
                DialogError.getInstance(requireActivity(),
                    getString(R.string.delete_chat),
                    getString(R.string.delete_chat_description),
                    getString(
                        R.string.yes
                    ),
                    getString(R.string.no),
                    object : DialogInteraction {
                        override fun onFirstOptionClicked() {
                            roomId?.let { id -> viewModel.deleteRoom(id) }
                            activity?.finish()
                        }
                    })
            }
        }

        tvExitGroup.setOnClickListener {
            val adminIds = ArrayList<Int>()
            for (user in roomUsers) {
                if (user.isAdmin)
                    adminIds.add(user.id)
            }

            // Exit condition:
            // If current user is not admin
            // Or current user is admin and and there are other admins
            if (!isAdmin || (adminIds.size > 1) && isAdmin) {
                DialogError.getInstance(requireActivity(),
                    getString(R.string.exit_group),
                    getString(R.string.exit_group_description),
                    getString(R.string.cancel),
                    getString(
                        R.string.exit
                    ),
                    object : DialogInteraction {
                        override fun onSecondOptionClicked() {
                            roomId?.let { id -> viewModel.leaveRoom(id) }
                            // Remove if admin
                            if (isAdmin) {
                                roomId?.let { id -> viewModel.removeAdmin(id, localUserId!!) }
                            }
                            activity?.finish()
                        }
                    })
            } else {
                DialogError.getInstance(requireActivity(),
                    getString(R.string.exit_group),
                    getString(R.string.exit_group_error),
                    null,
                    getString(R.string.ok),
                    object : DialogInteraction {
                        override fun onSecondOptionClicked() {
                            // Ignore
                        }
                    })
            }
        }

        tvSeeMoreLess.setOnClickListener {
            if (allUsers) {
                adapter.submitList(modifiedList.toList())
                tvSeeMoreLess.text = context!!.getString(R.string.see_less)
                allUsers = false
            } else {
                adapter.submitList(modifiedList.subList(0, 3).toList())
                tvSeeMoreLess.text = context!!.getString(R.string.see_more)
                allUsers = true
            }
        }
    }

    private fun updateImage() {
        val jsonObject = JsonObject()

        if (newAvatarFileId != 0L) {
            jsonObject.addProperty(Const.JsonFields.AVATAR_FILE_ID, newAvatarFileId)
            viewModel.updateRoom(jsonObject, roomWithUsers.room.roomId, 0)
            setAvatarAndUsername(newAvatarFileId)
        }
    }

    // Listener which handles switch events and sends event to specific switch
    private val multiListener: OnCheckedChangeListener =
        OnCheckedChangeListener { buttonView, isChecked ->
            when (buttonView.id) {
                binding.swPinChat.id -> {
                    if (buttonView.isPressed) {
                        if (isChecked) {
                            roomId?.let { viewModel.handleRoomPin(it, true) }
                        } else {
                            roomId?.let { viewModel.handleRoomPin(it, false) }
                        }
                    }
                }

                binding.swMute.id -> {
                    if (buttonView.isPressed) {
                        if (isChecked) {
                            roomId?.let { viewModel.handleRoomMute(it, true) }
                        } else {
                            roomId?.let { viewModel.handleRoomMute(it, false) }
                        }
                    }
                }
            }
        }

    private fun setAvatarAndUsername(avatarFileId: Long) {
        val imageUrl = avatarFileId.let { Tools.getFilePathUrl(it) }

        val glideRequest = Glide.with(this)
            .load(imageUrl)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

        glideRequest.into(binding.ivPickAvatar)
        glideRequest.into(binding.chatHeader.ivUserImage)

        binding.tvGroupName.text = roomWithUsers.room.name
        binding.chatHeader.tvChatName.text = roomWithUsers.room.name
    }

    private fun initializeObservers() {
        roomId?.let {
            viewModel.getRoomAndUsers(it).observe(viewLifecycleOwner) { data ->
                when (data.status) {
                    Resource.Status.SUCCESS -> {
                        val roomWithUsers = data.responseData
                        if (roomWithUsers != null) {
                            initializeViews(roomWithUsers)
                            if (Const.JsonFields.GROUP == roomWithUsers.room.type) {
                                updateRoomUserList(roomWithUsers)
                            }
                        }
                    }

                    Resource.Status.LOADING -> {
                        // Add loading bar
                    }

                    else -> {
                        Timber.d("Error: $data")
                    }
                }
            }
        }
    }

    private fun setupAdapter(isAdmin: Boolean, roomType: String) {
        adapter = ChatDetailsAdapter(
            requireContext(),
            isAdmin,
            roomType,
            onUserInteraction = { event, user ->
                when (event) {
                    Const.UserActions.USER_OPTIONS -> userActions(user, roomType)
                    Const.UserActions.USER_REMOVE -> removeUser(user)
                    else -> Timber.d("No other action currently")
                }
            }
        )

        binding.rvGroupMembers.adapter = adapter
        binding.rvGroupMembers.layoutManager =
            LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
    }

    private fun userActions(user: User, roomType: String) {
        val adminText = if (Const.JsonFields.GROUP == roomType) {
            if (isAdmin && !user.isAdmin) {
                getString(R.string.make_group_admin)
            } else {
                null
            }
        } else {
            null
        }

        user.formattedDisplayName.let {
            ChooserDialog.getInstance(requireContext(),
                it,
                null,
                getString(R.string.info),
                adminText,
                object : DialogInteraction {
                    override fun onFirstOptionClicked() {
                        // TODO("Not yet implemented")
                    }

                    override fun onSecondOptionClicked() {
                        user.isAdmin = true
                        makeAdmin()
                        modifiedList =
                            roomUsers.sortedBy { roomUser -> roomUser.isAdmin }.reversed()
                        adapter.submitList(modifiedList.toList())
                        adapter.notifyDataSetChanged()
                    }

                })
        }
    }

    private fun removeUser(user: User) {
        roomUsers.remove(user)
        updateRoomUsers(user.id)
        modifiedList =
            roomUsers.sortedBy { roomUser -> roomUser.isAdmin }.reversed()
        adapter.submitList(modifiedList.toList())
        adapter.notifyDataSetChanged()
    }

    private fun makeAdmin() {
        val jsonObject = JsonObject()
        val adminIds = JsonArray()

        for (user in roomUsers) {
            if (user.isAdmin)
                adminIds.add(user.id)
        }

        if (adminIds.size() > 0)
            jsonObject.add(Const.JsonFields.ADMIN_USER_IDS, adminIds)

        roomId?.let { viewModel.updateRoom(jsonObject, it, 0) }

    }

    private fun updateRoomUserList(roomWithUsers: RoomWithUsers) {
        roomUsers.clear()
        roomUsers.addAll(roomWithUsers.users)
        runBlocking {
            for (user in roomUsers) {
                if (roomId?.let { viewModel.isUserAdmin(it, user.id) } == true) {
                    user.isAdmin = true
                }
            }
            modifiedList = roomUsers.sortedBy { user -> user.isAdmin }.reversed()
            if (modifiedList.size > 3) {
                adapter.submitList(modifiedList.subList(0, 3).toList())
                binding.tvSeeMoreLess.visibility = View.VISIBLE
                binding.tvSeeMoreLess.text = context!!.getString(R.string.see_more)
                allUsers = true
            } else {
                adapter.submitList(modifiedList.toList())
                binding.tvSeeMoreLess.visibility = View.GONE
            }
        }
    }

    private fun updateGroupImage() {
        if (currentPhotoLocation != Uri.EMPTY) {
            val profilePicture =
                FilesHelper.uploadFile(
                    isThumbnail = false,
                    uri = currentPhotoLocation,
                    localId = "0",
                    roomId = roomWithUsers.room.roomId,
                    metadata = null,
                    isProfilePicture = true
                )
            val uploadData: ArrayList<FileData> = ArrayList()
            uploadData.add(profilePicture)
            startUploadService(uploadData)

            binding.flProgressScreen.visibility = View.VISIBLE
        }
    }

    private fun showUploadError(description: String) {
        DialogError.getInstance(requireActivity(),
            getString(R.string.error),
            getString(R.string.image_failed_upload, description),
            null,
            getString(R.string.ok),
            object : DialogInteraction {
                override fun onFirstOptionClicked() {
                    // Ignore
                }

                override fun onSecondOptionClicked() {
                    // Ignore
                }
            })
        binding.flProgressScreen.visibility = View.GONE
        binding.progressBar.secondaryProgress = 0
        currentPhotoLocation = Uri.EMPTY
        Glide.with(this).clear(binding.ivPickAvatar)
        binding.ivPickAvatar.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.img_camera
            )
        )
        binding.clSmallCameraPicker.visibility = View.GONE
    }

    private fun chooseImage() {
        chooseImageContract.launch(Const.JsonFields.IMAGE)
    }

    private fun takePhoto() {
        currentPhotoLocation = FileProvider.getUriForFile(
            requireActivity(),
            BuildConfig.APPLICATION_ID + ".fileprovider",
            Tools.createImageFile(requireActivity())
        )
        Timber.d("$currentPhotoLocation")
        takePhotoContract.launch(currentPhotoLocation)
    }

    private fun updateRoomUsers(idToRemove: Int) {
        val jsonObject = JsonObject()
        val userIds = JsonArray()

        for (user in roomUsers) {
            if (!user.isAdmin)
                userIds.add(user.id)
        }

        jsonObject.add(Const.JsonFields.USER_IDS, userIds)

        roomId?.let { viewModel.updateRoom(jsonObject, it, idToRemove) }
    }

    /** Upload service */
    private fun startUploadService(files: ArrayList<FileData>) {
        val intent = Intent(MainApplication.appContext, UploadService::class.java)
        intent.putParcelableArrayListExtra(Const.IntentExtras.FILES_EXTRA, files)
        MainApplication.appContext.startService(intent)
        activity?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UploadService.UploadServiceBinder
            fileUploadService = binder.getService()
            fileUploadService.setCallbackListener(object : UploadService.FileUploadCallback {
                override fun updateUploadProgressBar(
                    progress: Int,
                    maxProgress: Int,
                    localId: String?
                ) {
                    binding.progressBar.secondaryProgress = (progress * 100) / maxProgress
                }

                override fun uploadingFinished(uploadedFiles: MutableList<FileData>) {
                    Tools.deleteTemporaryMedia(context!!)
                    context?.cacheDir?.deleteRecursively()


                    if (isVisible || isResumed) {
                        activity!!.runOnUiThread {
                            binding.flProgressScreen.visibility = View.GONE
                        }
                    }
                    updateImage()
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("Service disconnected")
        }
    }
}

