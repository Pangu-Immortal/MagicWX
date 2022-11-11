package com.bignerdranch.android.photogallery.api

import okhttp3.Interceptor
import okhttp3.Response

private const val API_KEY = "4f721bbafa75bf6d2cb5af54f937bb70"

class PhotoInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Add necessary query parameters to all Flickr calls
        val newUrl = request.url().newBuilder()
                .addQueryParameter("api_key", API_KEY)
                .addQueryParameter("format", "json")
                .addQueryParameter("nojsoncallback", "1")
                .addQueryParameter("extras", "url_s")
                .build()
        // Build new request using new Url
        val newRequest = request.newBuilder()
                .url(newUrl)
                .build()

        return chain.proceed(newRequest)
    }
}