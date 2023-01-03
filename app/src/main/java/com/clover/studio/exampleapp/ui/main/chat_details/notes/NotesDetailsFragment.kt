package com.clover.studio.exampleapp.ui.main.chat_details.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.clover.studio.exampleapp.databinding.FragmentNotesDetailsBinding
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import org.commonmark.node.Link

class NotesDetailsFragment : Fragment() {
    private var bindingSetup: FragmentNotesDetailsBinding? = null
    private val binding get() = bindingSetup!!
    private val args: NotesDetailsFragmentArgs by navArgs()
    private var notes: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notes = args.notesDetails
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingSetup = FragmentNotesDetailsBinding.inflate(inflater, container, false)

        initializeViews()

        // Inflate the layout for this fragment
        return binding.root
    }

    private fun initializeViews() {
        val markwon = Markwon.create(requireContext())

        markwon.setMarkdown(binding.tvNotesDetails, notes)
        Linkify.addLinks(binding.tvNotesDetails, Linkify.WEB_URLS)
    }
}