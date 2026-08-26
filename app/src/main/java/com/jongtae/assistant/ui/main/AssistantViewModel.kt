package com.jongtae.assistant.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jongtae.assistant.data.contacts.ContactsSyncManager
import com.jongtae.assistant.data.contacts.ContactsSyncWorker
import com.jongtae.assistant.data.model.ArchiveGroup
import com.jongtae.assistant.data.model.Citation
import com.jongtae.assistant.data.network.ApiProvider
import com.jongtae.assistant.data.network.AssistantApiService
import com.jongtae.assistant.data.repository.AssistantRepository
import com.jongtae.assistant.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AssistantUiState(
    val isConfigured: Boolean = false,
    val baseUrl: String = "",
    val token: String = "",
    val defaultEmail: String = "",

    // ── 1. 사진/파일 선택 (다중) ──
    val pickedUris: List<Uri> = emptyList(),

    // ── 2. 지시사항 ──
    val youtubeUrl: String = "",
    val instruction: String = "",
    val showEvidence: Boolean = true, // "근거표시" 체크박스
    val isAnalyzing: Boolean = false,
    val resultText: String? = null,
    val citations: List<Citation> = emptyList(),
    val analyzeError: String? = null,

    // ── 3. 문서로 정리해서 메일로 받기 ──
    val to: String = "",
    val subject: String = "",
    val docType: String = "excel", // "excel" | "pptx"
    val matchContacts: Boolean = true, // "주소록 정보 활용" 토글
    val isRunningPipeline: Boolean = false,
    val pipelineDocTitle: String? = null,
    val pipelineMessage: String? = null,
    val pipelineError: String? = null,

    // ── 생성된 이미지 결과 ──
    val resultImageFilenames: List<String> = emptyList(),
    val selectedResultImages: Set<String> = emptySet(),

    // ── 저장(제목 확인) 다이얼로그 ──
    val showSaveDialog: Boolean = false,
    val isSuggestingTitles: Boolean = false,
    val draftTitles: Map<String, String> = emptyMap(), // filename -> 편집 중인 제목
    val isSavingOutputs: Boolean = false,
    val saveError: String? = null,
    val lastSavedMessage: String? = null,

    // ── 전체화면 이미지 뷰어 ──
    val viewerUrls: List<String> = emptyList(),
    val viewerStartIndex: Int = 0,
    val viewerVisible: Boolean = false,

    // ── 보관함 ──
    val showArchive: Boolean = false,
    val archiveGroups: List<ArchiveGroup> = emptyList(),
    val archivePeriod: String = "week", // today|week|month|all
    val archiveQuery: String = "",
    val isLoadingArchive: Boolean = false,
    val archiveError: String? = null,

    // ── 연락처 동기화 ──
    val hasContactsPermission: Boolean = false,
    val autoSyncContacts: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncCount: Int = 0,
    val isSyncingContacts: Boolean = false,
    val contactsSyncError: String? = null
) {
    /** "근거표시" 체크 시 지시사항 뒤에 문구를 붙여서 하나의 문자열로 합친다 — Claude API로 그대로 전송됨 */
    val combinedInstruction: String
        get() {
            val base = instruction.trim()
            return if (showEvidence) (if (base.isEmpty()) "근거표시" else "$base 근거표시") else base
        }
}

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private var api: AssistantApiService? = null
    private var repository: AssistantRepository? = null

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val baseUrl = settingsStore.baseUrlFlow.first()
            val token = settingsStore.tokenFlow.first()
            val email = settingsStore.defaultEmailFlow.first()
            val autoSync = settingsStore.autoSyncContactsFlow.first()
            val lastSyncAt = settingsStore.lastSyncAtFlow.first()
            val lastSyncCount = settingsStore.lastSyncCountFlow.first()
            _uiState.value = _uiState.value.copy(
                baseUrl = baseUrl, token = token, defaultEmail = email, to = email,
                subject = "개인비서 문서",
                autoSyncContacts = autoSync, lastSyncAt = lastSyncAt, lastSyncCount = lastSyncCount
            )
            if (baseUrl.isNotBlank() && token.isNotBlank()) {
                setupClient(baseUrl, token)
            }
        }
    }

    private fun setupClient(baseUrl: String, token: String) {
        val client = ApiProvider.create(baseUrl, token)
        api = client
        repository = AssistantRepository(getApplication(), client)
        _uiState.value = _uiState.value.copy(isConfigured = true, baseUrl = baseUrl, token = token)
    }

    fun saveSettings(baseUrl: String, token: String, defaultEmail: String) {
        viewModelScope.launch {
            settingsStore.save(baseUrl, token, defaultEmail)
            setupClient(baseUrl, token)
            _uiState.value = _uiState.value.copy(
                defaultEmail = defaultEmail,
                to = _uiState.value.to.ifBlank { defaultEmail }
            )
        }
    }

    // ══════════════════════ 1. 사진/파일 선택 ══════════════════════

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val merged = (_uiState.value.pickedUris + uris).distinct()
        _uiState.value = _uiState.value.copy(pickedUris = merged, resultText = null, analyzeError = null, pipelineMessage = null)
    }

    fun removePickedUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(pickedUris = _uiState.value.pickedUris - uri)
    }

    fun clearPickedUris() {
        _uiState.value = _uiState.value.copy(pickedUris = emptyList())
    }

    // ══════════════════════ 2. 지시사항 ══════════════════════

    fun setYoutubeUrl(text: String) { _uiState.value = _uiState.value.copy(youtubeUrl = text) }
    fun setInstruction(text: String) { _uiState.value = _uiState.value.copy(instruction = text) }
    fun setShowEvidence(checked: Boolean) { _uiState.value = _uiState.value.copy(showEvidence = checked) }

    fun analyze() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.pickedUris.isEmpty() && state.youtubeUrl.isBlank()) {
            _uiState.value = state.copy(analyzeError = "먼저 사진/파일을 선택하거나 유튜브 링크를 입력해주세요")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, analyzeError = null, resultText = null)
            try {
                val res = repo.analyzeImage(_uiState.value.pickedUris, _uiState.value.combinedInstruction)
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    resultText = res.text ?: "(응답 없음)",
                    citations = res.citations
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    analyzeError = "분석 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    // ══════════════════════ 3. 문서로 정리해서 메일로 받기 ══════════════════════

    fun setTo(text: String) { _uiState.value = _uiState.value.copy(to = text) }
    fun setSubject(text: String) { _uiState.value = _uiState.value.copy(subject = text) }
    fun setDocType(type: String) { _uiState.value = _uiState.value.copy(docType = type) }
    fun setMatchContacts(checked: Boolean) { _uiState.value = _uiState.value.copy(matchContacts = checked) }

    fun runPipeline() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.pickedUris.isEmpty() && state.youtubeUrl.isBlank()) {
            _uiState.value = state.copy(pipelineError = "사진/파일을 선택하거나 유튜브 링크를 입력해주세요")
            return
        }
        // 받는 사람을 적었으면 이메일도 같이 보내고(both), 안 적었으면 결과 이미지 링크만 만든다(link)
        val outputMode = if (state.to.isBlank()) "link" else "both"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunningPipeline = true, pipelineError = null, pipelineMessage = null,
                resultImageFilenames = emptyList(), selectedResultImages = emptySet()
            )
            try {
                val res = repo.pipelinePhotoToDocument(
                    uris = state.pickedUris,
                    instruction = state.combinedInstruction,
                    docType = state.docType,
                    outputMode = outputMode,
                    to = state.to,
                    subject = state.subject,
                    youtubeUrl = state.youtubeUrl,
                    matchContacts = state.matchContacts
                )
                val message = buildString {
                    append("문서 생성 완료")
                    if (res.gmailMessageId != null) append(" · 메일 발송됨")
                    if (res.emailError != null) append(" (메일 발송 실패: ${res.emailError})")
                }
                _uiState.value = _uiState.value.copy(
                    isRunningPipeline = false,
                    pipelineDocTitle = res.title,
                    pipelineMessage = message,
                    resultImageFilenames = res.imageFilenames
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningPipeline = false,
                    pipelineError = "요청 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    /** outputs 필드명(파일명)만으로 실제 접근 가능한 전체 URL을 조립한다 */
    fun outputImageUrl(filename: String): String {
        val base = _uiState.value.baseUrl.trimEnd('/')
        return "$base/assistant/outputs/${Uri.encode(filename)}"
    }

    // ══════════════════════ 생성된 이미지: 선택 ══════════════════════

    fun toggleResultImageSelection(filename: String) {
        val current = _uiState.value.selectedResultImages
        val updated = if (filename in current) current - filename else current + filename
        _uiState.value = _uiState.value.copy(selectedResultImages = updated)
    }

    fun selectAllResultImages() {
        _uiState.value = _uiState.value.copy(selectedResultImages = _uiState.value.resultImageFilenames.toSet())
    }

    fun clearResultImageSelection() {
        _uiState.value = _uiState.value.copy(selectedResultImages = emptySet())
    }

    // ══════════════════════ 저장(제목 확인) 다이얼로그 ══════════════════════

    fun openSaveDialog() {
        val repo = repository ?: return
        val targets = _uiState.value.selectedResultImages.toList()
        if (targets.isEmpty()) return
        _uiState.value = _uiState.value.copy(showSaveDialog = true, isSuggestingTitles = true, saveError = null)
        viewModelScope.launch {
            val fallback = targets.associateWith { it.substringBeforeLast('.') }
            val titles = try {
                val suggested = repo.suggestTitles(targets)
                fallback + suggested // suggested가 우선(있으면 덮어씀), 없으면 fallback 유지
            } catch (e: Exception) {
                fallback
            }
            _uiState.value = _uiState.value.copy(isSuggestingTitles = false, draftTitles = titles)
        }
    }

    fun updateDraftTitle(filename: String, newTitle: String) {
        _uiState.value = _uiState.value.copy(
            draftTitles = _uiState.value.draftTitles + (filename to newTitle)
        )
    }

    fun dismissSaveDialog() {
        _uiState.value = _uiState.value.copy(showSaveDialog = false, draftTitles = emptyMap(), saveError = null)
    }

    fun confirmSaveSelected() {
        val repo = repository ?: return
        val state = _uiState.value
        val items = state.selectedResultImages.map { filename ->
            filename to (state.draftTitles[filename]?.ifBlank { filename.substringBeforeLast('.') } ?: filename)
        }
        if (items.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingOutputs = true, saveError = null)
            try {
                val saved = repo.saveOutputs(items)
                _uiState.value = _uiState.value.copy(
                    isSavingOutputs = false,
                    showSaveDialog = false,
                    draftTitles = emptyMap(),
                    selectedResultImages = emptySet(),
                    lastSavedMessage = "${saved.size}장을 보관함에 저장했습니다"
                )
                // 보관함이 이미 열려 있었으면 최신 내용으로 갱신
                if (_uiState.value.showArchive) loadArchive()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingOutputs = false,
                    saveError = "저장 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    // ══════════════════════ 전체화면 이미지 뷰어 ══════════════════════

    fun openViewer(urls: List<String>, startIndex: Int) {
        if (urls.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            viewerUrls = urls,
            viewerStartIndex = startIndex.coerceIn(0, urls.lastIndex),
            viewerVisible = true
        )
    }

    fun closeViewer() {
        _uiState.value = _uiState.value.copy(viewerVisible = false)
    }

    // ══════════════════════ 보관함 ══════════════════════

    fun openArchive() {
        _uiState.value = _uiState.value.copy(showArchive = true)
        loadArchive()
    }

    fun closeArchive() {
        _uiState.value = _uiState.value.copy(showArchive = false)
    }

    fun setArchivePeriod(period: String) {
        _uiState.value = _uiState.value.copy(archivePeriod = period)
        loadArchive(period = period)
    }

    fun setArchiveQuery(q: String) {
        _uiState.value = _uiState.value.copy(archiveQuery = q)
    }

    fun searchArchive() {
        loadArchive()
    }

    fun loadArchive(period: String? = null, q: String? = null) {
        val repo = repository ?: return
        val effectivePeriod = period ?: _uiState.value.archivePeriod
        val effectiveQuery = q ?: _uiState.value.archiveQuery
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingArchive = true, archiveError = null)
            try {
                val groups = repo.getArchive(
                    period = effectivePeriod,
                    q = effectiveQuery.ifBlank { null }
                )
                _uiState.value = _uiState.value.copy(isLoadingArchive = false, archiveGroups = groups)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingArchive = false,
                    archiveError = "보관함을 불러오지 못했습니다: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    /** ArchiveEntry.url("/assistant/outputs/xxx" 형태)을 전체 URL로 조립 */
    fun archiveEntryUrl(relativeUrl: String?): String {
        if (relativeUrl == null) return ""
        val base = _uiState.value.baseUrl.trimEnd('/')
        return if (relativeUrl.startsWith("http")) relativeUrl else "$base$relativeUrl"
    }

    // ══════════════════════ 연락처 동기화 ══════════════════════

    fun setHasContactsPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasContactsPermission = granted)
    }

    /** 자동 동기화 토글 — 켜면 즉시 1회 동기화 + 15분 주기 백그라운드 작업 등록, 끄면 작업 취소 */
    fun setAutoSyncContacts(enabled: Boolean) {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(autoSyncContacts = enabled)
        viewModelScope.launch { settingsStore.setAutoSyncContacts(enabled) }
        if (enabled) {
            ContactsSyncWorker.schedule(context)
            if (_uiState.value.hasContactsPermission) syncContactsNow()
        } else {
            ContactsSyncWorker.cancel(context)
        }
    }

    fun syncContactsNow() {
        if (!_uiState.value.hasContactsPermission) {
            _uiState.value = _uiState.value.copy(contactsSyncError = "연락처 접근 권한이 필요합니다")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncingContacts = true, contactsSyncError = null)
            val result = ContactsSyncManager.syncNow(getApplication())
            result.onSuccess { count ->
                _uiState.value = _uiState.value.copy(
                    isSyncingContacts = false,
                    lastSyncAt = System.currentTimeMillis(),
                    lastSyncCount = count
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSyncingContacts = false,
                    contactsSyncError = e.message ?: "동기화에 실패했습니다"
                )
            }
        }
    }
}
