package com.clover.studio.exampleapp.ui.main.create_room

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.UserAndPhoneUser
import com.clover.studio.exampleapp.data.repositories.SharedPreferencesRepository
import com.clover.studio.exampleapp.databinding.FragmentGroupInformationBinding
import com.clover.studio.exampleapp.ui.main.MainViewModel
import com.clover.studio.exampleapp.ui.main.RoomCreated
import com.clover.studio.exampleapp.ui.main.RoomFailed
import com.clover.studio.exampleapp.ui.main.chat.startChatScreenActivity
import com.clover.studio.exampleapp.utils.*
import com.clover.studio.exampleapp.utils.Tools.getAvatarUrl
import com.clover.studio.exampleapp.utils.dialog.ChooserDialog
import com.clover.studio.exampleapp.utils.dialog.DialogError
import com.clover.studio.exampleapp.utils.dialog.DialogInteraction
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GroupInformationFragment : Fragment() {
    @Inject
    lateinit var uploadDownloadManager: UploadDownloadManager

    @Inject
    lateinit var sharedPrefs: SharedPreferencesRepository

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: GroupInformationAdapter
    private var selectedUsers: MutableList<UserAndPhoneUser> = ArrayList()
    private var currentPhotoLocation: Uri = Uri.EMPTY
    private var progress: Long = 1L
    private var avatarPath: String? = null

    private var bindingSetup: FragmentGroupInformationBinding? = null

    private val binding get() = bindingSetup!!

    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                val bitmap =
                    Tools.handleSamplingAndRotationBitmap(requireActivity(), it)
                val bitmapUri = Tools.convertBitmapToUri(requireActivity(), bitmap!!)

                Glide.with(this).load(bitmap).into(binding.ivPickPhoto)
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
                    Tools.handleSamplingAndRotationBitmap(requireActivity(), currentPhotoLocation)
                val bitmapUri = Tools.convertBitmapToUri(requireActivity(), bitmap!!)

                Glide.with(this).load(bitmap).into(binding.ivPickPhoto)
                binding.clSmallCameraPicker.visibility = View.VISIBLE
                currentPhotoLocation = bitmapUri
                updateGroupImage()
            } else {
                Timber.d("Photo error")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (requireArguments().getParcelableArrayList<UserAndPhoneUser>(Const.Navigation.SELECTED_USERS) == null) {
            DialogError.getInstance(requireActivity(),
                getString(R.string.error),
                getString(R.string.failed_user_data),
                null,
                getString(R.string.ok),
                object : DialogInteraction {
                    override fun onFirstOptionClicked() {
                        // ignore
                    }

                    override fun onSecondOptionClicked() {
                        // ignore
                    }
                })
            Timber.d("Failed to fetch user data")
        } else {
            selectedUsers =
                requireArguments().getParcelableArrayList(Const.Navigation.SELECTED_USERS)!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingSetup = FragmentGroupInformationBinding.inflate(inflater, container, false)

        setupAdapter()
        initializeObservers()
        initializeViews()

        return binding.root
    }

    private fun initializeViews() {
        binding.tvCreate.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val jsonObject = JsonObject()

                val userIdsArray = JsonArray()
                for (user in selectedUsers) {
                    userIdsArray.add(user.user.id)
                }
                val adminUserIds = JsonArray()
                adminUserIds.add(sharedPrefs.readUserId())

                jsonObject.addProperty(
                    Const.JsonFields.NAME,
                    binding.etEnterUsername.text.toString()
                )
                jsonObject.addProperty(Const.JsonFields.AVATAR_URL,
                    avatarPath?.let { path -> getAvatarUrl(path) })
                jsonObject.add(Const.JsonFields.USER_IDS, userIdsArray)
                jsonObject.add(Const.JsonFields.ADMIN_USER_IDS, adminUserIds)
                jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.GROUP)

                viewModel.createNewRoom(jsonObject)
            }
        }

        binding.tvPeopleSelected.text = getString(R.string.s_people_selected, selectedUsers.size)
        adapter.submitList(selectedUsers)

        binding.etEnterUsername.addTextChangedListener {
            if (binding.etEnterUsername.text.isNotEmpty()) {
                binding.tvCreate.isClickable = true
                binding.tvCreate.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.primary_color
                    )
                )
            } else {
                binding.tvCreate.isClickable = false
                binding.tvCreate.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.text_tertiary
                    )
                )
            }
        }

        binding.etEnterUsername.setOnFocusChangeListener { view, hasFocus ->
            run {
                if (!hasFocus) {
                    Tools.hideKeyboard(requireActivity(), view)
                }
            }
        }

        binding.cvPhotoPicker.setOnClickListener {
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

    private fun initializeObservers() {
        viewModel.createRoomListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                is RoomCreated -> {
                    val gson = Gson()
                    val roomData = gson.toJson(it.roomData)
                    activity?.let { parent -> startChatScreenActivity(parent, roomData) }
                    activity?.finish()
                }
                is RoomFailed -> Timber.d("Failed to create room")
                else -> Timber.d("Other error")
            }
        })
    }

    private fun setupAdapter() {
        adapter = GroupInformationAdapter(requireContext()) {
            selectedUsers.remove(it)
            adapter.submitList(selectedUsers)
            binding.tvPeopleSelected.text =
                getString(R.string.s_people_selected, selectedUsers.size)
            adapter.notifyDataSetChanged()
        }

        binding.rvContacts.adapter = adapter
        binding.rvContacts.layoutManager =
            LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
    }

    private fun chooseImage() {
        chooseImageContract.launch(Const.JsonFields.IMAGE)
    }

    private fun takePhoto() {
        currentPhotoLocation = FileProvider.getUriForFile(
            requireActivity(),
            "com.clover.studio.exampleapp.fileprovider",
            Tools.createImageFile(requireActivity())
        )
        Timber.d("$currentPhotoLocation")
        takePhotoContract.launch(currentPhotoLocation)
    }

    private fun updateGroupImage() {
        if (currentPhotoLocation != Uri.EMPTY) {
            val inputStream =
                requireActivity().contentResolver.openInputStream(currentPhotoLocation)

            val fileStream = Tools.copyStreamToFile(requireActivity(), inputStream!!)
            val uploadPieces =
                if ((fileStream.length() % CHUNK_SIZE).toInt() != 0)
                    fileStream.length() / CHUNK_SIZE + 1
                else fileStream.length() / CHUNK_SIZE

            binding.progressBar.max = uploadPieces.toInt()
            Timber.d("File upload start")
            CoroutineScope(Dispatchers.IO).launch {
                uploadDownloadManager.uploadFile(
                    requireActivity(),
                    currentPhotoLocation,
                    Const.JsonFields.IMAGE,
                    Const.JsonFields.AVATAR,
                    uploadPieces,
                    fileStream,
                    object :
                        FileUploadListener {
                        override fun filePieceUploaded() {
                            if (progress <= uploadPieces) {
                                binding.progressBar.secondaryProgress = progress.toInt()
                                progress++
                            } else progress = 0
                        }

                        override fun fileUploadError() {
                            Timber.d("Upload Error")
                            showUploadError()
                        }

                        override fun fileUploadVerified(path: String) {
                            Timber.d("Upload verified")
                            requireActivity().runOnUiThread {
                                binding.clProgressScreen.visibility = View.GONE
                            }
                            avatarPath = path
                        }

                    })
            }
            binding.clProgressScreen.visibility = View.VISIBLE
        }
    }

    private fun showUploadError() {
        DialogError.getInstance(requireActivity(),
            getString(R.string.error),
            getString(R.string.image_failed_upload),
            null,
            getString(R.string.ok),
            object : DialogInteraction {
                override fun onFirstOptionClicked() {
                    // ignore
                }

                override fun onSecondOptionClicked() {
                    // ignore
                }
            })
        binding.clProgressScreen.visibility = View.GONE
        binding.progressBar.secondaryProgress = 0
        currentPhotoLocation = Uri.EMPTY
        Glide.with(this).clear(binding.ivPickPhoto)
        binding.ivPickPhoto.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.img_camera
            )
        )
        binding.clSmallCameraPicker.visibility = View.GONE
    }
}