package com.clover.studio.exampleapp.ui.main.create_room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clover.studio.exampleapp.R
import com.clover.studio.exampleapp.data.models.User
import com.clover.studio.exampleapp.data.models.UserAndPhoneUser
import com.clover.studio.exampleapp.databinding.FragmentNewRoomBinding
import com.clover.studio.exampleapp.ui.main.MainViewModel
import com.clover.studio.exampleapp.ui.main.RoomExists
import com.clover.studio.exampleapp.ui.main.RoomNotFound
import com.clover.studio.exampleapp.ui.main.chat.startChatScreenActivity
import com.clover.studio.exampleapp.ui.main.contacts.ContactsAdapter
import com.clover.studio.exampleapp.utils.Const
import com.clover.studio.exampleapp.utils.EventObserver
import com.clover.studio.exampleapp.utils.extendables.BaseFragment
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import timber.log.Timber

class NewRoomFragment : BaseFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var selectedContactsAdapter: SelectedContactsAdapter
    private lateinit var userList: List<UserAndPhoneUser>
    private var selectedUsers: MutableList<UserAndPhoneUser> = ArrayList()
    private var user: User? = null

    private var bindingSetup: FragmentNewRoomBinding? = null

    private val binding get() = bindingSetup!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bindingSetup = FragmentNewRoomBinding.inflate(inflater, container, false)

        initializeObservers()
        initializeViews()
        setupAdapter(false)

        return binding.root
    }

    private fun initializeViews() {
        // SearchView is immediately acting as if selected
        binding.svContactsSearch.setIconifiedByDefault(false)

        binding.tvNext.setOnClickListener {
            val bundle = bundleOf(Const.Navigation.SELECTED_USERS to selectedUsers)
            findNavController().navigate(
                R.id.action_newRoomFragment_to_groupInformationFragment,
                bundle
            )
        }

        binding.ivCancel.setOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.tvNewGroupChat.setOnClickListener {
            binding.clSelectedContacts.visibility = View.VISIBLE
            binding.tvSelectedNumber.text = getString(R.string.users_selected, selectedUsers.size)
            binding.tvNewGroupChat.visibility = View.GONE
            binding.tvNext.visibility = View.VISIBLE
            binding.tvTitle.text = getString(R.string.select_members)

            setupAdapter(true)
            initializeObservers()
        }
    }

    private fun setupAdapter(isGroupCreation: Boolean) {
        // Contacts Adapter
        contactsAdapter = ContactsAdapter(requireContext(), isGroupCreation) {
            if (binding.tvNewGroupChat.visibility == View.GONE) {
                if (selectedUsers.contains(it)) {
                    selectedUsers.remove(it)
                } else {
                    selectedUsers.add(it)
                    binding.tvSelectedNumber.text =
                        getString(R.string.users_selected, selectedUsers.size)
                }
                selectedContactsAdapter.submitList(selectedUsers)
                selectedContactsAdapter.notifyDataSetChanged()

                handleNextTextView()
                handleSelectedUserList(it)
            } else {
                user = it.user
                it.user.id.let { id -> viewModel.checkIfRoomExists(id) }
            }
        }

        binding.rvContacts.adapter = contactsAdapter
        binding.rvContacts.layoutManager =
            LinearLayoutManager(activity, RecyclerView.VERTICAL, false)

        // Contacts Selected Adapter
        selectedContactsAdapter = SelectedContactsAdapter(requireContext()) {
            if (selectedUsers.contains(it)) {
                selectedUsers.remove(it)
                selectedContactsAdapter.submitList(selectedUsers)
                binding.tvSelectedNumber.text =
                    getString(R.string.users_selected, selectedUsers.size)
                selectedContactsAdapter.notifyDataSetChanged()
            }

            handleNextTextView()
            handleSelectedUserList(it)
        }

        binding.rvSelected.adapter = selectedContactsAdapter
        binding.rvSelected.layoutManager =
            LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
    }

    private fun handleSelectedUserList(userItem: UserAndPhoneUser) {
        for (user in userList) {
            if (user == userItem) {
                user.user.selected = !user.user.selected
            }
        }

        contactsAdapter.submitList(userList)
        contactsAdapter.notifyDataSetChanged()
    }

    private fun handleNextTextView() {
        if (selectedUsers.size > 0) {
            binding.tvNext.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.primary_color
                )
            )
            binding.tvNext.isClickable = true
        } else binding.tvNext.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                R.color.text_tertiary
            )
        )
    }

    private fun initializeObservers() {
        viewModel.getUserAndPhoneUser().observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                userList = it
                val users = userList.toMutableList().sortedBy { user ->
                    user.phoneUser?.name?.lowercase() ?: user.user.displayName?.lowercase()
                }
                userList = users
                contactsAdapter.submitList(users)
            }
        }

        viewModel.checkRoomExistsListener.observe(viewLifecycleOwner, EventObserver {
            when (it) {
                is RoomExists -> {
                    Timber.d("Room already exists")
                    val gson = Gson()
                    val roomData = gson.toJson(it.roomData)
                    activity?.let { parent -> startChatScreenActivity(parent, roomData) }
                }
                is RoomNotFound -> {
                    Timber.d("Room not found, creating new one")
                    val jsonObject = JsonObject()

                    val userIdsArray = JsonArray()
                    userIdsArray.add(user?.id)

                    jsonObject.addProperty(Const.JsonFields.NAME, user?.displayName)
                    jsonObject.addProperty(Const.JsonFields.AVATAR_URL, user?.avatarUrl)
                    jsonObject.add(Const.JsonFields.USER_IDS, userIdsArray)
                    jsonObject.addProperty(Const.JsonFields.TYPE, Const.JsonFields.PRIVATE)

                    viewModel.createNewRoom(jsonObject)
                }
                else -> Timber.d("Other error")
            }
        })
    }
}