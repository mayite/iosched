/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.ui.schedule

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.OnHierarchyChangeListener
import com.google.android.material.widget.Snackbar
import com.google.android.material.widget.Snackbar.LENGTH_LONG
import com.google.android.material.widget.Snackbar.LENGTH_SHORT
import com.google.android.material.widget.TabLayout
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider
import com.google.samples.apps.iosched.shared.util.checkAllMatched
import com.google.samples.apps.iosched.ui.SnackbarMessage
import com.google.samples.apps.iosched.ui.dialog.SignInDialogFragment
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogin
import com.google.samples.apps.iosched.ui.login.LoginEvent.RequestLogout
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity
import com.google.samples.apps.iosched.util.login.LoginHandler
import dagger.android.support.DaggerFragment
import timber.log.Timber
import javax.inject.Inject

/**
 * The Schedule page of the top-level Activity.
 */
class ScheduleFragment : DaggerFragment() {

    companion object {
        private val COUNT = ConferenceDay.values().size + 1 // Agenda
        private val AGENDA_POSITION = COUNT - 1
        private const val DIALOG_NEED_TO_SIGN_IN = "dialog_need_to_sign_in"
    }

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var loginHandler: LoginHandler

    private lateinit var viewModel: ScheduleViewModel
    private lateinit var coordinatorLayout: CoordinatorLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = activityViewModelProvider(viewModelFactory)
        val binding = FragmentScheduleBinding.inflate(inflater, container, false)
        coordinatorLayout = binding.coordinatorLayout

        // Set the layout variables
        binding.viewModel = viewModel
        binding.setLifecycleOwner(this)

        viewModel.navigateToSessionAction.observe(this, Observer { navigationEvent ->
            navigationEvent?.getContentIfNotHandled()?.let { sessionId ->
                openSessionDetail(sessionId)
            }
        })

        viewModel.performLoginEvent.observe(this, Observer { loginRequestEvent ->
            loginRequestEvent?.getContentIfNotHandled()?.let { loginEvent ->
                when (loginEvent) {
                    RequestLogout -> doLogout()
                    RequestLogin -> requestLogin()
                }.checkAllMatched
            }
        })

        viewModel.navigateToSignInDialogAction.observe(this, Observer {
            it?.getContentIfNotHandled()?.let {
                openSignInDialog(requireActivity())
            }
        })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewpager: ViewPager = view.findViewById(R.id.viewpager)
        viewpager.offscreenPageLimit = COUNT - 1
        viewpager.adapter = ScheduleAdapter(childFragmentManager)

        val tabs: TabLayout = view.findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewpager)

        // Ensure snackbars appear above the hiding BottomNavigationView.
        // We clear the Snackbar's insetEdge, which is also set in it's (final) showView() method,
        // so we have to do it later (e.g. when it's added to the hierarchy).
        coordinatorLayout.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                if (child is Snackbar.SnackbarLayout) {
                    child.layoutParams = (child.layoutParams as CoordinatorLayout.LayoutParams)
                        .apply {
                            insetEdge = Gravity.NO_GRAVITY
                            dodgeInsetEdges = Gravity.BOTTOM
                        }
                }
            }

            override fun onChildViewRemoved(parent: View, child: View) {}
        })

        // Show snackbar messages generated by the ViewModel
        viewModel.snackBarMessage.observe(this, Observer {
            it?.getContentIfNotHandled()?.let { message: SnackbarMessage ->
                val duration = if (message.longDuration) LENGTH_LONG else LENGTH_SHORT
                Snackbar.make(coordinatorLayout, message.messageId, duration).apply {
                    message.actionId?.let { action ->
                        setAction(action, { this.dismiss() })
                    }
                    setActionTextColor(ContextCompat.getColor(context, R.color.teal))
                    show()
                }
            }
        })
    }

    private fun openSessionDetail(id: String) {
        startActivity(SessionDetailActivity.starterIntent(requireContext(), id))
    }

    private fun openSignInDialog(activity: FragmentActivity) {
        val dialog = SignInDialogFragment()
        dialog.show(activity.supportFragmentManager, DIALOG_NEED_TO_SIGN_IN)
    }

    private fun doLogout() {
        // TODO: b/74393872 Implement full UX here
        this.context?.let {
            loginHandler.logout(it) {
                Timber.d("Logged out!")
            }
        }
    }

    private fun requestLogin() {
        loginHandler.makeLoginIntent()?.let { startActivity(it) }
    }

    /**
     * Adapter that build a page for each conference day.
     */
    inner class ScheduleAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getCount() = COUNT

        override fun getItem(position: Int): Fragment {
            return when (position) {
                AGENDA_POSITION -> ScheduleAgendaFragment()
                else -> ScheduleDayFragment.newInstance(ConferenceDay.values()[position])
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                AGENDA_POSITION -> getString(R.string.agenda)
                else -> ConferenceDay.values()[position].formatMonthDay()
            }
        }
    }
}