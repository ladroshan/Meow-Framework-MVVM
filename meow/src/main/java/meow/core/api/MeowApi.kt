/*
 * Copyright (C) 2020 Hamidreza Etebarian & Ali Modares.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package meow.core.api

import android.webkit.WebSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import meow.MeowApp
import meow.controller
import meow.utils.avoidException
import meow.utils.hasNetwork
import meow.utils.logD
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The class of Api Utils such as OKHTTP client, Retrofit Configuration containing Moshi Adapter.
 * and containing Authorization methods.
 *
 * @author  Hamidreza Etebarian
 * @version 1.0.0
 * @since   2020-02-28
 */

typealias InterceptorBlock = (builder: Request.Builder) -> Unit

val TAG = "MeowApi"

fun MeowApp.getMeowClientBuilder() = OkHttpClient.Builder().apply {
    connectTimeout(30, TimeUnit.SECONDS)
    readTimeout(60, TimeUnit.SECONDS)
    writeTimeout(60, TimeUnit.SECONDS)
    cache(Cache(cacheDir, 10 * 1024 * 1024))
    if (controller.isDebugMode)
        addNetworkInterceptor(MeowLoggingInterceptor())
}

fun MeowApp.getUserAgent() =
    WebSettings.getDefaultUserAgent(this).replace(Regex("[^A-Za-z0-9 ().,_/]"), "")

fun MeowApp.getCacheInterceptorBlock(options: MeowApi.Options): InterceptorBlock = {
    if (hasNetwork())
        it.header("Cache-Control", "no-cache")
    else if (options.isEnabledCache)
        it.header(
            "Cache-Control",
            "public, only-if-cached, max-stale=$options.maxStateSecond"
        )
}

abstract class MeowApi(
    open val options: Options = Options()
) {

    abstract fun getOkHttpClient(): OkHttpClient
    abstract fun getBaseUrl(): String

    fun createDefaultService(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(getOkHttpClient())
            .build()
    }

    open fun getRefreshTokenResponse(): retrofit2.Response<MeowOauthToken>? {
        return null
    }

    open fun onSaveOauth(oauthToken: MeowOauthToken) {
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> createServiceByAdapter(adapter: Any? = null): T {
        val moshiBuilder = Moshi.Builder()
        val moshi = adapter?.let {
            moshiBuilder.add(KotlinJsonAdapterFactory()).add(it).build()
        } ?: moshiBuilder.add(KotlinJsonAdapterFactory()).build()
        return createDefaultService().newBuilder()
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build().create(T::class.java)
    }

    open fun onUnauthorizedAfterAuthenticate() {
    }

    sealed class Authorization(val interceptorBlock: InterceptorBlock) {
        //todo add other types
        class SimpleToken(
            var isLogin: Boolean,
            var token: String
        ) : Authorization({
            if (isLogin)
                it.header("Authorization", "Bearer $token")
        })
    }

    class RefreshToken(
        val meowApi: MeowApi,
        val token: Authorization.SimpleToken
    ) : Authenticator {

        override fun authenticate(route: Route?, response: Response): Request? {
            return if (token.isLogin) {
                logD(
                    TAG,
                    "response code in authenticator : " + response.code + " , response count : " + responseCount(
                        response
                    )
                )
                if (responseCount(response) >= 2) {
                    logD(TAG, "response count is full")
                    // If both the original callApi and the callApi with refreshed token failed,
                    // it will probably keep failing, so don't try again.
                    null
                } else {
                    val tokenResponse =
                        avoidException { meowApi.getRefreshTokenResponse() } ?: return null

                    return when {
                        tokenResponse.code() == HttpCodes.OK.code -> {
                            logD(TAG, "new token is created by refresh token")

                            val newToken = tokenResponse.body() ?: return null
                            meowApi.onSaveOauth(newToken)
                            token

                            response.request.newBuilder()
                                .header("Authorization", token.apply { isLogin = true }.token)
                                .build()
                        }
                        tokenResponse.code() == HttpCodes.UNAUTHORIZED.code -> {
                            logD(TAG, "unAuthorize when getting new token")
                            meowApi.onUnauthorizedAfterAuthenticate()
                            null
                        }
                        else -> {
                            null
                        }
                    }
                }
            } else {
                null
            }
        }
    }

    class Options(
        var isEnabledCache: Boolean = false,
        var cacheMaxStaleSecond: Long = 30 * 60L
    )

}

fun MeowApi.enableCache() = apply { options.isEnabledCache = true }
fun MeowApi.changeCacheInDay(day: Int) = apply { options.cacheMaxStaleSecond = day * 24 * 60 * 60L }
fun MeowApi.changeCacheInHour(hour: Int) = apply { options.cacheMaxStaleSecond = hour * 60 * 60L }
fun MeowApi.changeCacheInMinutes(minute: Int) = apply { options.cacheMaxStaleSecond = minute * 60L }
fun MeowApi.changeCacheInSeconds(seconds: Int) =
    apply { options.cacheMaxStaleSecond = seconds.toLong() }

fun OkHttpClient.Builder.addInterceptorBlocks(interceptorBlocks: List<InterceptorBlock>) {
    addInterceptor {
        val request = it.request().newBuilder()
        interceptorBlocks.forEach {
            it(request)
        }
        it.proceed(request.build())
    }
}

fun responseCount(response: Response?): Int {
    var rs = response?.priorResponse
    var result = 1
    while (rs != null) {
        result++
        rs = rs.priorResponse
    }
    return result
}