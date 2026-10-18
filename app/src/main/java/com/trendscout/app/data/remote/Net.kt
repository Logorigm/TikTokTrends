package com.trendscout.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Typed failure so the UI can explain what went wrong instead of showing a stack trace. */
sealed class NetError {
    data object NoInternet : NetError()
    data object Timeout : NetError()
    data class Http(val code: Int, val body: String? = null) : NetError()
    data object Unauthorized : NetError()
    data object RateLimited : NetError()
    data class Parse(val detail: String?) : NetError()
    data class Other(val detail: String?) : NetError()
}

sealed class NetResult<out T> {
    data class Ok<T>(val value: T) : NetResult<T>()
    data class Err(val error: NetError) : NetResult<Nothing>()
}

class Net(private val context: Context) {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Executes the request off the main thread and maps failures to [NetError]. */
    suspend fun <T> execute(request: Request, parse: (Response, String) -> T): NetResult<T> = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext NetResult.Err(NetError.NoInternet)
        try {
            client.newCall(request).await().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 || resp.code == 403 -> NetResult.Err(NetError.Unauthorized)
                    resp.code == 429 -> NetResult.Err(NetError.RateLimited)
                    !resp.isSuccessful -> NetResult.Err(NetError.Http(resp.code, body.take(500)))
                    else -> try {
                        NetResult.Ok(parse(resp, body))
                    } catch (e: Exception) {
                        NetResult.Err(NetError.Parse(e.message))
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            NetResult.Err(NetError.Timeout)
        } catch (e: UnknownHostException) {
            NetResult.Err(NetError.NoInternet)
        } catch (e: IOException) {
            NetResult.Err(NetError.Other(e.message))
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
