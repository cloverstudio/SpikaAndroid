package com.clover.studio.exampleapp.ui.onboarding.account_creation

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.databinding.FragmentAccountCreationBinding
import com.clover.studio.exampleapp.ui.main.startMainActivity
import com.clover.studio.exampleapp.ui.onboarding.OnboardingStates
import com.clover.studio.exampleapp.ui.onboarding.OnboardingViewModel
import com.clover.studio.exampleapp.utils.*
import com.clover.studio.exampleapp.utils.Tools.convertBitmapToUri
import com.clover.studio.exampleapp.utils.dialog.ChooserDialog
import com.clover.studio.exampleapp.utils.dialog.DialogError
import com.clover.studio.exampleapp.utils.dialog.DialogInteraction
import com.clover.studio.exampleapp.utils.extendables.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class AccountCreationFragment @Inject constructor(
    private val uploadDownloadManager: UploadDownloadManager
) : BaseFragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()
    private var currentPhotoLocation: Uri = Uri.EMPTY
    private var progress: Long = 1L

    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                val bitmap =
                    Tools.handleSamplingAndRotationBitmap(requireActivity(), it)
                val bitmapUri = convertBitmapToUri(requireActivity(), bitmap!!)

                Glide.with(this).load(bitmap).into(binding.ivPickPhoto)
                binding.clSmallCameraPicker.visibility = View.VISIBLE
                currentPhotoLocation = bitmapUri
            } else {
                Timber.d("Gallery error")
            }
        }

    private val takePhotoContract =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                val bitmap =
                    Tools.handleSamplingAndRotationBitmap(requireActivity(), currentPhotoLocation)
                val bitmapUri = convertBitmapToUri(requireActivity(), bitmap!!)

                Glide.with(this).load(bitmap).into(binding.ivPickPhoto)
                binding.clSmallCameraPicker.visibility = View.VISIBLE
                currentPhotoLocation = bitmapUri
            } else {
                Timber.d("Photo error")
            }
        }

    private var bindingSetup: FragmentAccountCreationBinding? = null

    private val binding get() = bindingSetup!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        bindingSetup = FragmentAccountCreationBinding.inflate(inflater, container, false)

        addTextListeners()
        addClickListeners()
        addObservers()

        viewModel.sendContacts()
        return binding.root
    }

    private fun addObservers() {
        viewModel.accountCreationListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                OnboardingStates.CONTACTS_SENT -> Timber.d("Contacts sent successfully")
                OnboardingStates.CONTACTS_ERROR -> Timber.d("Failed to send contacts")
                else -> Timber.d("Other error")
            }
        })

        viewModel.userUpdateListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                OnboardingStates.USER_UPDATED -> {
                    startMainActivity(requireActivity())
                }
                OnboardingStates.USER_UPDATE_ERROR -> Timber.d("Error updating user")
                else -> Timber.d("Other error")
            }
        })
    }

    private fun addClickListeners() {
        binding.btnNext.setOnClickListener {
            activity?.currentFocus?.let { view ->
                val imm =
                    requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
            checkUsername()
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

    private fun addTextListeners() {
        binding.etEnterUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                // ignore
            }

            override fun onTextChanged(text: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (!text.isNullOrEmpty()) {
                    binding.btnNext.isEnabled = true
                    binding.clUsernameError.visibility = View.INVISIBLE
                }
            }

            override fun afterTextChanged(text: Editable?) {
                if (!text.isNullOrEmpty()) {
                    binding.btnNext.isEnabled = true
                    binding.clUsernameError.visibility = View.INVISIBLE
                }
            }
        })

        binding.etEnterUsername.setOnFocusChangeListener { view, hasFocus ->
            run {
                if (!hasFocus) {
                    Tools.hideKeyboard(requireActivity(), view)
                }
            }
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

    private fun checkUsername() {
        if (binding.etEnterUsername.text.isNullOrEmpty()) {
            binding.btnNext.isEnabled = false
            binding.clUsernameError.visibility = View.VISIBLE
        } else {
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
                        object : FileUploadListener {
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
                                requireActivity().runOnUiThread {
                                    binding.clProgressScreen.visibility = View.GONE
                                }

                                val userData = hashMapOf(
                                    Const.UserData.DISPLAY_NAME to binding.etEnterUsername.text.toString(),
                                    Const.UserData.AVATAR_URL to path
                                )
                                viewModel.updateUserData(userData)
                            }

                        })
                }
                binding.clProgressScreen.visibility = View.VISIBLE
            } else {
                viewModel.updateUserData(hashMapOf(Const.UserData.DISPLAY_NAME to binding.etEnterUsername.text.toString()))
            }
        }
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
}