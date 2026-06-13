package com.prplegryn.moegirl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prplegryn.moegirl.data.CloudFile

private enum class SheetMode {
    Login,
    Manage,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onHeaderClick: () -> Unit,
    onSendCode: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (CloudFile) -> Unit,
    onGoUp: () -> Unit,
    onSetRoot: () -> Unit,
    onResetRoot: () -> Unit,
    onVideoClick: (CloudFile) -> Unit,
    onClearLoginMessage: () -> Unit,
) {
    var sheetMode by rememberSaveable { mutableStateOf<SheetMode?>(null) }
    val canGoUp = state.path.size > 1
    BackHandler(enabled = canGoUp && sheetMode == null) {
        onGoUp()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val pagePadding = when {
            maxWidth < 380.dp -> 24.dp
            maxWidth < 700.dp -> 32.dp
            else -> 48.dp
        }
        Surface(
            modifier = Modifier
                .padding(pagePadding)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                IdentityHeader(
                    state = state,
                    onClick = {
                        onHeaderClick()
                        sheetMode = if (state.session.isLoggedIn) SheetMode.Manage else SheetMode.Login
                    },
                )
                Spacer(modifier = Modifier.height(18.dp))
                PathBar(
                    pathText = state.path.joinToString(" / ") { it.name },
                    canGoUp = canGoUp,
                    isRefreshing = state.isRefreshing,
                    onGoUp = onGoUp,
                    onRefresh = onRefresh,
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                FileContent(
                    state = state,
                    onOpenFolder = onOpenFolder,
                    onVideoClick = onVideoClick,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (sheetMode != null) {
        ModalBottomSheet(
            onDismissRequest = {
                sheetMode = null
                onClearLoginMessage()
            },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            tonalElevation = 4.dp,
        ) {
            when (sheetMode) {
                SheetMode.Login -> LoginSheet(
                    state = state.login,
                    savedPhone = state.session.phone,
                    isLoggedIn = state.session.isLoggedIn,
                    onSendCode = onSendCode,
                    onVerifyCode = onVerifyCode,
                    onDismiss = {
                        sheetMode = null
                        onClearLoginMessage()
                    },
                )
                SheetMode.Manage -> ManageSheet(
                    state = state,
                    onRefresh = {
                        onRefresh()
                        sheetMode = null
                    },
                    onSetRoot = {
                        onSetRoot()
                        sheetMode = null
                    },
                    onResetRoot = {
                        onResetRoot()
                        sheetMode = null
                    },
                    onLogout = {
                        onLogout()
                        sheetMode = null
                    },
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun IdentityHeader(
    state: HomeUiState,
    onClick: () -> Unit,
) {
    val name = state.session.userName
        ?: state.session.phone
        ?: "未登录"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
            .defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = name, avatarUrl = state.session.avatarUrl)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (state.session.isLoggedIn) "文件管理" else "点击登录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = if (state.session.isLoggedIn) Icons.Rounded.Settings else Icons.Rounded.Login,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Avatar(name: String, avatarUrl: String?) {
    val gradient = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "用户头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PathBar(
    pathText: String,
    canGoUp: Boolean,
    isRefreshing: Boolean,
    onGoUp: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onGoUp,
            enabled = canGoUp,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = "上一级",
            )
        }
        Text(
            text = pathText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.size(44.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新文件",
                )
            }
        }
    }
}

@Composable
private fun FileContent(
    state: HomeUiState,
    onOpenFolder: (CloudFile) -> Unit,
    onVideoClick: (CloudFile) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        val target = when {
            !state.session.isLoggedIn -> "login"
            state.isLoading -> "loading"
            state.error != null -> "error"
            state.files.isEmpty() -> "empty"
            else -> "content"
        }
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            label = "file-content",
            modifier = Modifier.fillMaxSize(),
        ) { contentState ->
            when (contentState) {
                "login" -> CenterState(
                    title = "登录后查看文件",
                    body = "头像区域可以打开登录弹窗。",
                    action = null,
                )
                "loading" -> LoadingRows()
                "error" -> CenterState(
                    title = "加载失败",
                    body = state.error.orEmpty(),
                    action = "重试" to onRefresh,
                )
                "empty" -> CenterState(
                    title = "这个目录是空的",
                    body = "可以返回上级或刷新当前目录。",
                    action = "刷新" to onRefresh,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.files, key = { it.id }) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                when {
                                    file.isDirectory -> onOpenFolder(file)
                                    file.isVideo -> onVideoClick(file)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: CloudFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = file.isDirectory || file.isVideo
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                },
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(file = file)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(file.subtitle, file.updatedAt).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileIcon(file: CloudFile) {
    val icon: ImageVector = when {
        file.isDirectory -> Icons.Rounded.Folder
        file.isVideo -> Icons.Rounded.Movie
        else -> Icons.Rounded.InsertDriveFile
    }
    val tint = when {
        file.isDirectory -> MaterialTheme.colorScheme.secondary
        file.isVideo -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LoadingRows() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                    )
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterState(
    title: String,
    body: String,
    action: Pair<String, () -> Unit>?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(18.dp))
            FilledTonalButton(
                onClick = action.second,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(action.first)
            }
        }
    }
}

@Composable
private fun LoginSheet(
    state: LoginUiState,
    savedPhone: String?,
    isLoggedIn: Boolean,
    onSendCode: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf(savedPhone ?: "+86 ") }
    var code by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "登录",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(8.dp),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("短信验证码") },
            singleLine = true,
            enabled = state.stage == LoginStage.CodeSent || state.stage == LoginStage.SigningIn,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(8.dp),
        )
        if (!state.message.isNullOrBlank()) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.message.contains("失败") || state.message.contains("需要")) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
        }
        if (!state.challengeUrl.isNullOrBlank()) {
            TextButton(
                onClick = { uriHandler.openUri(state.challengeUrl) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("打开验证链接")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                onClick = { onSendCode(phone) },
                enabled = state.stage != LoginStage.Sending && state.stage != LoginStage.SigningIn,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (state.stage == LoginStage.Sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.stage == LoginStage.CodeSent) "重新发送" else "发送验证码")
                }
            }
            Button(
                onClick = { onVerifyCode(code) },
                enabled = state.stage == LoginStage.CodeSent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (state.stage == LoginStage.SigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("登录")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ManageSheet(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onSetRoot: () -> Unit,
    onResetRoot: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        val name = state.session.userName ?: state.session.phone ?: "已登录用户"
        ListItem(
            headlineContent = {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = "当前目录：${state.path.lastOrNull()?.name ?: "根目录"}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Avatar(name = name, avatarUrl = state.session.avatarUrl)
            },
        )
        SheetAction(Icons.Rounded.Refresh, "刷新文件", onRefresh)
        SheetAction(Icons.Rounded.FolderOpen, "设置当前目录为根目录", onSetRoot)
        SheetAction(Icons.Rounded.Home, "恢复系统根目录", onResetRoot)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SheetAction(
            icon = Icons.Rounded.ExitToApp,
            text = "退出登录",
            onClick = onLogout,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}
