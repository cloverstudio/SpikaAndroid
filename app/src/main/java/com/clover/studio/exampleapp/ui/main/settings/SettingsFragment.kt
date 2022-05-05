package com.clover.studio.exampleapp.ui.main.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.databinding.FragmentSettingsBinding
import com.clover.studio.exampleapp.ui.main.MainViewModel
import com.clover.studio.exampleapp.utils.Tools.getAvatarUrl
import com.clover.studio.exampleapp.utils.extendables.BaseFragment

class SettingsFragment : BaseFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private var bindingSetup: FragmentSettingsBinding? = null

    private val binding get() = bindingSetup!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingSetup = FragmentSettingsBinding.inflate(inflater, container, false)

        setupClickListeners()
        initializeObservers()

        return binding.root
    }

    private fun initializeObservers() {
        viewModel.getLocalUser().observe(viewLifecycleOwner) {
            binding.tvUsername.text = it.displayName ?: getString(R.string.no_username)
            binding.tvPhoneNumber.text = it.telephoneNumber

            Glide.with(requireActivity()).load(it.avatarUrl?.let { url -> getAvatarUrl(url) })
                .into(binding.ivPickPhoto)
        }
    }

    private fun setupClickListeners() {
        binding.clPrivacy.setOnClickListener {
            goToPrivacySettings()
        }

        binding.clChat.setOnClickListener {
            goToChatSettings()
        }

        binding.clNotifications.setOnClickListener {
            goToNotificationSettings()
        }

        binding.clMediaDownload.setOnClickListener {
            goToDownloadSettings()
        }
    }

    private fun goToPrivacySettings() {
        findNavController().navigate(R.id.action_mainFragment_to_privacySettingsFragment22)
    }

    private fun goToChatSettings() {
        findNavController().navigate(R.id.action_mainFragment_to_chatSettingsFragment22)
    }

    private fun goToNotificationSettings() {
        findNavController().navigate(R.id.action_mainFragment_to_notificationSettingsFragment22)
    }

    private fun goToDownloadSettings() {
        findNavController().navigate(R.id.action_mainFragment_to_downloadSettingsFragment2)
    }
}