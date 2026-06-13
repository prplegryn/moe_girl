package com.prplegryn.moegirl.data

data class Session(
    val accessToken: String = "",
    val refreshToken: String? = null,
    val expiresAtMillis: Long? = null,
    val phone: String? = null,
    val deviceId: String = "",
    val userName: String? = null,
    val avatarUrl: String? = null,
    val rootId: String? = null,
    val rootName: String = "根目录",
) {
    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank()
}

data class UserProfile(
    val name: String,
    val avatarUrl: String?,
    val phone: String?,
)

data class CloudFile(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val isVideo: Boolean,
    val sizeBytes: Long?,
    val updatedAt: String?,
    val parentId: String?,
    val fileType: Int?,
) {
    val subtitle: String
        get() = when {
            isDirectory -> "文件夹"
            sizeBytes != null -> formatBytes(sizeBytes)
            isVideo -> "视频"
            else -> "文件"
        }

    companion object {
        private fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = listOf("KB", "MB", "GB", "TB")
            var value = bytes / 1024.0
            var unit = units.first()
            for (candidate in units) {
                unit = candidate
                if (value < 1024 || candidate == units.last()) break
                value /= 1024.0
            }
            return if (value >= 10) {
                "${value.toInt()} $unit"
            } else {
                "%.1f %s".format(value, unit)
            }
        }
    }
}

data class PathNode(
    val id: String?,
    val name: String,
)

data class SmsChallenge(
    val captchaToken: String?,
    val challengeUrl: String?,
)

data class SmsSendResult(
    val verificationId: String,
)

data class SmsVerifyResult(
    val verificationToken: String,
)

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long?,
)

data class FilePage(
    val files: List<CloudFile>,
    val hasMore: Boolean,
)

