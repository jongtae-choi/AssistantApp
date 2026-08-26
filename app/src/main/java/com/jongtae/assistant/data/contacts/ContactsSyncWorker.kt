package com.jongtae.assistant.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * "자동 동기화" 토글이 켜져 있는 동안 연락처를 서버에 계속 최신 상태로 맞춰준다.
 *
 * 안드로이드는 앱이 백그라운드에 있을 때 연락처 변경을 그 즉시 감지해서 앱을 깨워주는
 * 방법을 제공하지 않는다(배터리 최적화/Doze 제약). 그래서 "실시간 업로드"에 실질적으로
 * 가장 가깝게 구현하는 방법은 두 가지를 함께 쓰는 것이다:
 *   1) 앱이 켜져 있는 동안은 연락처 변경을 즉시 감지해서 바로 동기화 (MainActivity의 ContentObserver)
 *   2) 앱이 꺼져 있어도, WorkManager가 최소 주기(15분)로 다시 확인해서 놓친 변경사항을 따라잡음
 * 즉 "폰을 쓰는 동안은 즉시, 최악의 경우에도 15분 안에는 반영"되는 구조다.
 */
class ContactsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val granted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.failure()

        val result = ContactsSyncManager.syncNow(applicationContext)
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "contacts_auto_sync"

        /** 자동 동기화 토글을 켤 때 호출 — 15분 주기 백그라운드 작업을 등록한다. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ContactsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** 자동 동기화 토글을 끌 때 호출 — 예약된 백그라운드 작업을 취소한다. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
