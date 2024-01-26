package com.clover.studio.spikamessenger.ui.main.chat_details

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
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
import com.clover.studio.spikamessenger.utils.UserOptions
import com.clover.studio.spikamessenger.utils.dialog.ChooserDialog
import com.clover.studio.spikamessenger.utils.dialog.DialogError
import com.clover.studio.spikamessenger.utils.extendables.BaseFragment
import com.clover.studio.spikamessenger.utils.extendables.DialogInteraction
import com.clover.studio.spikamessenger.utils.getChunkSize
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.clover.studio.spikamessenger.utils.helpers.UploadService
import com.clover.studio.spikamessenger.utils.helpers.UserOptionsData
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ChatDetailsFragment : BaseFragment(), ServiceConnection {

    @Inject
    lateinit var sharedPrefs: SharedPreferencesRepository

    private val viewModel: ChatViewModel by activityViewModels()
    private val args: ChatDetailsFragmentArgs by navArgs()
    private lateinit var adapter: ChatDetailsAdapter
    private var currentPhotoLocation: Uri = Uri.EMPTY
    private var roomUsers: MutableList<User> = ArrayList()
    private lateinit var roomWithUsers: RoomWithUsers
    private var uploadPieces: Int = 0
    private var roomId: Int? = null
    private var isAdmin = false
    private var localUserId: Int? = 0
    private lateinit var fileUploadService: UploadService
    private var bound = false

    private var userName = ""
    private var avatarFileId = 0L
    private var isUploading = false

    private var bindingSetup: FragmentChatDetailsBinding? = null
    private val binding get() = bindingSetup!!

    private var modifiedList: List<User> = mutableListOf()
    private var optionList: MutableList<UserOptionsData> = mutableListOf()
    private var pinSwitch: Drawable? = null
    private var muteSwitch: Drawable? = null
    private var avatarData: FileData? = null

    private var navOptionsBuilder: NavOptions? = null

    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                val bitmap =
                    Tools.handleSamplingAndRotationBitmap(requireActivity(), it, false)
                val bitmapUri = Tools.convertBitmapToUri(requireActivity(), bitmap!!)

                Glide.with(this).load(bitmap).centerCrop().into(binding.profilePicture.ivPickAvatar)
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

                Glide.with(this).load(bitmap).centerCrop().into(binding.profilePicture.ivPickAvatar)
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

        navOptionsBuilder = Tools.createCustomNavOptions()

        handleUserStatusViews(isAdmin)
        initializeViews(roomWithUsers)

        setOptionList()

        val userOptions = UserOptions(requireContext())
        userOptions.setOptions(optionList)
        userOptions.setOptionsListener(object : UserOptions.OptionsListener {
            override fun clickedOption(option: Int, optionName: String) {
                when (optionName) {
                    getString(R.string.notes) -> goToNotes()
                    getString(R.string.delete_chat) -> goToDeleteChat()
                    getString(R.string.exit_group) -> goToExitGroup()
                }
            }

            override fun switchOption(optionName: String, isSwitched: Boolean) {
                switchPinMuteOptions(optionName, isSwitched)
            }
        })
        binding.flOptionsContainer.addView(userOptions)

        initializeObservers()

        return binding.root
    }

    private fun goToExitGroup() {
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

    private fun goToDeleteChat() {
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

    private fun handleUserStatusViews(isAdmin: Boolean) = with(binding) {
        if (!isAdmin) {
            tvGroupName.isClickable = false
            ivDone.isFocusable = false
            profilePicture.ivPickAvatar.isClickable = false
            profilePicture.ivPickAvatar.isFocusable = false
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

        // This will stop image file changes while file is uploading via LiveData
        if (!isUploading && ivDone.visibility == View.GONE) {
            setAvatarAndUsername(avatarFileId, userName)
        }

        initializeListeners(roomWithUsers)
    }

    private fun switchPinMuteOptions(optionName: String, isSwitched: Boolean) {
        if (optionName == getString(R.string.pin_chat)) {
            viewModel.handleRoomPin(roomWithUsers.room.roomId, isSwitched)
        } else {
            viewModel.handleRoomMute(roomWithUsers.room.roomId, isSwitched)
        }
    }

    private fun setOptionList() = with(binding) {
        val pinId =
            if (roomWithUsers.room.pinned) R.drawable.img_switch else R.drawable.img_switch_left
        val muteId =
            if (roomWithUsers.room.muted) R.drawable.img_switch else R.drawable.img_switch_left

        pinSwitch = AppCompatResources.getDrawable(requireContext(), pinId)
        muteSwitch = AppCompatResources.getDrawable(requireContext(), muteId)

        optionList = mutableListOf(
            UserOptionsData(
                option = getString(R.string.notes),
                firstDrawable = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.img_notes
                ),
                secondDrawable = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.img_arrow_forward
                ),
                switchOption = false,
                isSwitched = false,
            ),
            UserOptionsData(
                option = getString(R.string.pin_chat),
                firstDrawable = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.img_pin
                ),
                secondDrawable = pinSwitch,
                switchOption = true,
                isSwitched = roomWithUsers.room.pinned
            ),
            UserOptionsData(
                option = getString(R.string.mute),
                firstDrawable = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.img_mute
                ),
                secondDrawable = muteSwitch,
                switchOption = true,
                isSwitched = roomWithUsers.room.pinned
            ),
        )

        if (isAdmin) {
            optionList.add(
                UserOptionsData(
                    option = getString(R.string.delete_chat),
                    firstDrawable = AppCompatResources.getDrawable(
                        requireContext(),
                        R.drawable.img_delete_note
                    ),
                    secondDrawable = null,
                    switchOption = false,
                    isSwitched = false
                )
            )
            ivAddMember.visibility = View.VISIBLE

        }

        if (!roomWithUsers.room.roomExit) {
            optionList.add(
                UserOptionsData(
                    option = getString(R.string.exit_group),
                    firstDrawable = AppCompatResources.getDrawable(
                        requireContext(),
                        R.drawable.img_chat_exit
                    ),
                    secondDrawable = null,
                    switchOption = false,
                    isSwitched = false
                )
            )
        }
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
                tilEnterGroupName.visibility = View.VISIBLE
                etEnterGroupName.setText(tvGroupName.text)
                showKeyboard(etEnterGroupName)
                tvGroupPlaceholder.visibility = View.GONE
                ivDone.visibility = View.VISIBLE
                tvGroupName.visibility = View.GONE
            }
        }

        ivDone.setOnClickListener {
            val roomName = etEnterGroupName.text.toString()
            val jsonObject = JsonObject()
            if (roomName.isNotEmpty()) {
                jsonObject.addProperty(Const.JsonFields.NAME, roomName)
            }

            jsonObject.addProperty(Const.JsonFields.ACTION, Const.JsonFields.CHANGE_GROUP_NAME)

            viewModel.updateRoom(jsonObject, roomWithUsers.room.roomId, roomWithUsers.users.size)

            ivDone.visibility = View.GONE
            tilEnterGroupName.visibility = View.GONE
            tvGroupPlaceholder.visibility = View.VISIBLE
            tvGroupName.visibility = View.VISIBLE
        }

        profilePicture.ivPickAvatar.setOnClickListener {
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

        binding.ivBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    private fun goToNotes() {
        val action = roomId?.let { id ->
            ChatDetailsFragmentDirections.actionChatDetailsFragmentToNotesFragment(
                id
            )
        }
        if (action != null) {
            findNavController().navigate(action)
        }
    }

    private fun setAvatarAndUsername(avatarFileId: Long, username: String) {
        if (avatarFileId != 0L) {
            Glide.with(this)
                .load(avatarFileId.let { Tools.getFilePathUrl(it) })
                .centerCrop()
                .placeholder(R.drawable.img_group_avatar)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(binding.profilePicture.ivPickAvatar)
        }
        binding.tvGroupName.text = username
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
            } else if (isAdmin) {
                getString(R.string.dismiss_as_admin)
            } else {
                null
            }
        } else {
            null
        }

        user.formattedDisplayName.let {
            if (user.id != localUserId) {
                ChooserDialog.getInstance(requireContext(),
                    it,
                    null,
                    getString(R.string.info),
                    adminText,
                    object : DialogInteraction {
                        override fun onFirstOptionClicked() {
                            val privateGroupUser = Tools.transformUserToPrivateGroupChat(user)
                            val bundle =
                                bundleOf(
                                    Const.Navigation.USER_PROFILE to privateGroupUser,
                                    Const.Navigation.ROOM_ID to roomWithUsers.room.roomId,
                                    Const.Navigation.ROOM_DATA to roomWithUsers.room
                                )
                            findNavController().navigate(
                                R.id.action_chatDetailsFragment_to_contactDetailsFragment,
                                bundle,
                                navOptionsBuilder
                            )
                        }

                        override fun onSecondOptionClicked() {
                            if (getString(R.string.dismiss_as_admin) == adminText) {
                                user.isAdmin = false
                                removeAdmin(user.id)
                            } else {
                                user.isAdmin = true
                                makeAdmin(user.id)
                            }

                            modifiedList =
                                roomUsers.sortedBy { roomUser -> roomUser.isAdmin }.reversed()
                            adapter.submitList(modifiedList.toList())
                            adapter.notifyDataSetChanged()
                        }

                    })
            }
        }
    }

    private fun removeUser(user: User) {
        DialogError.getInstance(requireActivity(),
            getString(R.string.remove_from_group),
            getString(R.string.remove_person, user.formattedDisplayName),
            getString(R.string.cancel),
            getString(R.string.ok),
            object : DialogInteraction {
                override fun onSecondOptionClicked() {
                    roomUsers.remove(user)
                    updateRoomUsers(user.id)
                    modifiedList =
                        roomUsers.sortedBy { roomUser -> roomUser.isAdmin }.reversed()
                    adapter.submitList(modifiedList.toList())
                    adapter.notifyDataSetChanged()
                }
            })
    }

    private fun makeAdmin(userId: Int) {
        val jsonObject = JsonObject()
        val adminIds = JsonArray()

        adminIds.add(userId)

        if (adminIds.size() > 0) {
            jsonObject.addProperty(Const.JsonFields.ACTION, Const.JsonFields.ADD_GROUP_ADMINS)
            jsonObject.add(Const.JsonFields.USER_IDS, adminIds)
        }

        roomId?.let { viewModel.updateRoom(jsonObject, it, roomUsers.size) }
    }

    private fun removeAdmin(userId: Int) {
        val jsonObject = JsonObject()
        val adminIds = JsonArray()

        adminIds.add(userId)

        if (adminIds.size() > 0) {
            jsonObject.addProperty(Const.JsonFields.ACTION, Const.JsonFields.REMOVE_GROUP_ADMINS)
            jsonObject.add(Const.JsonFields.USER_IDS, adminIds)
        }

        roomId?.let { viewModel.updateRoom(jsonObject, it, roomUsers.size) }
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
            adapter.submitList(modifiedList.toList())

        }
    }

    private fun updateGroupImage() {
        if (currentPhotoLocation != Uri.EMPTY) {
            val inputStream =
                requireActivity().contentResolver.openInputStream(currentPhotoLocation)

            val fileStream = Tools.copyStreamToFile(
                inputStream!!,
                activity?.contentResolver?.getType(currentPhotoLocation)!!
            )
            uploadPieces =
                if ((fileStream.length() % getChunkSize(fileStream.length())).toInt() != 0)
                    (fileStream.length() / getChunkSize(fileStream.length()) + 1).toInt()
                else (fileStream.length() / getChunkSize(fileStream.length())).toInt()

            binding.profilePicture.progressBar.max = uploadPieces
            Timber.d("File upload start")
            isUploading = true

            avatarData = FileData(
                fileUri = currentPhotoLocation,
                fileType = Const.JsonFields.AVATAR_TYPE,
                filePieces = uploadPieces,
                file = fileStream,
                messageBody = null,
                isThumbnail = false,
                localId = null,
                roomId = roomWithUsers.room.roomId,
                messageStatus = null,
                metadata = null
            )

            if (bound) {
                CoroutineScope(Dispatchers.Default).launch {
                    avatarData?.let {
                        fileUploadService.uploadAvatar(
                            fileData = it,
                            isGroup = true
                        )
                    }
                }
            } else {
                startUploadService()
            }
            binding.profilePicture.flProgressScreen.visibility = View.VISIBLE
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
        binding.profilePicture.flProgressScreen.visibility = View.GONE
        binding.profilePicture.progressBar.secondaryProgress = 0
        currentPhotoLocation = Uri.EMPTY
        Glide.with(this).clear(binding.profilePicture.ivPickAvatar)
        binding.profilePicture.ivPickAvatar.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.img_camera
            )
        )
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

        userIds.add(idToRemove)
        jsonObject.addProperty(Const.JsonFields.ACTION, Const.JsonFields.REMOVE_GROUP_USERS)
        jsonObject.add(Const.JsonFields.USER_IDS, userIds)

        roomId?.let { viewModel.updateRoom(jsonObject, it, roomUsers.size) }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            requireActivity().unbindService(serviceConnection)
        }
        bound = false
    }

    /** Upload service */
    private fun startUploadService() {
        val intent = Intent(MainApplication.appContext, UploadService::class.java)
        MainApplication.appContext.startService(intent)
        activity?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private val serviceConnection = this
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        bound = true

        val binder = service as UploadService.UploadServiceBinder
        fileUploadService = binder.getService()
        fileUploadService.setCallbackListener(object : UploadService.FileUploadCallback {
            override fun uploadError(description: String) {
                Timber.d("Upload Error")
                requireActivity().runOnUiThread {
                    showUploadError(description)
                }
            }

            override fun avatarUploadFinished() {
                requireActivity().runOnUiThread {
                    binding.profilePicture.flProgressScreen.visibility = View.GONE
                }
            }
        })

        CoroutineScope(Dispatchers.Default).launch {
            avatarData?.let { fileUploadService.uploadAvatar(fileData = it, isGroup = true) }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Timber.d("Service disconnected")
    }
}
