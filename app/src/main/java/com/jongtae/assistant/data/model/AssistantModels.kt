package com.jongtae.assistant.data.model

import com.google.gson.JsonElement

// server.js(개인비서) 응답 구조를 그대로 반영

data class Citation(val url: String, val title: String? = null)

data class AnalyzeResponse(
    val text: String? = null,
    val citations: List<Citation> = emptyList()
)

// 사진 없이 순수 지시사항(+웹서치, +유튜브 자막)만으로 답변받을 때 사용 — 응답 형태는 AnalyzeResponse와 동일
// youtubeUrl을 함께 보내면 서버가 그 영상의 자막을 먼저 가져와서 프롬프트에 포함시켜준다
// (Claude는 유튜브 링크에 직접 접속해서 보는 기능이 없기 때문 — 자막 텍스트로 대신 전달해야 함)
data class ResearchRequest(val prompt: String, val youtubeUrl: String? = null)

data class PipelineAckResponse(
    val ok: Boolean = false,
    val status: String? = null,
    val message: String? = null
)

data class MakeFileResponse(
    val url: String? = null,
    val filename: String? = null
)

data class MakeImageResponse(
    val urls: List<String> = emptyList(),
    val filenames: List<String> = emptyList()
)

data class SendGmailResponse(
    val ok: Boolean = false,
    val gmailMessageId: String? = null
)

data class HealthResponse(
    val ok: Boolean = false,
    val time: String? = null
)

// POST /api/pipeline/photo-to-document, POST /api/pipeline/refine-with-claude 의 "즉시" 응답.
// 두 API 모두 시간이 오래 걸릴 수 있어(수십 초~수 분) 더 이상 결과를 바로 주지 않고,
// 요청을 접수했다는 jobId만 즉시 돌려준다. 실제 결과는 GET /pipeline/status/{jobId} 로
// 폴링해서 PipelineStatusResponse로 받아야 한다.
data class PipelineJobAckResponse(
    val ok: Boolean = false,
    val jobId: String? = null,
    val status: String? = null, // "processing"
    val message: String? = null
)

// GET /api/pipeline/status/{jobId} 응답.
// status: "processing"(아직 진행중) | "done"(완료 — 아래 필드들이 채워짐) | "error"(실패) | "not_found"(만료/서버재시작 등)
data class PipelineStatusResponse(
    val status: String? = null,
    val error: String? = null,
    val title: String? = null,
    val imageFilenames: List<String> = emptyList(),
    val gmailMessageId: String? = null,
    val emailError: String? = null,
    val usedProvider: String? = null, // "gemini" | "claude" — 이 초안을 어느 AI가 만들었는지
    val docStructure: JsonElement? = null, // 그대로 보관했다가 "클로드로 보완" 요청 시 되돌려 보낸다
    val docType: String? = null // "excel" | "pptx" — 마찬가지로 보완 요청 시 그대로 되돌려 보낸다
)

// POST /api/pipeline/refine-with-claude 요청 — photo-to-document 응답의 docStructure/docType을
// 그대로 담아 보내면, Claude가 검토/보완한 뒤 문서를 다시 만들어(+필요시 메일 발송) 돌려준다.
data class RefineWithClaudeRequest(
    val docStructure: JsonElement,
    val docType: String,
    val outputMode: String,
    val to: String? = null,
    val subject: String? = null,
    val filename: String? = null,
    val instruction: String? = null
)

// POST /api/outputs/suggest-titles 응답 — { "파일명.png": "제안된 제목" } 형태
data class SuggestTitlesResponse(
    val titles: Map<String, String> = emptyMap()
)

// 보관함(Archive) 한 건 — /api/outputs/save, /api/outputs/archive 공용
data class ArchiveEntry(
    val filename: String,
    val title: String,
    val savedAt: String? = null,
    val savedAtLabel: String? = null,
    val url: String? = null
)

data class SaveOutputsResponse(
    val ok: Boolean = false,
    val saved: List<ArchiveEntry> = emptyList()
)

data class ArchiveGroup(
    val date: String,
    val dateLabel: String,
    val items: List<ArchiveEntry> = emptyList()
)

data class ArchiveResponse(
    val groups: List<ArchiveGroup> = emptyList()
)

// POST /api/contacts/sync 응답
data class ContactsSyncResponse(
    val ok: Boolean = false,
    val ownerId: String? = null,
    val count: Int = 0
)

// ── JSON POST 요청 바디들 (멀티파트가 아닌 순수 JSON으로 보내는 API들) ──
data class SuggestTitlesRequest(val filenames: List<String>)

data class SaveOutputItem(val filename: String, val title: String)
data class SaveOutputsRequest(val items: List<SaveOutputItem>)

data class ContactsSyncRequest(val ownerId: String, val vcf: String)

data class ApiErrorBody(val error: String? = null)

// ══════════════════════ 가계부(장부) ══════════════════════
// 영수증/모임통장내역 등 서류를 사진으로 찍어 보내면 Claude가 항목을 뽑아주고(추출),
// 사용자가 확인한 뒤 이름 붙인 장부에 계속 누적해서 저장한다.

// 서버가 사진에서 뽑아낸(아직 저장 전) 초안 항목. 저장 요청 시에도 같은 형태로 보낸다.
data class LedgerEntryDraft(
    val date: String? = null,       // "YYYY-MM-DD" (모르면 null)
    val type: String = "expense",   // "expense"(지출) | "income"(수입)
    val amount: Double = 0.0,
    val description: String = "",
    val category: String = "",
    val counterparty: String = ""
)

data class LedgerExtractResponse(
    val entries: List<LedgerEntryDraft> = emptyList()
)

// 서버에 실제로 저장된 뒤의 항목 (id/savedAt/memo 포함)
data class LedgerEntry(
    val id: String = "",
    val date: String? = null,
    val type: String = "expense",
    val amount: Double = 0.0,
    val description: String = "",
    val category: String = "",
    val counterparty: String = "",
    val memo: String = "",
    val savedAt: String? = null
)

data class LedgerSaveRequest(val ledger: String, val entries: List<LedgerEntryDraft>)
data class LedgerSaveResponse(
    val ok: Boolean = false,
    val ledger: String? = null,
    val saved: List<LedgerEntry> = emptyList(),
    val count: Int = 0,
    val balance: Double = 0.0
)

data class LedgerSummary(
    val name: String,
    val count: Int = 0,
    val balance: Double = 0.0,
    val lastUpdatedAt: String? = null
)
data class LedgerListResponse(val ledgers: List<LedgerSummary> = emptyList())

data class LedgerGroup(
    val month: String,        // "YYYY-MM"
    val monthLabel: String,   // "2026년 8월"
    val items: List<LedgerEntry> = emptyList(),
    val subtotal: Double = 0.0
)
data class LedgerEntriesResponse(
    val ledger: String? = null,
    val balance: Double = 0.0,
    val count: Int = 0,
    val groups: List<LedgerGroup> = emptyList()
)

data class LedgerDeleteRequest(val ledger: String, val id: String)
data class LedgerDeleteResponse(val ok: Boolean = false, val count: Int = 0, val balance: Double = 0.0)

// ══════════════════════ 캘린더 일정 등록 ══════════════════════
// 달력/일정표 사진을 올리면 서버(Claude)가 일정 목록을 읽어주고(추출),
// 사용자가 확인/수정한 뒤 실제로 구글 캘린더에 등록한다.

data class CalendarEventDraft(
    val title: String = "",
    val date: String = "",          // "YYYY-MM-DD"
    val startTime: String? = null,  // "HH:MM" (null이면 종일 일정)
    val endTime: String? = null,
    val allDay: Boolean = false,
    val description: String = ""
)

data class ExtractEventsResponse(val events: List<CalendarEventDraft> = emptyList())

data class RegisterEventsRequest(val events: List<CalendarEventDraft>)

data class RegisteredEvent(
    val title: String = "",
    val date: String = "",
    val htmlLink: String? = null
)

data class FailedEvent(
    val event: CalendarEventDraft? = null,
    val error: String? = null
)

data class RegisterEventsResponse(
    val ok: Boolean = false,
    val registered: List<RegisteredEvent> = emptyList(),
    val failed: List<FailedEvent> = emptyList()
)
