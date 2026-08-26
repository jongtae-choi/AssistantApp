package com.jongtae.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.jongtae.assistant.ui.main.AssistantApp
import com.jongtae.assistant.ui.main.AssistantViewModel
import com.jongtae.assistant.ui.theme.AssistantAppTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: AssistantViewModel by viewModels()

    private var pendingCameraUri: Uri? = null

    // 사진 여러 장(10장 이상 가능) 선택
    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) viewModel.addPickedUris(uris)
        }

    // 파일(PDF 등) 여러 개 선택
    private val pickFilesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) viewModel.addPickedUris(uris)
        }

    // 카메라는 한 번에 한 장 촬영 — 촬영 결과를 선택 목록에 추가
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.addPickedUris(listOf(it)) }
    }

    // 연락처 접근 권한 요청 — 결과를 뷰모델에 반영하고, 허용되면 즉시 한 번 동기화 + 감시 등록한다.
    // (자동 동기화 켜짐 시 15분 주기 백그라운드 작업 등록은 viewModel.setAutoSyncContacts에서 이미 처리됨)
    private val requestContactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.setHasContactsPermission(granted)
            if (granted) {
                registerContactsObserverIfPermitted()
                viewModel.syncContactsNow()
            }
        }

    // 권한 없이 등록을 시도하면 SecurityException으로 앱이 즉시 죽을 수 있어, 반드시 권한이
    // 있을 때만 등록하고 중복 등록되지 않도록 상태를 추적한다.
    private var contactsObserverRegistered = false

    // 연락처가 폰에서 변경되면(추가/수정/삭제) 앱이 포그라운드에 있는 동안 즉시 재동기화한다.
    // (백그라운드에서는 WorkManager의 15분 주기 작업이 대신 따라잡는다 — Android는 15분 미만의
    // 주기적 백그라운드 실행을 허용하지 않기 때문에, "실시간"은 앱이 열려 있을 때만 가능하다.)
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val ui = viewModel.uiState.value
            if (ui.autoSyncContacts && ui.hasContactsPermission && !ui.isSyncingContacts) {
                viewModel.syncContactsNow()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.setHasContactsPermission(hasContactsPermission())
        handleIncomingIntent(intent)

        setContent {
            AssistantAppTheme {
                AssistantApp(
                    viewModel = viewModel,
                    onPickImages = { pickImagesLauncher.launch("image/*") },
                    onPickFiles = { pickFilesLauncher.launch("*/*") },
                    onTakePhoto = { launchCamera() },
                    onRequestContactsPermission = {
                        requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 앱이 다시 포그라운드로 올 때 권한 상태가 설정 화면(OS 설정)에서 바뀌었을 수 있으니 먼저 최신화
        viewModel.setHasContactsPermission(hasContactsPermission())
        registerContactsObserverIfPermitted()
    }

    override fun onStop() {
        super.onStop()
        unregisterContactsObserverIfNeeded()
    }

    /** READ_CONTACTS 권한이 있을 때만 연락처 변경 감시를 등록한다 — 권한 없이 등록하면 크래시 위험이 있다. */
    private fun registerContactsObserverIfPermitted() {
        if (contactsObserverRegistered || !hasContactsPermission()) return
        try {
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contactsObserver
            )
            contactsObserverRegistered = true
        } catch (_: SecurityException) {
            // 일부 기기/OS 버전에서 권한 체크 타이밍이 달라 예외가 날 수 있어 방어적으로 무시
        }
    }

    private fun unregisterContactsObserverIfNeeded() {
        if (!contactsObserverRegistered) return
        contentResolver.unregisterContentObserver(contactsObserver)
        contactsObserverRegistered = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** 다른 앱(갤러리, 카메라, 파일관리자 등)에서 "공유하기"로 사진/PDF를 보낸 경우 자동으로 선택 목록에 반영 */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) viewModel.addPickedUris(listOf(uri))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!uris.isNullOrEmpty()) viewModel.addPickedUris(uris)
            }
        }
    }

    private fun launchCamera() {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(imagesDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "com.jongtae.assistant.fileprovider", file)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }
}
