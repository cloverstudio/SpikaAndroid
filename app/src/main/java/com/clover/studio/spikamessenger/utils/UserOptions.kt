package com.clover.studio.spikamessenger.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.databinding.ChatOptionItemBinding
import com.clover.studio.spikamessenger.databinding.MoreOptionsBinding
import com.clover.studio.spikamessenger.utils.helpers.UserOptionsData


class UserOptions(context: Context) :
    ConstraintLayout(context) {

    private var bindingSetup: MoreOptionsBinding = MoreOptionsBinding.inflate(
        LayoutInflater.from(context), this, true
    )
    private val binding get() = bindingSetup
    private var listener: OptionsListener? = null

    interface OptionsListener {
        fun clickedOption(option: Int, optionName: String)
        fun switchOption(optionName: String, isSwitched: Boolean)
    }

    fun setOptionsListener(listener: OptionsListener?) {
        this.listener = listener
    }

    fun setOptions(optionList: MutableList<UserOptionsData>) {
        optionList.forEachIndexed { index, item ->
            val isFirstView = index == 0
            val isLastView = index == optionList.size - 1

            val newView: View =
                LayoutInflater.from(context).inflate(R.layout.chat_option_item, null)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )

            val marginInPixels = resources.getDimensionPixelSize(R.dimen.eight_dp_margin)
            layoutParams.setMargins(0, 0, 0, marginInPixels)
            newView.layoutParams = layoutParams

            val newViewBinding = ChatOptionItemBinding.bind(newView)

            if (item.additionalText.isNotEmpty()) {
                binding.tvAdditionalText.text = item.additionalText
            }

            if (item.secondDrawable != null) {
                val imageView =
                    if (isFirstView) binding.ivFirstOption else if (isLastView) binding.ivLastOption else newViewBinding.ivOption
                imageView.setImageDrawable(item.secondDrawable)
                imageView.visibility = View.VISIBLE

                if (item.switchOption) {
                    var isSwitched = item.secondDrawable == AppCompatResources.getDrawable(
                        context,
                        R.drawable.img_switch_left
                    )

                    imageView.setOnClickListener {
                        isSwitched = !isSwitched

                        val drawable = if (!isSwitched) {
                            AppCompatResources.getDrawable(context, R.drawable.img_switch_left)
                        } else {
                            AppCompatResources.getDrawable(context, R.drawable.img_switch)
                        }

                        imageView.setImageDrawable(drawable)

                        listener?.switchOption(item.option, isSwitched)
                    }
                }
            }

            val textView =
                if (isFirstView) binding.tvFirstItem else if (isLastView) binding.tvLastItem else newViewBinding.tvOptionName
            textView.text = item.option

            if (item.firstDrawable != null) {
                textView.setCompoundDrawablesWithIntrinsicBounds(
                    item.firstDrawable,
                    null,
                    null,
                    null
                )
            }

            val frameLayout =
                if (isFirstView) binding.flFirstItem else if (isLastView) binding.flLastItem else newViewBinding.flNewItem
            frameLayout.tag = index
            frameLayout.setOnClickListener {
                listener?.clickedOption(it.tag as Int, item.option)
            }

            if (item.option == context.getString(R.string.delete)) {
                frameLayout.setBackgroundColor(resources.getColor(R.color.warningColor))
            }

            if (index > 0 && index < optionList.size - 1) {
                binding.llOptions.addView(newView, index)
            }
        }
    }
}
