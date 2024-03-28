package com.clover.studio.spikamessenger.ui.main.chat.chat_layouts

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.clover.studio.spikamessenger.data.models.entity.Message
import com.clover.studio.spikamessenger.databinding.ImageLayoutBinding
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.helpers.ChatAdapterHelper
import com.clover.studio.spikamessenger.utils.helpers.Resource
import timber.log.Timber

class ImageLayout(context: Context) :
    ConstraintLayout(context) {

    private var bindingSetup: ImageLayoutBinding = ImageLayoutBinding.inflate(
        LayoutInflater.from(context), this, true
    )
    private val binding get() = bindingSetup
    private var listener: ImageLayoutListener? = null

    interface ImageLayoutListener {
        fun imageNavigation()
        fun imageResend()
        fun imageCancelUpload()
        fun imageOptions()
    }

    fun setImageLayoutListener(listener: ImageLayoutListener) {
        this.listener = listener
    }

    fun bindImage(chatMessage: Message) = with(binding) {
        val imageResized = Tools.resizeImage(
            chatMessage.body?.file?.metaData?.width,
            chatMessage.body?.file?.metaData?.height
        )

        val mediaPath = Tools.getMediaPath(context, chatMessage)

        ChatAdapterHelper.loadMedia(
            context = context,
            mediaPath = mediaPath,
            mediaImage = ivChatImage,
            loadingProgress = pbMediaLoading,
            height = imageResized.second,
            width = imageResized.first,
        )

        if (chatMessage.id < 0) {
            bindLoadingImage(chatMessage)
        } else {
            flLoadingScreen.visibility = View.GONE
            clImageChat.apply {
                setOnClickListener {
                    listener?.imageNavigation()
                }
                setOnLongClickListener {
                    listener?.imageOptions()
                    true
                }
            }

            clImageChat.setOnLongClickListener {
                listener?.imageOptions()
                true
            }
            clImageChat.setOnClickListener {
                listener?.imageNavigation()
            }
        }
    }

    private fun bindLoadingImage(
        chatMessage: Message,
    ) = with(binding) {
        when (chatMessage.messageStatus) {
            Resource.Status.LOADING.toString() -> {
                flLoadingScreen.visibility = View.VISIBLE
                ivImageFailed.visibility = View.GONE
                pbImages.apply {
                    visibility = View.VISIBLE
                    secondaryProgress = chatMessage.uploadProgress
                }
                ivCancelImage.apply {
                    visibility = View.VISIBLE
                    setOnClickListener {
                        listener?.imageCancelUpload()
                    }
                }
            }

            Resource.Status.ERROR.toString() -> {
                flLoadingScreen.visibility = View.VISIBLE
                pbImages.visibility = View.GONE
                ivCancelImage.visibility = View.GONE
                ivImageFailed.apply {
                    visibility = View.VISIBLE
                    setOnClickListener {
                        listener?.imageResend()
                    }
                }
            }

            else -> {
                Timber.d("Other error")
            }
        }
    }
}
