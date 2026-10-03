package com.readvoice.app.ui.setup

import com.readvoice.app.domain.AccountRepository
import com.readvoice.app.domain.AuthRepository
import com.readvoice.app.domain.ClientIdCheck
import com.readvoice.app.domain.ClientIdRules
import com.readvoice.app.domain.ClientIdSettings
import com.readvoice.app.domain.ClientIdVerifier
import com.readvoice.app.domain.ConversationCache
import com.readvoice.app.domain.LogoutUseCase
import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.AppResult
import com.readvoice.app.domain.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val validId = "AbC-123_xyzQRSTUVwxyz0"

    private val settings = object : ClientIdSettings {
        var stored: String? = null
        override fun current(): String? = stored
        override fun isSetByUser(): Boolean = stored != null
        override fun save(clientId: String) {
            stored = clientId
        }

        override fun clear() {
            stored = null
        }
    }
    private val verifier = object : ClientIdVerifier {
        var answer = ClientIdCheck.VALID
        val asked = mutableListOf<String>()
        override suspend fun verify(clientId: String): ClientIdCheck {
            asked += clientId
            return answer
        }
    }
    private val auth = object : AuthRepository {
        var loggedIn = false
        var logouts = 0
        override val isLoggedIn: Boolean get() = loggedIn
        override fun beginLogin(): AppResult<String> = AppResult.Failure(AppError.NOT_CONFIGURED)
        override suspend fun completeLogin(redirectUri: String): AppResult<Unit> =
            AppResult.Failure(AppError.AUTH_REJECTED)

        override suspend fun logout() {
            logouts++
            loggedIn = false
        }
    }
    private val account = object : AccountRepository {
        override suspend fun username(): AppResult<String> = AppResult.Success("me")
        override fun forget() = Unit
    }
    private val cache = object : ConversationCache {
        override fun replaceAll(conversations: List<Conversation>) = Unit
        override fun find(key: String): Conversation? = null
        override fun clear() = Unit
    }

    private fun viewModel() =
        SetupViewModel(settings, verifier, auth, LogoutUseCase(auth, account, cache))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun clientIdFormatRules() {
        assertTrue(ClientIdRules.isValid(validId))
        assertEquals(validId, ClientIdRules.normalize("  $validId\n"))
        assertFalse(ClientIdRules.isValid(""))
        assertFalse(ClientIdRules.isValid("short"))
        assertFalse(ClientIdRules.isValid("has a space in the id"))
        assertFalse(ClientIdRules.isValid("https://www.reddit.com/prefs/apps"))
    }

    @Test
    fun pastedTextIsTrimmedAndValidated() {
        val vm = viewModel()

        vm.onInputChanged("  $validId \n")

        assertEquals(validId, vm.state.value.input)
        assertTrue(vm.state.value.isFormatValid)
    }

    @Test
    fun badFormatCanBeNeitherCheckedNorSaved() {
        val vm = viewModel()
        vm.onInputChanged("not valid!")

        vm.check()
        vm.save()

        assertTrue(verifier.asked.isEmpty())
        assertNull(settings.stored)
        assertFalse(vm.state.value.isDone)
    }

    @Test
    fun checkReportsRedditsAnswer() {
        val vm = viewModel()
        vm.onInputChanged(validId)
        verifier.answer = ClientIdCheck.INVALID

        vm.check()

        assertEquals(listOf(validId), verifier.asked)
        assertEquals(CheckStatus.INVALID, vm.state.value.check)
    }

    @Test
    fun editingTheTextClearsAnEarlierCheckResult() {
        val vm = viewModel()
        vm.onInputChanged(validId)
        vm.check()

        vm.onInputChanged(validId + "9")

        assertEquals(CheckStatus.NONE, vm.state.value.check)
    }

    @Test
    fun firstSaveStoresTheIdWithoutLoggingOut() {
        val vm = viewModel()
        vm.onInputChanged(validId)

        vm.save()

        assertEquals(validId, settings.stored)
        assertEquals(0, auth.logouts)
        assertTrue(vm.state.value.isDone)
    }

    @Test
    fun changingTheIdLogsOutFirst() {
        settings.stored = "OldClientId_0123456789"
        auth.loggedIn = true
        val vm = viewModel()
        vm.onInputChanged(validId)

        vm.save()

        assertEquals(1, auth.logouts)
        assertEquals(validId, settings.stored)
    }

    @Test
    fun savingTheSameIdKeepsTheSession() {
        settings.stored = validId
        auth.loggedIn = true
        val vm = viewModel()

        vm.save()

        assertEquals(0, auth.logouts)
        assertTrue(vm.state.value.isDone)
    }

    @Test
    fun removeLogsOutAndClearsTheId() {
        settings.stored = validId
        auth.loggedIn = true
        val vm = viewModel()

        vm.remove()

        assertEquals(1, auth.logouts)
        assertNull(settings.stored)
        assertTrue(vm.state.value.isDone)
    }
}
