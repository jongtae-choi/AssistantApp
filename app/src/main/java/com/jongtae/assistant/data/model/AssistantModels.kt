package com.jongtae.assistant.data.model

// server.js(개인비서) 응답 구조를 그대로 반영

data class Citation(val url: String, val title: String? = null)

data class AnalyzeResponse(
    val text: String? = null,
    val citations: List<Citation> = emptyList()
)

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

// POST /api/pipeline/photo-to-document 응답
data class PhotoDocumentResponse(
    val ok: Boolean = false,
    val title: String? = null,
    val imageFilenames: List<String> = emptyList(),
    val gmailMessageId: String? = null,
    val emailError: String? = null
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
