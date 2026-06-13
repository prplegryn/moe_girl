package com.prplegryn.moegirl.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prplegryn.moegirl.data.CloudFile
import com.prplegryn.moegirl.data.GuangyaApi
import com.prplegryn.moegirl.data.PathNode
import com.prplegryn.moegirl.data.Session
import com.prplegryn.moegirl.data.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val session: Session = Session(),
    val files: List<CloudFile> = emptyList(),
    val path: List<PathNode> = listOf(PathNode(null, "根目录")),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val login: LoginUiState = LoginUiState(),
    val selectedVideo: CloudFile? = null,
    val player: PlayerUiState = PlayerUiState(),
)

data class LoginUiState(
    val stage: LoginStage = LoginStage.Idle,
    val message: String? = null,
    val challengeUrl: String? = null,
)

enum class LoginStage {
    Idle,
    Sending,
    CodeSent,
    SigningIn,
}

data class PlayerUiState(
    val isLoading: Boolean = false,
    val downloadUrl: String? = null,
    val savedPositionMs: Long = 0L,
    val error: String? = null,
)

private data class PendingLogin(
    val phone: String,
    val deviceId: String,
    val captchaToken: String,
    val verificationId: String,
)

class MoeGirlViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private val api = GuangyaApi()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var pendingLogin: PendingLogin? = null
    private var loadedToken: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val deviceId = store.getOrCreateDeviceId()
            store.sessionFlow.collect { stored ->
                val session = if (stored.deviceId.isBlank()) stored.copy(deviceId = deviceId) else stored
                _uiState.update { state ->
                    val root = PathNode(session.rootId, session.rootName)
                    val path = if (state.path.isEmpty() || !state.session.isLoggedIn) listOf(root) else state.path
                    state.copy(session = session, path = path)
                }
                if (!session.isLoggedIn) {
                    loadedToken = null
                    _uiState.update {
                        it.copy(
                            files = emptyList(),
                            path = listOf(PathNode(session.rootId, session.rootName)),
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            selectedVideo = null,
                            player = PlayerUiState(),
                        )
                    }
                } else if (loadedToken != session.accessToken) {
                    loadedToken = session.accessToken
                    bootstrapSession()
                }
            }
        }
    }

    fun sendLoginCode(phoneInput: String) {
        val phone = phoneInput.trim()
        if (phone.isBlank()) {
            _uiState.update {
                it.copy(login = it.login.copy(message = "请输入手机号，包含国家区号。"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(login = LoginUiState(stage = LoginStage.Sending), error = null)
            }
            runCatching {
                val deviceId = store.getOrCreateDeviceId()
                val challenge = api.initSms(phone, deviceId)
                val captchaToken = challenge.captchaToken
                if (captchaToken.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            login = LoginUiState(
                                stage = LoginStage.Idle,
                                message = "需要先完成人机验证，再重新发送验证码。",
                                challengeUrl = challenge.challengeUrl,
                            ),
                        )
                    }
                    return@launch
                }
                val sent = api.sendSms(phone, deviceId, captchaToken)
                pendingLogin = PendingLogin(
                    phone = phone,
                    deviceId = deviceId,
                    captchaToken = captchaToken,
                    verificationId = sent.verificationId,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        login = LoginUiState(
                            stage = LoginStage.CodeSent,
                            message = "验证码已发送。",
                        ),
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        login = LoginUiState(
                            stage = LoginStage.Idle,
                            message = throwable.cleanMessage("验证码发送失败"),
                        ),
                    )
                }
            }
        }
    }

    fun verifyLoginCode(codeInput: String) {
        val code = codeInput.trim()
        val pending = pendingLogin
        if (pending == null) {
            _uiState.update {
                it.copy(login = it.login.copy(message = "请先发送验证码。"))
            }
            return
        }
        if (code.isBlank()) {
            _uiState.update {
                it.copy(login = it.login.copy(message = "请输入短信验证码。"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(login = it.login.copy(stage = LoginStage.SigningIn, message = null), error = null)
            }
            runCatching {
                val verified = api.verifySms(pending.deviceId, pending.verificationId, code)
                val tokens = api.signIn(
                    phone = pending.phone,
                    deviceId = pending.deviceId,
                    verificationCode = code,
                    verificationToken = verified.verificationToken,
                    captchaToken = pending.captchaToken,
                )
                store.saveTokens(tokens, phone = pending.phone, deviceId = pending.deviceId)
                val profile = api.userInfo(tokens.accessToken, pending.deviceId)
                store.saveUser(profile)
                pendingLogin = null
            }.onSuccess {
                _uiState.update {
                    it.copy(login = LoginUiState(stage = LoginStage.Idle, message = "登录成功。"))
                }
                bootstrapSession()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        login = LoginUiState(
                            stage = LoginStage.CodeSent,
                            message = throwable.cleanMessage("登录失败"),
                        ),
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            pendingLogin = null
            loadedToken = null
            store.clearLogin()
        }
    }

    fun refreshFiles() {
        loadFolder(_uiState.value.path.lastOrNull()?.id, refreshing = true)
    }

    fun openFolder(file: CloudFile) {
        if (!file.isDirectory) return
        val nextPath = _uiState.value.path + PathNode(file.id, file.name)
        _uiState.update { it.copy(path = nextPath) }
        loadFolder(file.id)
    }

    fun goUp() {
        val currentPath = _uiState.value.path
        if (currentPath.size <= 1) return
        val nextPath = currentPath.dropLast(1)
        _uiState.update { it.copy(path = nextPath) }
        loadFolder(nextPath.lastOrNull()?.id)
    }

    fun setCurrentAsRoot() {
        viewModelScope.launch {
            val current = _uiState.value.path.lastOrNull() ?: PathNode(null, "根目录")
            store.setRoot(current.id, current.name)
            _uiState.update { it.copy(path = listOf(current), error = null) }
            loadFolder(current.id, refreshing = true)
        }
    }

    fun resetRoot() {
        viewModelScope.launch {
            store.setRoot(null, "根目录")
            _uiState.update { it.copy(path = listOf(PathNode(null, "根目录")), error = null) }
            loadFolder(null, refreshing = true)
        }
    }

    fun selectVideo(file: CloudFile) {
        if (!file.isVideo) return
        _uiState.update {
            it.copy(selectedVideo = file, player = PlayerUiState(isLoading = true))
        }
    }

    fun loadPlayer(file: CloudFile, force: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            if (!force && current.selectedVideo?.id == file.id && !current.player.downloadUrl.isNullOrBlank()) {
                return@launch
            }
            _uiState.update { it.copy(player = PlayerUiState(isLoading = true), selectedVideo = file) }
            runCatching {
                val session = activeSession()
                val saved = store.playbackPosition(file.id)
                val url = authenticated(session) { fresh ->
                    api.downloadUrl(fresh.accessToken, fresh.deviceId, file.id)
                }
                saved to url
            }.onSuccess { (saved, url) ->
                _uiState.update {
                    it.copy(player = PlayerUiState(downloadUrl = url, savedPositionMs = saved))
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(player = PlayerUiState(error = throwable.cleanMessage("无法获取播放地址")))
                }
            }
        }
    }

    fun savePlayback(fileId: String, positionMs: Long) {
        viewModelScope.launch {
            _uiState.update { state ->
                if (state.selectedVideo?.id == fileId) {
                    state.copy(player = state.player.copy(savedPositionMs = positionMs))
                } else {
                    state
                }
            }
            store.savePlaybackPosition(fileId, positionMs)
        }
    }

    fun clearPlayer() {
        _uiState.update { it.copy(player = PlayerUiState()) }
    }

    fun clearLoginMessage() {
        _uiState.update { it.copy(login = it.login.copy(message = null, challengeUrl = null)) }
    }

    private fun bootstrapSession() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val session = activeSession()
            if (!session.isLoggedIn) return@launch
            runCatching {
                val profile = authenticated(session) { fresh ->
                    api.userInfo(fresh.accessToken, fresh.deviceId)
                }
                store.saveUser(profile)
            }
            val root = PathNode(session.rootId, session.rootName)
            _uiState.update { it.copy(path = listOf(root)) }
            loadFolder(root.id, cancelCurrent = false)
        }
    }

    private fun loadFolder(parentId: String?, refreshing: Boolean = false, cancelCurrent: Boolean = true) {
        if (cancelCurrent) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing,
                    isRefreshing = refreshing,
                    error = null,
                )
            }
            runCatching {
                val session = activeSession()
                authenticated(session) { fresh ->
                    api.listFiles(
                        accessToken = fresh.accessToken,
                        deviceId = fresh.deviceId,
                        parentId = parentId,
                    )
                }
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        files = page.files,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = throwable.cleanMessage("文件加载失败"),
                    )
                }
            }
        }
    }

    private suspend fun activeSession(forceRefresh: Boolean = false): Session {
        var session = store.getSession()
        val deviceId = if (session.deviceId.isBlank()) store.getOrCreateDeviceId() else session.deviceId
        session = session.copy(deviceId = deviceId)
        val expiresAt = session.expiresAtMillis
        val shouldRefresh = session.isLoggedIn &&
            !session.refreshToken.isNullOrBlank() &&
            (forceRefresh || (expiresAt != null && System.currentTimeMillis() >= expiresAt - 60_000L))
        if (!shouldRefresh) return session

        val tokens = api.refresh(session.refreshToken.orEmpty(), deviceId)
        store.saveTokens(tokens, phone = session.phone, deviceId = deviceId)
        return session.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: session.refreshToken,
            expiresAtMillis = tokens.expiresAtMillis,
        )
    }

    private suspend fun <T> authenticated(session: Session, block: suspend (Session) -> T): T {
        return runCatching { block(session) }.getOrElse { throwable ->
            if (throwable.message?.contains("401") == true && !session.refreshToken.isNullOrBlank()) {
                block(activeSession(forceRefresh = true))
            } else {
                throw throwable
            }
        }
    }

    private fun Throwable.cleanMessage(prefix: String): String {
        val detail = message
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(160)
            ?.takeIf { it.isNotBlank() }
        return if (detail == null) prefix else "$prefix：$detail"
    }
}
