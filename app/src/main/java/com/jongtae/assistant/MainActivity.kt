package com.jongtae.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.Toast
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

    // ── 캘린더 일정 등록 화면 전용 사진 선택/촬영 (메인 화면의 선택 목록과는 별개로 관리) ──
    private var pendingCalendarCameraUri: Uri? = null

    private val pickCalendarImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) viewModel.addCalendarPickedUris(uris)
        }

    private val takeCalendarPictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCalendarCameraUri?.let { viewModel.addCalendarPickedUris(listOf(it)) }
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

    // 카메라 촬영 권한 요청 — 매니페스트에 CAMERA 권한을 선언해두면, 런타임에 실제로
    // 허용받기 전까지는 ACTION_IMAGE_CAPTURE(카메라 앱 실행) 자체가 실패한다(크래시 원인).
    // 그래서 촬영 버튼을 누를 때마다 먼저 권한이 있는지 확인하고, 없으면 요청부터 한다.
    // (메인 화면 촬영/캘린더 화면 촬영 둘 다 이 한 곳에서 처리 — pendingCameraAction에
    //  "권한 허용되면 할 일"을 담아뒀다가, 허용되면 그걸 실행한다)
    private var pendingCameraAction: (() -> Unit)? = null

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingCameraAction?.invoke()
            pendingCameraAction = null
            // 거부되면 아무 것도 하지 않는다 (버튼을 다시 누르면 재요청됨)
        }

    // 알림 권한 요청 (Android 13+) — 문서 생성/보완이 비동기(폴링)로 바뀌면서, 앱이
    // 백그라운드에 있어도 완료를 알 수 있도록 알림을 띄운다. 거부해도 앱 사용에는
    // 지장 없음(화면에서 결과를 직접 확인 가능) — 그래서 결과만 조용히 무시한다.
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과 무시 */ }

    // 음성으로 지시사항 입력 — 안드로이드 기본 음성 인식(구글) 화면을 띄워서 인식된
    // 텍스트만 돌려받는다. 녹음 자체는 그 화면(구글 앱)이 처리하므로 별도의 RECORD_AUDIO
    // 권한 요청이 필요 없다.
    private val voiceInputLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) viewModel.appendVoiceInstructionText(text)
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
        requestNotificationPermissionIfNeeded()

        setContent {
            AssistantAppTheme {
                AssistantApp(
                    viewModel = viewModel,
                    onPickImages = { pickImagesLauncher.launch("image/*") },
                    onPickFiles = { pickFilesLauncher.launch("*/*") },
                    onTakePhoto = { launchCamera() },
                    onRequestContactsPermission = {
                        requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onVoiceInput = { launchVoiceInput() },
                    onPickCalendarImages = { pickCalendarImagesLauncher.launch("image/*") },
                    onTakeCalendarPhoto = { launchCalendarCamera() }
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

    /** Android 13(API 33) 미만은 알림 권한이 필요 없으므로(매니페스트 선언만으로 동작) 그 이하에서는 건너뛴다. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

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

    /** 음성 인식 화면을 띄워서 말한 내용을 텍스트로 받아온다 (지시사항 입력용). */
    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "지시사항을 말씀해주세요")
        }
        if (intent.resolveActivity(packageManager) != null) {
            voiceInputLauncher.launch(intent)
        } else {
            Toast.makeText(this, "이 기기에서는 음성 인식을 사용할 수 없어요", Toast.LENGTH_SHORT).show()
        }
    }

    /** CAMERA 권한이 있으면 바로 action을 실행하고, 없으면 요청부터 한 뒤 허용되면 실행한다. */
    private fun ensureCameraPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingCameraAction = action
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() = ensureCameraPermission {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(imagesDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "com.jongtae.assistant.fileprovider", file)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun launchCalendarCamera() = ensureCameraPermission {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(imagesDir, "calendar_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "com.jongtae.assistant.fileprovider", file)
        pendingCalendarCameraUri = uri
        takeCalendarPictureLauncher.launch(uri)
    }
}
