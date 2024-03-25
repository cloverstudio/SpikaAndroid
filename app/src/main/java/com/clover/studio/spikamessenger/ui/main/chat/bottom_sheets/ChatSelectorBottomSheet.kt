package com.clover.studio.spikamessenger.ui.main.chat.bottom_sheets

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clover.studio.spikamessenger.R
import com.clover.studio.spikamessenger.data.models.entity.PrivateGroupChats
import com.clover.studio.spikamessenger.databinding.BottomSheetForwardBinding
import com.clover.studio.spikamessenger.ui.main.MainViewModel
import com.clover.studio.spikamessenger.ui.main.contacts.UsersGroupsAdapter
import com.clover.studio.spikamessenger.ui.main.create_room.UsersGroupsSelectedAdapter
import com.clover.studio.spikamessenger.utils.EventObserver
import com.clover.studio.spikamessenger.utils.Tools
import com.clover.studio.spikamessenger.utils.helpers.ColorHelper
import com.clover.studio.spikamessenger.utils.helpers.Resource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import timber.log.Timber

class ChatSelectorBottomSheet(
    private val context: Context,
    private val localId: Int,
    private val title: String,
) :
    BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetForwardBinding
    private var listener: BottomSheetForwardAction? = null
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var usersGroupsAdapter: UsersGroupsAdapter
    private lateinit var selectedAdapter: UsersGroupsSelectedAdapter

    private var groupList: List<PrivateGroupChats> = mutableListOf()
    private var userList: List<PrivateGroupChats> = mutableListOf()
    private var selectedChats: MutableList<PrivateGroupChats> = mutableListOf()

    private var searchedQuery: String = ""
    private var isContactButtonActive: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetForwardBinding.inflate(layoutInflater)

        viewModel.getUserAndPhoneUser(localId)
        viewModel.getAllGroups()

        setUpAdapter()
        setUpSelectedAdapter()
        initializeObservers()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews()

        val bottomSheetBehavior = BottomSheetBehavior.from((view.parent as View))

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val peekHeight = (screenHeight)
        bottomSheetBehavior.peekHeight = peekHeight

        setUpSearch()
    }

    companion object {
        const val TAG = "forwardSheet"
    }

    interface BottomSheetForwardAction {
        fun forwardShare(userIds: ArrayList<Int>, roomIds: ArrayList<Int>)
    }

    fun setForwardShareListener(listener: BottomSheetForwardAction) {
        this.listener = listener
    }

    private fun setUpAdapter() = with(binding) {
        usersGroupsAdapter = UsersGroupsAdapter(
            context = requireContext(),
            isGroupCreation = false,
            userIdsInRoom = null,
            isForward = true
        )
        {
            if (!selectedChats.any { item ->
                    (isContactButtonActive && item.userId == it.userId) ||
                            (!isContactButtonActive && item.roomId == it.roomId)
                }) {
                selectedChats.add(it)
                selectedAdapter.submitList(selectedChats.toMutableList())

                refreshAdapter(it, true)

                rvSelected.visibility = View.VISIBLE
                fabForward.visibility = View.VISIBLE
            } else {
                selectedChats.remove(it)
                selectedAdapter.submitList(selectedChats.toMutableList())

                refreshAdapter(it, false)
            }
        }

        rvContacts.apply {
            itemAnimator = null
            adapter = usersGroupsAdapter
            layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        }
    }

    private fun setUpSelectedAdapter() = with(binding) {
        selectedAdapter = UsersGroupsSelectedAdapter(
            context
        ) {
            selectedChats.remove(it)
            selectedAdapter.submitList(selectedChats.toMutableList())

            refreshAdapter(it, false)

            if (selectedChats.isEmpty()) {
                fabForward.visibility = View.GONE
            }
        }

        rvSelected.apply {
            itemAnimator = null
            adapter = selectedAdapter
            layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
        }
    }

    private fun refreshAdapter(list: PrivateGroupChats, isSelected: Boolean) {
        val activeList = if (isContactButtonActive) userList else groupList
        val activeId = if (isContactButtonActive) list.userId else list.roomId

        activeList.forEachIndexed { index, item ->
            if ((isContactButtonActive && item.userId == activeId) || (!isContactButtonActive && item.roomId == activeId)) {
                item.selected = isSelected
                usersGroupsAdapter.notifyItemChanged(index)
            }
        }
    }

    private fun initializeObservers() = with(binding) {
        viewModel.contactListener.observe(viewLifecycleOwner, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    if (it.responseData != null) {
                        pbForward.visibility = View.GONE
                        llForward.visibility = View.VISIBLE

                        userList = Tools.transformPrivateList(context, it.responseData)

                        viewModel.getRecentContacts()
                    }
                }

                else -> Timber.d("Other error")
            }
        })

        viewModel.recentContacts.observe(viewLifecycleOwner, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    if (!it.responseData.isNullOrEmpty()) {
                        val list = Tools.transformRecentContacts(localId, context, it.responseData)
                        list.map { it1 -> it1.isRecent = true }
                        userList = list + userList
                        usersGroupsAdapter.submitList(userList)
                    }
                }

                else -> usersGroupsAdapter.submitList(userList)
            }
        })

        viewModel.allGroupsListener.observe(viewLifecycleOwner, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    if (!it.responseData.isNullOrEmpty()) {
                        groupList = Tools.transformGroupList(context, it.responseData)
                        viewModel.getRecentGroups()
                    }
                }

                else -> Timber.d("Other error")
            }
        })

        viewModel.recentGroupsListener.observe(viewLifecycleOwner, EventObserver {
            when (it.status) {
                Resource.Status.SUCCESS -> {
                    if (it.responseData != null) {
                        val list = Tools.transformGroupList(context, it.responseData)
                        list.map { it1 -> it1.isRecent = true }
                        groupList = list + groupList
                    }
                }

                else -> Timber.d("Other error")
            }
        })
    }

    private fun initializeViews() = with(binding) {
        tvTitle.text = title

        btnContacts.setOnClickListener {
            btnContacts.backgroundTintList =
                ColorStateList.valueOf(ColorHelper.getPrimaryColor(context))
            btnGroups.backgroundTintList =
                ColorStateList.valueOf(ColorHelper.getFourthAdditionalColor(context))

            rvContacts.visibility = View.VISIBLE
            tvEmptyList.visibility = View.GONE
            tvNoResults.visibility = View.GONE

            usersGroupsAdapter.submitList(null)
            usersGroupsAdapter.submitList(userList)

            isContactButtonActive = true

            if (searchedQuery.isNotEmpty()) {
                svRoomsContacts.setQuery(searchedQuery, true)
                setUpSearch()
            }
        }

        btnGroups.setOnClickListener {
            btnGroups.backgroundTintList =
                ColorStateList.valueOf(ColorHelper.getPrimaryColor(context))
            btnContacts.backgroundTintList =
                ColorStateList.valueOf(ColorHelper.getFourthAdditionalColor(context))

            if (groupList.isEmpty()) {
                rvContacts.visibility = View.GONE
                tvNoResults.visibility = View.GONE
                tvEmptyList.apply {
                    text = context.getString(R.string.no_groups_yet)
                    visibility = View.VISIBLE
                }
            } else {
                rvContacts.visibility = View.VISIBLE
                tvEmptyList.visibility = View.GONE

                usersGroupsAdapter.submitList(null)
                usersGroupsAdapter.submitList(groupList)
            }

            isContactButtonActive = false

            if (searchedQuery.isNotEmpty()) {
                svRoomsContacts.setQuery(searchedQuery, true)
                setUpSearch()
            }
        }

        fabForward.setOnClickListener {
            val userIds = arrayListOf<Int>()
            val roomIds = arrayListOf<Int>()

            selectedChats.forEach {
                if (it.phoneNumber != null && it.roomId != null) {
                    // This is the contact from recent list
                    // For it we don't need to make new group, just send roomId
                    roomIds.add(it.roomId)
                } else if (it.phoneNumber != null) {
                    // This is contact selected from list
                    it.userId.let { userId -> userIds.add(userId) }
                } else {
                    // This is group
                    it.roomId?.let { roomId -> roomIds.add(roomId) }
                }
            }
            listener?.forwardShare(userIds, roomIds)
            dismiss()
        }

        ivCloseSheet.setOnClickListener {
            dismiss()
        }
    }

    private fun setUpSearch() = with(binding) {
        Tools.setUpSearchBar(
            context = context,
            searchView = svRoomsContacts,
            hint = getString(R.string.contact_groups_search)
        )

        // Searching users / groups
        svRoomsContacts.setOnQueryTextListener(object :
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                makeQuery(query)
                return true
            }

            override fun onQueryTextChange(query: String?): Boolean {
                makeQuery(query)
                return true
            }
        })

        // Setting click listener on whole search area
        svRoomsContacts.setOnClickListener {
            svRoomsContacts.onActionViewExpanded()
        }

        // Setting listener for clearing user input
        val closeButton: View =
            svRoomsContacts.findViewById(androidx.appcompat.R.id.search_close_btn)
        closeButton.setOnClickListener {
            svRoomsContacts.setQuery("", true)
            searchedQuery = ""
        }
    }

    private fun makeQuery(query: String?) = with(binding) {
        if (query != null && query != "") {
            searchedQuery = query
            val filteredList = if (isContactButtonActive) {
                userList.filter {
                    it.userName?.contains(query, ignoreCase = true) == true
                }.toMutableList()
            } else {
                groupList.filter {
                    it.roomName?.contains(query, ignoreCase = true) == true
                }.toMutableList()
            }

            tvNoResults.visibility =
                if (filteredList.isEmpty() && tvEmptyList.visibility == View.GONE) View.VISIBLE else View.GONE
            rvContacts.visibility = if (filteredList.isEmpty()) View.GONE else View.VISIBLE

            usersGroupsAdapter.submitList(null)
            usersGroupsAdapter.submitList(ArrayList(filteredList))
        } else {
            searchedQuery = ""
            val list = if (isContactButtonActive) userList else groupList

            usersGroupsAdapter.submitList(null)
            usersGroupsAdapter.submitList(list)

            tvNoResults.visibility =
                if (tvEmptyList.visibility == View.GONE) View.VISIBLE else View.GONE
            rvContacts.visibility =
                if (tvEmptyList.visibility == View.GONE) View.VISIBLE else View.GONE
        }
    }
}
