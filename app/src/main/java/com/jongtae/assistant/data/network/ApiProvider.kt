package com.jongtae.assistant.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiProvider {

    fun create(baseUrl: String, token: String): AssistantApiService {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val authInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("X-My-Token", token)
                .build()
            chain.proceed(req)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // 사진 여러 장 + Claude 웹서치 + 문서 생성까지 이어지는 파이프라인은 시간이 걸릴 수 있어
        // 읽기 타임아웃을 넉넉히 잡는다. (photo-to-gmail은 서버가 즉시 202류 응답을 먼저 보내므로
        // 실제로는 오래 걸리지 않지만, analyze-image/photo-to-document는 Claude 응답을 그대로
        // 기다려야 하고, 사진이 여러 장이면 더 오래 걸릴 수 있음. 리서치 기능이 GPT/Gemini/Groq/
        // OpenRouter까지 병렬로 물어보고 Claude가 교차검증하는 방식으로 확장되면서(가장 느린
        // AI 하나 + Claude 종합 시간) 응답이 더 오래 걸릴 수 있어 240초로 여유를 더 뒀다)
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .writeTimeout(240, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AssistantApiService::class.java)
    }
}
