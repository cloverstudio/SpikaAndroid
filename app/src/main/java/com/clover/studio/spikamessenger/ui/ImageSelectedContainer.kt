package com.clover.studio.spikamessenger.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.clover.studio.spikamessenger.databinding.ItemImageSelectedBinding
import com.clover.studio.spikamessenger.utils.helpers.ChatAdapterHelper

class ImageSelectedContainer(context: Context, attrs: AttributeSet?) :
    ConstraintLayout(context, attrs) {
    private var removeImageSelected: RemoveImageSelected? = null
    private var bindingSetup: ItemImageSelectedBinding = ItemImageSelectedBinding.inflate(
        LayoutInflater.from(context), this, true
    )
    private val binding get() = bindingSetup

    init {
        handleButtonClicks()
    }

    interface RemoveImageSelected {
        fun removeImage()
    }

    fun setButtonListener(removeImageSelected: RemoveImageSelected?) {
        this.removeImageSelected = removeImageSelected
    }

    fun setImage(bitmap: Bitmap) {
        binding.ivUserImage.let { Glide.with(this).load(bitmap).centerCrop().into(it) }
    }

    fun setFile(extension: String, name: String) {
        binding.clFileDetails.visibility = View.VISIBLE
        binding.ivUserImage.visibility = View.GONE
        binding.tvFileName.text = name

        ChatAdapterHelper.addFiles(context, binding.ivFile, extension)
    }

    private fun handleButtonClicks() {
        binding.ivRemoveImage.setOnClickListener { removeImageSelected!!.removeImage() }
    }
}