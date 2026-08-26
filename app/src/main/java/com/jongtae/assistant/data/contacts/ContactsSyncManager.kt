package com.jongtae.assistant.data.contacts

import android.content.Context
import com.jongtae.assistant.data.network.ApiProvider
import com.jongtae.assistant.data.repository.AssistantRepository
import com.jongtae.assistant.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * 연락처 동기화의 실제 동작(기기 연락처 읽기 → 서버 전송 → 마지막 동기화 기록)을 한 곳에 모아둔다.
 * 설정 화면의 "지금 동기화" 버튼, 앱 실행 중 연락처 변경 감지, 백그라운드 주기 동기화(WorkManager)가
 * 전부 이 함수 하나를 공유해서 호출한다 — 로직이 여러 곳에 흩어지지 않도록.
 */
object ContactsSyncManager {

    suspend fun syncNow(context: Context): Result<Int> {
        val settingsStore = SettingsStore(context)
        val baseUrl = settingsStore.baseUrlFlow.first()
        val token = settingsStore.tokenFlow.first()
        val ownerId = settingsStore.contactsOwnerIdFlow.first()
        if (baseUrl.isBlank() || token.isBlank()) {
            return Result.failure(IllegalStateException("서버 주소/토큰이 설정되지 않았습니다"))
        }

        return try {
            val (vcf, count) = ContactsVCardBuilder.buildFromDevice(context)
            if (count == 0) {
                return Result.failure(IllegalStateException("이름+전화번호가 있는 연락처를 찾지 못했습니다"))
            }
            val api = ApiProvider.create(baseUrl, token)
            val repo = AssistantRepository(context, api)
            val (ok, savedCount) = repo.syncContacts(ownerId, vcf)
            if (ok) {
                settingsStore.recordContactsSync(savedCount, System.currentTimeMillis())
                Result.success(savedCount)
            } else {
                Result.failure(IllegalStateException("서버가 저장을 거부했습니다"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
