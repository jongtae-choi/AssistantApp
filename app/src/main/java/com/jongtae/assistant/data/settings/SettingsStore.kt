package com.jongtae.assistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "assistant_settings")

class SettingsStore(private val context: Context) {
    companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_DEFAULT_EMAIL = stringPreferencesKey("default_email")

        // 연락처 동기화 관련
        val KEY_AUTO_SYNC_CONTACTS = booleanPreferencesKey("auto_sync_contacts")
        val KEY_LAST_SYNC_AT = longPreferencesKey("last_contacts_sync_at") // epoch millis
        val KEY_LAST_SYNC_COUNT = intPreferencesKey("last_contacts_sync_count")
        // 이 기기의 연락처가 서버 주소록에 어떤 owner 이름으로 저장되는지(기본: "default")
        val KEY_CONTACTS_OWNER_ID = stringPreferencesKey("contacts_owner_id")
    }

    val baseUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: "" }
    val tokenFlow: Flow<String> = context.dataStore.data.map { it[KEY_TOKEN] ?: "" }
    val defaultEmailFlow: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_EMAIL] ?: "" }

    val autoSyncContactsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SYNC_CONTACTS] ?: false }
    val lastSyncAtFlow: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_SYNC_AT] ?: 0L }
    val lastSyncCountFlow: Flow<Int> = context.dataStore.data.map { it[KEY_LAST_SYNC_COUNT] ?: 0 }
    val contactsOwnerIdFlow: Flow<String> = context.dataStore.data.map { it[KEY_CONTACTS_OWNER_ID] ?: "default" }

    suspend fun save(baseUrl: String, token: String, defaultEmail: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[KEY_TOKEN] = token.trim()
            prefs[KEY_DEFAULT_EMAIL] = defaultEmail.trim()
        }
    }

    suspend fun setAutoSyncContacts(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_SYNC_CONTACTS] = enabled }
    }

    suspend fun recordContactsSync(count: Int, atMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_AT] = atMillis
            prefs[KEY_LAST_SYNC_COUNT] = count
        }
    }
}
