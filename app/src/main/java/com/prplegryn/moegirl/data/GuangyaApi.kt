package com.prplegryn.moegirl.data

import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GuangyaApi(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun initSms(phone: String, deviceId: String, captchaToken: String? = null): SmsChallenge {
        val body = buildJsonObject {
            put("client_id", CLIENT_ID)
            put("action", "POST:/v1/auth/verification")
            put("device_id", deviceId)
            putJsonObject("meta") {
                put("phone_number", phone)
            }
            if (!captchaToken.isNullOrBlank()) put("captcha_token", captchaToken)
        }
        val result = post(
            url = "$ACCOUNT_BASE/v1/shield/captcha/init",
            headers = accountHeaders(deviceId),
            body = body,
        )
        return SmsChallenge(
            captchaToken = result.findString("captcha_token", "captchaToken") ?: captchaToken,
            challengeUrl = result.findString("url", "captcha_url", "captchaUrl"),
        )
    }

    suspend fun sendSms(phone: String, deviceId: String, captchaToken: String, target: String = "ANY"): SmsSendResult {
        val result = post(
            url = "$ACCOUNT_BASE/v1/auth/verification",
            headers = accountHeaders(deviceId) + ("x-captcha-token" to captchaToken),
            body = buildJsonObject {
                put("phone_number", phone)
                put("target", target)
                put("client_id", CLIENT_ID)
            },
        )
        val verificationId = result.findString("verification_id", "verificationId")
            ?: throw IOException("短信接口未返回 verification_id")
        return SmsSendResult(verificationId)
    }

    suspend fun verifySms(deviceId: String, verificationId: String, code: String): SmsVerifyResult {
        val result = post(
            url = "$ACCOUNT_BASE/v1/auth/verification/verify",
            headers = accountHeaders(deviceId),
            body = buildJsonObject {
                put("verification_id", verificationId)
                put("verification_code", code)
                put("client_id", CLIENT_ID)
            },
        )
        val verificationToken = result.findString("verification_token", "verificationToken")
            ?: throw IOException("验证码校验接口未返回 verification_token")
        return SmsVerifyResult(verificationToken)
    }

    suspend fun signIn(
        phone: String,
        deviceId: String,
        verificationCode: String,
        verificationToken: String,
        captchaToken: String,
    ): SessionTokens {
        val result = post(
            url = "$ACCOUNT_BASE/v1/auth/signin",
            headers = accountHeaders(deviceId) + ("x-captcha-token" to captchaToken),
            body = buildJsonObject {
                put("verification_code", verificationCode)
                put("verification_token", verificationToken)
                put("username", phone)
                put("client_id", CLIENT_ID)
            },
        )
        return result.toTokens()
    }

    suspend fun refresh(refreshToken: String, deviceId: String): SessionTokens {
        val result = post(
            url = "$ACCOUNT_BASE/v1/auth/token",
            headers = accountHeaders(deviceId) + ("x-action" to "401"),
            body = buildJsonObject {
                put("client_id", CLIENT_ID)
                put("grant_type", "refresh_token")
                put("refresh_token", refreshToken)
            },
        )
        return result.toTokens(fallbackRefreshToken = refreshToken)
    }

    suspend fun userInfo(accessToken: String, deviceId: String): UserProfile {
        val result = post(
            url = "$ACCOUNT_BASE/v1/user/me",
            headers = accountHeaders(deviceId) + ("authorization" to "Bearer $accessToken"),
        )
        val name = result.findString("nickname", "displayName", "userName", "username", "name")
            ?: result.findString("phone_number", "phone")
            ?: "已登录用户"
        return UserProfile(
            name = name,
            avatarUrl = result.findString("avatar", "avatarUrl", "avatar_url", "headImg", "icon"),
            phone = result.findString("phone_number", "phone", "mobile"),
        )
    }

    suspend fun listFiles(
        accessToken: String,
        deviceId: String,
        parentId: String?,
        page: Int = 0,
        pageSize: Int = 200,
    ): FilePage {
        val result = post(
            url = "$API_BASE/userres/v1/file/get_file_list",
            headers = apiHeaders(deviceId, accessToken),
            body = buildJsonObject {
                put("parentId", parentId.orEmpty())
                put("page", page)
                put("pageSize", pageSize)
                put("orderBy", 0)
                put("sortType", 0)
                put("needPlayRecord", true)
            },
        )
        val files = result.extractFileItems()
            .mapNotNull { it.toCloudFile(parentId) }
            .distinctBy { it.id }
            .sortedWith(compareBy<CloudFile> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
        return FilePage(files = files, hasMore = files.size >= pageSize)
    }

    suspend fun downloadUrl(accessToken: String, deviceId: String, fileId: String): String {
        val result = post(
            url = "$API_BASE/nd.bizuserres.s/v1/get_res_download_url",
            headers = apiHeaders(deviceId, accessToken),
            body = buildJsonObject {
                put("fileId", fileId)
            },
        )
        return result.findString("downloadUrl", "download_url", "url", "link", "resUrl", "sourceUrl")
            ?: throw IOException("下载接口未返回播放地址")
    }

    private suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: JsonObject? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val requestBody = (body?.toString().orEmpty()).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $responseText")
            }
            if (responseText.isBlank()) return@withContext JsonObject(emptyMap())
            json.parseToJsonElement(responseText).jsonObject
        }
    }

    private fun JsonObject.toTokens(fallbackRefreshToken: String? = null): SessionTokens {
        val accessToken = findString("access_token", "accessToken")
            ?: throw IOException("登录接口未返回 access_token")
        val refreshToken = findString("refresh_token", "refreshToken") ?: fallbackRefreshToken
        val expiresInSeconds = findLong("expires_in", "expiresIn")
        return SessionTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresInSeconds?.let { System.currentTimeMillis() + it * 1000L },
        )
    }

    private fun accountHeaders(deviceId: String): Map<String, String> = mapOf(
        "accept" to "*/*",
        "content-type" to "application/json",
        "origin" to "https://www.guangyapan.com",
        "referer" to "https://www.guangyapan.com/",
        "user-agent" to USER_AGENT,
        "x-client-id" to CLIENT_ID,
        "x-client-version" to "0.0.1",
        "x-device-id" to deviceId,
        "x-device-model" to "chrome%2F147.0.0.0",
        "x-device-name" to "PC-Chrome",
        "x-device-sign" to "wdi10.$deviceId${randomHex(16)}",
        "x-net-work-type" to "NONE",
        "x-os-version" to "MacIntel",
        "x-platform-version" to "1",
        "x-protocol-version" to "301",
        "x-provider-name" to "NONE",
        "x-sdk-version" to "9.0.2",
    )

    private fun apiHeaders(deviceId: String, accessToken: String): Map<String, String> = mapOf(
        "accept" to "application/json, text/plain, */*",
        "authorization" to "Bearer $accessToken",
        "content-type" to "application/json",
        "did" to deviceId,
        "dt" to "4",
        "origin" to "https://www.guangyapan.com",
        "referer" to "https://www.guangyapan.com/",
        "traceparent" to traceparent(),
        "user-agent" to USER_AGENT,
    )

    private fun JsonElement.findString(vararg names: String): String? {
        when (this) {
            is JsonObject -> {
                names.forEach { name ->
                    val value = this[name]
                    if (value is JsonPrimitive) value.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                }
                values.forEach { value ->
                    value.findString(*names)?.let { return it }
                }
            }
            is JsonArray -> forEach { value ->
                value.findString(*names)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    private fun JsonElement.findLong(vararg names: String): Long? {
        when (this) {
            is JsonObject -> {
                names.forEach { name ->
                    val value = this[name]
                    if (value is JsonPrimitive) {
                        value.longOrNull?.let { return it }
                        value.contentOrNull?.toLongOrNull()?.let { return it }
                    }
                }
                values.forEach { value ->
                    value.findLong(*names)?.let { return it }
                }
            }
            is JsonArray -> forEach { value ->
                value.findLong(*names)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    private fun JsonElement.findInt(vararg names: String): Int? {
        when (this) {
            is JsonObject -> {
                names.forEach { name ->
                    val value = this[name]
                    if (value is JsonPrimitive) {
                        value.intOrNull?.let { return it }
                        value.contentOrNull?.toIntOrNull()?.let { return it }
                    }
                }
                values.forEach { value ->
                    value.findInt(*names)?.let { return it }
                }
            }
            is JsonArray -> forEach { value ->
                value.findInt(*names)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    private fun JsonElement.findBoolean(vararg names: String): Boolean? {
        when (this) {
            is JsonObject -> {
                names.forEach { name ->
                    val value = this[name]
                    if (value is JsonPrimitive) {
                        value.booleanOrNull?.let { return it }
                        value.contentOrNull?.toBooleanStrictOrNull()?.let { return it }
                    }
                }
                values.forEach { value ->
                    value.findBoolean(*names)?.let { return it }
                }
            }
            is JsonArray -> forEach { value ->
                value.findBoolean(*names)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    private fun JsonObject.extractFileItems(): List<JsonObject> {
        val arrays = mutableListOf<JsonArray>()
        val likelyNames = setOf("list", "items", "records", "files", "fileList", "dataList", "rows")

        fun collect(element: JsonElement, depth: Int = 0) {
            if (depth > 5 || element is JsonNull) return
            when (element) {
                is JsonObject -> {
                    element.forEach { (key, value) ->
                        if (key in likelyNames && value is JsonArray) arrays += value
                        collect(value, depth + 1)
                    }
                }
                is JsonArray -> {
                    if (element.any { item ->
                            item is JsonObject && (
                                "fileId" in item ||
                                    "id" in item ||
                                    "fileName" in item ||
                                    "name" in item
                                )
                        }
                    ) {
                        arrays += element
                    }
                    element.forEach { collect(it, depth + 1) }
                }
                else -> Unit
            }
        }

        collect(this)
        return arrays
            .maxByOrNull { it.size }
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
    }

    private fun JsonObject.toCloudFile(parentId: String?): CloudFile? {
        val name = findString("name", "fileName", "filename", "dirName", "title") ?: return null
        val id = findString("fileId", "file_id", "id", "fid", "resId") ?: return null
        val typeName = findString("fileTypeName", "typeName", "categoryName", "kind")
        val fileType = findInt("fileType", "type", "category")
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val videoExtensions = setOf("mp4", "mkv", "mov", "m4v", "webm", "avi", "flv", "wmv", "ts", "m2ts")
        val isVideo = fileType == 2 ||
            extension in videoExtensions ||
            findString("mime", "mimeType", "contentType")?.startsWith("video/", ignoreCase = true) == true
        val explicitDirectory = findBoolean("isDir", "isDirectory", "dir", "folder")
        val isDirectory = explicitDirectory ?: (
            !isVideo && (
                fileType == 0 ||
                    typeName?.contains("文件夹") == true ||
                    typeName?.contains("folder", ignoreCase = true) == true ||
                    findString("mime", "mimeType") == "inode/directory"
                )
            )
        return CloudFile(
            id = id,
            name = name,
            isDirectory = isDirectory,
            isVideo = isVideo,
            sizeBytes = findLong("size", "fileSize", "bytes", "length"),
            updatedAt = findString("updatedAt", "updateTime", "updated_time", "modifyTime", "createdAt"),
            parentId = findString("parentId", "parent_id") ?: parentId,
            fileType = fileType,
        )
    }

    private fun randomHex(bytes: Int): String {
        val random = ByteArray(bytes)
        secureRandom.nextBytes(random)
        return random.joinToString("") { "%02x".format(it) }
    }

    private fun traceparent(): String = "00-${randomHex(16)}-${randomHex(8)}-01"

    companion object {
        private const val ACCOUNT_BASE = "https://account.guangyapan.com"
        private const val API_BASE = "https://api.guangyapan.com"
        private const val CLIENT_ID = "aMe-8VSlkrbQXpUR"
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val secureRandom = SecureRandom()

        fun generateDid(): String {
            val random = ByteArray(16)
            secureRandom.nextBytes(random)
            val digest = MessageDigest.getInstance("MD5").digest(random)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

