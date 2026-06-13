package com.prplegryn.moegirl.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MoeGirlApp(
    viewModel: MoeGirlViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(260),
                initialOffsetX = { it / 3 },
            ) + fadeIn(tween(180))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(180),
                targetOffsetX = { -it / 4 },
            ) + fadeOut(tween(140))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(240),
                initialOffsetX = { -it / 4 },
            ) + fadeIn(tween(160))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(180),
                targetOffsetX = { it / 3 },
            ) + fadeOut(tween(140))
        },
    ) {
        composable("home") {
            HomeScreen(
                state = state,
                onHeaderClick = {},
                onSendCode = viewModel::sendLoginCode,
                onVerifyCode = viewModel::verifyLoginCode,
                onLogout = viewModel::logout,
                onRefresh = viewModel::refreshFiles,
                onOpenFolder = viewModel::openFolder,
                onGoUp = viewModel::goUp,
                onSetRoot = viewModel::setCurrentAsRoot,
                onResetRoot = viewModel::resetRoot,
                onVideoClick = { file ->
                    viewModel.selectVideo(file)
                    navController.navigate("player")
                },
                onClearLoginMessage = viewModel::clearLoginMessage,
            )
        }
        composable("player") {
            val file = state.selectedVideo
            if (file == null) {
                MissingPlayer(onBack = { navController.popBackStack() })
            } else {
                PlayerScreen(
                    file = file,
                    state = state.player,
                    onLoad = { viewModel.loadPlayer(file) },
                    onReload = { viewModel.loadPlayer(file, force = true) },
                    onSavePosition = { position -> viewModel.savePlayback(file.id, position) },
                    onBack = {
                        navController.popBackStack()
                        viewModel.clearPlayer()
                    },
                )
            }
        }
    }
}

@Composable
private fun MissingPlayer(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "没有可播放的视频",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("返回")
            }
        }
    }
}
