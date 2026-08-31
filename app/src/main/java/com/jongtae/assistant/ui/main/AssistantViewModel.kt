package com.jongtae.assistant.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.jongtae.assistant.data.contacts.ContactsSyncManager
import com.jongtae.assistant.data.contacts.ContactsSyncWorker
import com.jongtae.assistant.data.model.ApiErrorBody
import com.jongtae.assistant.data.model.ArchiveGroup
import com.jongtae.assistant.data.model.CalendarEventDraft
import com.jongtae.assistant.data.model.Citation
import com.jongtae.assistant.data.model.LedgerEntryDraft
import com.jongtae.assistant.data.model.LedgerGroup
import com.jongtae.assistant.data.model.LedgerSummary
import com.jongtae.assistant.data.network.ApiProvider
import com.jongtae.assistant.data.network.AssistantApiService
import com.jongtae.assistant.data.repository.AssistantRepository
import com.jongtae.assistant.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * 서버가 HTTP 에러(4xx/5xx)와 함께 JSON으로 보낸 실제 에러 메시지를 최대한 꺼내서 보여준다.
 * Retrofit의 HttpException.message는 "HTTP 400" 처럼 상태코드만 담고 있어서, 서버가
 * { "error": "..." } 형태로 보낸 진짜 이유(예: "유튜브 자막을 가져오지 못했습니다: ...")를
 * 대신 파싱해서 보여준다. 파싱에 실패하면 원문/기본 메시지로 조용히 대체한다.
 */
private fun Throwable.friendlyMessage(default: String = "알 수 없는 오류"): String {
    if (this is HttpException) {
        try {
            val body = response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                try {
                    val parsed = Gson().fromJson(body, ApiErrorBody::class.java)
                    if (!parsed?.error.isNullOrBlank()) return parsed!!.error!!
                } catch (_: Exception) {
                    // JSON이 아니면 아래에서 원문을 그대로 보여준다
                }
                if (body.length < 300) return body
            }
        } catch (_: Exception) {
            // 응답 본문을 못 읽으면 그냥 아래 기본 메시지로 넘어간다
        }
    }
    return message ?: default
}

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
    // 방금 만들어진 초안을 어느 AI가 만들었는지("gemini"|"claude"), 그리고 "클로드로 보완"
    // 요청 시 그대로 되돌려 보낼 원본 문서 구조 — 사진을 다시 첨부할 필요 없이 이 JSON만
    // 다시 보내면 되므로 서버 응답을 그대로 들고 있는다.
    val pipelineUsedProvider: String? = null,
    val pipelineDocStructure: com.google.gson.JsonElement? = null,
    val pipelineDocTypeUsed: String? = null,
    val refineInstruction: String = "",
    val isRefiningWithClaude: Boolean = false,
    val refineError: String? = null,

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
    val contactsSyncError: String? = null,

    // ── 가계부(장부): 영수증/통장내역 등을 사진으로 찍어 항목별로 누적 기록 ──
    val showLedger: Boolean = false,
    val ledgerPickedUris: List<Uri> = emptyList(),
    val ledgerInstruction: String = "",
    val isExtractingLedger: Boolean = false,
    val ledgerExtractError: String? = null,
    val ledgerDraftEntries: List<LedgerEntryDraft> = emptyList(), // 추출 후 사용자가 확인/수정 중인 항목들
    val ledgerTargetName: String = "", // 저장할 장부 이름 (기존 선택 또는 새로 입력)
    val isSavingLedger: Boolean = false,
    val ledgerSaveError: String? = null,
    val ledgerSaveMessage: String? = null,

    val ledgerList: List<LedgerSummary> = emptyList(),
    val isLoadingLedgerList: Boolean = false,
    val ledgerListError: String? = null,

    val selectedLedgerName: String? = null, // null이면 목록 화면, 값이 있으면 그 장부의 상세(월별 내역) 화면
    val ledgerGroups: List<LedgerGroup> = emptyList(),
    val ledgerBalance: Double = 0.0,
    val ledgerQuery: String = "",
    val isLoadingLedgerEntries: Boolean = false,
    val ledgerEntriesError: String? = null,

    // ── 캘린더 일정 등록: 달력/일정표 사진 → 일정 추출(확인) → 구글 캘린더 등록 ──
    val showCalendar: Boolean = false,
    val calendarPickedUris: List<Uri> = emptyList(),
    val calendarInstruction: String = "",
    val isExtractingEvents: Boolean = false,
    val calendarExtractError: String? = null,
    val calendarDraftEvents: List<CalendarEventDraft> = emptyList(),
    val isRegisteringEvents: Boolean = false,
    val calendarRegisterError: String? = null,
    val calendarRegisterMessage: String? = null
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

    /** 음성 인식으로 받은 텍스트를 지시사항에 반영한다. 이미 입력된 내용이 있으면 뒤에 이어붙인다
     *  (말한 내용을 통째로 덮어써서 기존에 적어둔 내용이 사라지지 않도록). */
    fun appendVoiceInstructionText(text: String) {
        val current = _uiState.value.instruction
        val merged = if (current.isBlank()) text else "$current $text"
        _uiState.value = _uiState.value.copy(instruction = merged)
    }
    fun setShowEvidence(checked: Boolean) { _uiState.value = _uiState.value.copy(showEvidence = checked) }

    fun analyze() {
        val repo = repository ?: return
        val state = _uiState.value
        // 사진/파일도 없고, 유튜브 링크도 없고, 지시사항 텍스트도 없으면 분석할 내용 자체가 없다.
        // (사진 없이 지시사항만으로도 요청할 수 있도록, 이 셋 중 하나만 있으면 진행한다)
        if (state.pickedUris.isEmpty() && state.youtubeUrl.isBlank() && state.instruction.isBlank()) {
            _uiState.value = state.copy(analyzeError = "사진/파일을 선택하거나, 유튜브 링크 또는 지시사항 중 하나는 입력해주세요")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, analyzeError = null, resultText = null)
            try {
                val res = if (state.pickedUris.isNotEmpty()) {
                    repo.analyzeImage(state.pickedUris, state.combinedInstruction)
                } else {
                    // 사진 없이 지시사항(+ 있다면 유튜브 링크)만으로 답변을 받는다.
                    // 유튜브 링크는 프롬프트 텍스트에 그냥 섞어 보내면 Claude가 "링크에 직접
                    // 접속할 수 없다"고 답하고 끝나버리므로, 반드시 별도 필드로 보내야 한다 —
                    // 서버가 그 링크의 자막을 먼저 가져와서 프롬프트에 텍스트로 붙여준다.
                    repo.research(
                        prompt = state.combinedInstruction.ifBlank { "위 영상 내용을 조사해서 알려줘" },
                        youtubeUrl = state.youtubeUrl.ifBlank { null }
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    resultText = res.text ?: "(응답 없음)",
                    citations = res.citations
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    analyzeError = "분석 실패: ${e.friendlyMessage()}"
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
        // 사진/파일/유튜브 링크가 하나도 없어도, 지시사항 텍스트만으로 문서를 만들 수 있다
        // (예: "이번 주 반도체 업황 조사해서 표로 정리해줘" — 서버가 웹서치로 내용을 채운다)
        if (state.pickedUris.isEmpty() && state.youtubeUrl.isBlank() && state.instruction.isBlank()) {
            _uiState.value = state.copy(pipelineError = "사진/파일을 선택하거나, 유튜브 링크 또는 지시사항 중 하나는 입력해주세요")
            return
        }
        // 받는 사람을 적었으면 이메일도 같이 보내고(both), 안 적었으면 결과 이미지 링크만 만든다(link)
        val outputMode = if (state.to.isBlank()) "link" else "both"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunningPipeline = true, pipelineError = null, pipelineMessage = null,
                resultImageFilenames = emptyList(), selectedResultImages = emptySet(),
                pipelineUsedProvider = null, pipelineDocStructure = null, pipelineDocTypeUsed = null,
                refineError = null
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
                    if (res.usedProvider == "gemini") append(" (Gemini 초안)")
                    else if (res.usedProvider == "claude") append(" (Claude)")
                    if (res.gmailMessageId != null) append(" · 메일 발송됨")
                    if (res.emailError != null) append(" (메일 발송 실패: ${res.emailError})")
                }
                _uiState.value = _uiState.value.copy(
                    isRunningPipeline = false,
                    pipelineDocTitle = res.title,
                    pipelineMessage = message,
                    resultImageFilenames = res.imageFilenames,
                    pipelineUsedProvider = res.usedProvider,
                    pipelineDocStructure = res.docStructure,
                    pipelineDocTypeUsed = res.docType
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningPipeline = false,
                    pipelineError = "요청 실패: ${e.friendlyMessage()}"
                )
            }
        }
    }

    fun setRefineInstruction(text: String) {
        _uiState.value = _uiState.value.copy(refineInstruction = text)
    }

    /**
     * 방금 만들어진 초안(Gemini 등 무료 AI가 만든 것)을 Claude에게 검토/보완시킨다.
     * 사진을 다시 첨부할 필요 없이, 이전 응답의 docStructure/docType을 그대로 서버에
     * 되돌려 보낸다. 자동으로는 절대 호출되지 않고, 사용자가 버튼을 눌렀을 때만 실행된다.
     */
    fun refineWithClaude() {
        val repo = repository ?: return
        val state = _uiState.value
        val draft = state.pipelineDocStructure ?: return
        val docType = state.pipelineDocTypeUsed ?: state.docType
        val outputMode = if (state.to.isBlank()) "link" else "both"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefiningWithClaude = true, refineError = null)
            try {
                val res = repo.refineWithClaude(
                    docStructure = draft,
                    docType = docType,
                    outputMode = outputMode,
                    to = state.to,
                    subject = state.subject,
                    instruction = state.refineInstruction
                )
                val message = buildString {
                    append("Claude 보완 완료")
                    if (res.gmailMessageId != null) append(" · 메일 발송됨")
                    if (res.emailError != null) append(" (메일 발송 실패: ${res.emailError})")
                }
                _uiState.value = _uiState.value.copy(
                    isRefiningWithClaude = false,
                    pipelineDocTitle = res.title,
                    pipelineMessage = message,
                    resultImageFilenames = res.imageFilenames,
                    pipelineUsedProvider = res.usedProvider,
                    pipelineDocStructure = res.docStructure,
                    pipelineDocTypeUsed = res.docType
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefiningWithClaude = false,
                    refineError = "보완 실패: ${e.friendlyMessage()}"
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
                    saveError = "저장 실패: ${e.friendlyMessage()}"
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
                    archiveError = "보관함을 불러오지 못했습니다: ${e.friendlyMessage()}"
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
                    contactsSyncError = e.friendlyMessage("동기화에 실패했습니다")
                )
            }
        }
    }

    // ══════════════════════ 가계부(장부) ══════════════════════

    fun openLedger() {
        _uiState.value = _uiState.value.copy(showLedger = true, selectedLedgerName = null)
        loadLedgerList()
    }

    fun closeLedger() {
        _uiState.value = _uiState.value.copy(
            showLedger = false, selectedLedgerName = null,
            ledgerDraftEntries = emptyList(), ledgerPickedUris = emptyList()
        )
    }

    fun loadLedgerList() {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLedgerList = true, ledgerListError = null)
            try {
                val list = repo.listLedgers()
                _uiState.value = _uiState.value.copy(isLoadingLedgerList = false, ledgerList = list)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingLedgerList = false,
                    ledgerListError = "장부 목록을 불러오지 못했습니다: ${e.friendlyMessage()}"
                )
            }
        }
    }

    fun openLedgerDetail(name: String) {
        _uiState.value = _uiState.value.copy(selectedLedgerName = name, ledgerQuery = "")
        loadLedgerEntries(name)
    }

    fun closeLedgerDetail() {
        _uiState.value = _uiState.value.copy(selectedLedgerName = null, ledgerGroups = emptyList())
    }

    fun setLedgerQuery(q: String) {
        _uiState.value = _uiState.value.copy(ledgerQuery = q)
    }

    fun searchLedgerEntries() {
        _uiState.value.selectedLedgerName?.let { loadLedgerEntries(it) }
    }

    fun loadLedgerEntries(name: String) {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLedgerEntries = true, ledgerEntriesError = null)
            try {
                val res = repo.getLedgerEntries(name, _uiState.value.ledgerQuery)
                _uiState.value = _uiState.value.copy(
                    isLoadingLedgerEntries = false,
                    ledgerGroups = res.groups,
                    ledgerBalance = res.balance
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingLedgerEntries = false,
                    ledgerEntriesError = "내역을 불러오지 못했습니다: ${e.friendlyMessage()}"
                )
            }
        }
    }

    fun deleteLedgerEntry(id: String) {
        val repo = repository ?: return
        val name = _uiState.value.selectedLedgerName ?: return
        viewModelScope.launch {
            try {
                repo.deleteLedgerEntry(name, id)
                loadLedgerEntries(name)
                loadLedgerList()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    ledgerEntriesError = "삭제 실패: ${e.friendlyMessage()}"
                )
            }
        }
    }

    // ── 새 항목 추가: 사진 선택 → 추출 → 확인/수정 → 저장 ──

    fun addLedgerPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val merged = (_uiState.value.ledgerPickedUris + uris).distinct()
        _uiState.value = _uiState.value.copy(ledgerPickedUris = merged, ledgerExtractError = null)
    }

    fun removeLedgerPickedUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(ledgerPickedUris = _uiState.value.ledgerPickedUris - uri)
    }

    fun clearLedgerPickedUris() {
        _uiState.value = _uiState.value.copy(ledgerPickedUris = emptyList())
    }

    fun setLedgerInstruction(text: String) {
        _uiState.value = _uiState.value.copy(ledgerInstruction = text)
    }

    fun setLedgerTargetName(text: String) {
        _uiState.value = _uiState.value.copy(ledgerTargetName = text, ledgerSaveError = null)
    }

    fun extractLedgerEntries() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.ledgerPickedUris.isEmpty()) {
            _uiState.value = state.copy(ledgerExtractError = "먼저 영수증/서류 사진을 선택해주세요")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExtractingLedger = true, ledgerExtractError = null, ledgerDraftEntries = emptyList()
            )
            try {
                val entries = repo.extractLedgerEntries(state.ledgerPickedUris, state.ledgerInstruction)
                _uiState.value = _uiState.value.copy(
                    isExtractingLedger = false,
                    ledgerDraftEntries = entries,
                    ledgerExtractError = if (entries.isEmpty()) "사진에서 항목을 찾지 못했습니다" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtractingLedger = false,
                    ledgerExtractError = "추출 실패: ${e.friendlyMessage()}"
                )
            }
        }
    }

    fun updateLedgerDraftEntry(index: Int, updated: LedgerEntryDraft) {
        val list = _uiState.value.ledgerDraftEntries.toMutableList()
        if (index in list.indices) {
            list[index] = updated
            _uiState.value = _uiState.value.copy(ledgerDraftEntries = list)
        }
    }

    fun removeLedgerDraftEntry(index: Int) {
        val list = _uiState.value.ledgerDraftEntries.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _uiState.value = _uiState.value.copy(ledgerDraftEntries = list)
        }
    }

    fun addBlankLedgerDraftEntry() {
        _uiState.value = _uiState.value.copy(
            ledgerDraftEntries = _uiState.value.ledgerDraftEntries + LedgerEntryDraft()
        )
    }

    fun confirmSaveLedgerEntries() {
        val repo = repository ?: return
        val state = _uiState.value
        val name = state.ledgerTargetName.trim()
        if (name.isEmpty()) {
            _uiState.value = state.copy(ledgerSaveError = "저장할 장부 이름을 입력해주세요")
            return
        }
        if (state.ledgerDraftEntries.isEmpty()) {
            _uiState.value = state.copy(ledgerSaveError = "저장할 항목이 없습니다")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingLedger = true, ledgerSaveError = null)
            try {
                val res = repo.saveLedgerEntries(name, state.ledgerDraftEntries)
                _uiState.value = _uiState.value.copy(
                    isSavingLedger = false,
                    ledgerSaveMessage = "${res.saved.size}건을 \"$name\" 장부에 저장했습니다",
                    ledgerDraftEntries = emptyList(),
                    ledgerPickedUris = emptyList(),
                    ledgerInstruction = "",
                    ledgerTargetName = ""
                )
                loadLedgerList()
                if (state.selectedLedgerName == name) loadLedgerEntries(name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingLedger = false,
                    ledgerSaveError = "저장 실패: ${e.friendlyMessage()}"
                )
            }
        }
    }

    fun dismissLedgerSaveMessage() {
        _uiState.value = _uiState.value.copy(ledgerSaveMessage = null)
    }

    // ══════════════════════ 캘린더 일정 등록 ══════════════════════

    fun openCalendar() {
        _uiState.value = _uiState.value.copy(showCalendar = true)
    }

    fun closeCalendar() {
        _uiState.value = _uiState.value.copy(
            showCalendar = false,
            calendarPickedUris = emptyList(),
            calendarDraftEvents = emptyList(),
            calendarInstruction = "",
            calendarExtractError = null,
            calendarRegisterError = null,
            calendarRegisterMessage = null
        )
    }

    fun addCalendarPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val merged = (_uiState.value.calendarPickedUris + uris).distinct()
        _uiState.value = _uiState.value.copy(calendarPickedUris = merged, calendarExtractError = null)
    }

    fun removeCalendarPickedUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(calendarPickedUris = _uiState.value.calendarPickedUris - uri)
    }

    fun clearCalendarPickedUris() {
        _uiState.value = _uiState.value.copy(calendarPickedUris = emptyList())
    }

    fun setCalendarInstruction(text: String) {
        _uiState.value = _uiState.value.copy(calendarInstruction = text)
    }

    fun extractCalendarEvents() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.calendarPickedUris.isEmpty()) {
            _uiState.value = state.copy(calendarExtractError = "먼저 달력/일정표 사진을 선택해주세요")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExtractingEvents = true, calendarExtractError = null, calendarDraftEvents = emptyList()
            )
            try {
                val events = repo.extractCalendarEvents(state.calendarPickedUris, state.calendarInstruction)
                _uiState.value = _uiState.value.copy(
                    isExtractingEvents = false,
                    calendarDraftEvents = events,
                    calendarExtractError = if (events.isEmpty()) "사진에서 일정을 찾지 못했습니다" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExtractingEvents = false,
                    calendarExtractError = "추출 실패: ${e.friendlyMessage()}"
                )
            }
        }
    }

    fun updateCalendarDraftEvent(index: Int, updated: CalendarEventDraft) {
        val list = _uiState.value.calendarDraftEvents.toMutableList()
        if (index in list.indices) {
            list[index] = updated
            _uiState.value = _uiState.value.copy(calendarDraftEvents = list)
        }
    }

    fun removeCalendarDraftEvent(index: Int) {
        val list = _uiState.value.calendarDraftEvents.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _uiState.value = _uiState.value.copy(calendarDraftEvents = list)
        }
    }

    fun addBlankCalendarDraftEvent() {
        _uiState.value = _uiState.value.copy(
            calendarDraftEvents = _uiState.value.calendarDraftEvents + CalendarEventDraft()
        )
    }

    fun confirmRegisterCalendarEvents() {
        val repo = repository ?: return
        val state = _uiState.value
        val invalid = state.calendarDraftEvents.any { it.title.isBlank() || it.date.isBlank() }
        if (state.calendarDraftEvents.isEmpty()) {
            _uiState.value = state.copy(calendarRegisterError = "등록할 일정이 없습니다")
            return
        }
        if (invalid) {
            _uiState.value = state.copy(calendarRegisterError = "모든 일정에 제목과 날짜를 입력해주세요")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegisteringEvents = true, calendarRegisterError = null)
            try {
                val res = repo.registerCalendarEvents(state.calendarDraftEvents)
                val message = buildString {
                    append("${res.registered.size}건 캘린더에 등록 완료")
                    if (res.failed.isNotEmpty()) append(" · ${res.failed.size}건 실패")
                }
                _uiState.value = _uiState.value.copy(
                    isRegisteringEvents = false,
                    calendarRegisterMessage = message,
                    // 실패한 일정만 남겨서 고친 뒤 다시 등록할 수 있게 한다 (전부 성공했으면 비움)
                    calendarDraftEvents = res.failed.mapNotNull { it.event },
                    calendarPickedUris = emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRegisteringEvents = false,
                    calendarRegisterError = "등록 실패: ${e.friendlyMessage()}"
                )
            }
        }
    }

    fun dismissCalendarRegisterMessage() {
        _uiState.value = _uiState.value.copy(calendarRegisterMessage = null)
    }
}
