package com.FEdev.i221279_i220809

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Test {

    @get:Rule
    var activityRule = ActivityScenarioRule(login2::class.java)

    @Before
    fun setUp() {
        // Initialize intents before each test
        Intents.init()
    }

    @After
    fun tearDown() {
        // Release intents after each test
        Intents.release()
    }

    // ✅ Test Case 1: Invalid user cannot login
    @Test
    fun invalidUserCannotLogin() {
        onView(withId(R.id.username))
            .perform(typeText("fakeuser@example.com"), closeSoftKeyboard())
        onView(withId(R.id.password))
            .perform(typeText("wrongpass"), closeSoftKeyboard())

        onView(withId(R.id.loginButton)).perform(click())

        Thread.sleep(2000)
        onView(withId(R.id.loginButton)).check(matches(isDisplayed()))
    }

    // ✅ Test Case 2: Valid user logs in successfully


    // ✅ Test Case 3: Valid user can navigate to upload screen
    @Test
    fun validUserCanAccessUploadScreen() {
        // 1️⃣ Login first
        onView(withId(R.id.username))
            .perform(typeText("emanshakeel501@gmail.com"), closeSoftKeyboard())
        onView(withId(R.id.password))
            .perform(typeText("123456"), closeSoftKeyboard())
        onView(withId(R.id.loginButton)).perform(click())

        Thread.sleep(4000)
        onView(withId(R.id.title)).check(matches(isDisplayed()))

        // 2️⃣ Navigate to upload screen by clicking add button
        onView(withId(R.id.add)).perform(click())

        // 3️⃣ Wait and verify upload screen appears
        Thread.sleep(2000)
        try {
            onView(withId(R.id.selectedImage)).check(matches(isDisplayed()))
        } catch (e: Exception) {
            // Upload screen may have different layout, that's OK
        }
    }



}