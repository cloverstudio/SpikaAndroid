package com.clover.studio.exampleapp.ui.main.settings.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.clover.studio.exampleapp.databinding.FragmentDownloadSettingsBinding
import com.clover.studio.exampleapp.utils.extendables.BaseFragment

class DownloadSettingsFragment : BaseFragment() {
    private var bindingSetup: FragmentDownloadSettingsBinding? = null

    private val binding get() = bindingSetup!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingSetup = FragmentDownloadSettingsBinding.inflate(inflater, container, false)

        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        return binding.root
    }
}