package com.jongtae.assistant.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.jongtae.assistant.data.model.AnalyzeResponse
import com.jongtae.assistant.data.model.ArchiveGroup
import com.jongtae.assistant.data.model.ArchiveEntry
import com.jongtae.assistant.data.model.CalendarEventDraft
import com.jongtae.assistant.data.model.ContactsSyncRequest
import com.jongtae.assistant.data.model.LedgerDeleteRequest
import com.jongtae.assistant.data.model.LedgerDeleteResponse
import com.jongtae.assistant.data.model.LedgerEntriesResponse
import com.jongtae.assistant.data.model.LedgerEntryDraft
import com.jongtae.assistant.data.model.LedgerSaveRequest
import com.jongtae.assistant.data.model.LedgerSaveResponse
import com.jongtae.assistant.data.model.LedgerSummary
import com.jongtae.assistant.data.model.PhotoDocumentResponse
import com.jongtae.assistant.data.model.PipelineAckResponse
import com.jongtae.assistant.data.model.RefineWithClaudeRequest
import com.jongtae.assistant.data.model.RegisterEventsRequest
import com.jongtae.assistant.data.model.RegisterEventsResponse
import com.jongtae.assistant.data.model.ResearchRequest
import com.jongtae.assistant.data.model.SaveOutputItem
import com.jongtae.assistant.data.model.SaveOutputsRequest
import com.jongtae.assistant.data.model.SuggestTitlesRequest
import com.jongtae.assistant.data.network.AssistantApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AssistantRepository(
    private val context: Context,
    private val api: AssistantApiService
) {
    private val textMediaType = "text/plain".toMediaTypeOrNull()

    private fun text(value: String): RequestBody = value.toRequestBody(textMediaType)

    /** 값이 비어있으면 아예 파트를 만들지 않는다 (서버의 "필드가 존재하면 true" 식 falsy 체크를 피하기 위함) */
    private fun textOrNull(value: String?): RequestBody? =
        if (value.isNullOrBlank()) null else text(value)

    /**
     * content:// Uri를 앱 캐시 폴더로 복사한 뒤 MultipartBody.Part로 만든다.
     * (스트림을 바로 RequestBody로 넘길 수도 있지만, 파일로 한 번 떨어뜨려두면 재시도/디버깅이 쉬움)
     */
    private fun uriToPart(uri: Uri, partName: String = "photo"): MultipartBody.Part {
        val resolver: ContentResolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: guessMimeFromUri(uri) ?: "application/octet-stream"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"

        val cacheDir = File(context.cacheDir, "uploads").apply { mkdirs() }
        val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}.$ext")

        resolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("파일을 읽을 수 없습니다: $uri")

        val body = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, tempFile.name, body)
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    /** 여러 장을 한 번에 전송할 때 사용. 필드명은 전부 "photo"로 통일 — 서버(multer)는
     *  필드명과 무관하게 mimetype으로 이미지/PDF/텍스트/docx를 알아서 분류한다. */
    private fun urisToParts(uris: List<Uri>): List<MultipartBody.Part> =
        uris.map { uriToPart(it, partName = "photo") }

    suspend fun analyzeImage(uris: List<Uri>, instruction: String): AnalyzeResponse {
        val parts = urisToParts(uris)
        return api.analyzeImage(parts, text(instruction))
    }

    /** 사진 없이 순수 지시사항(+웹서치, +유튜브 자막)만으로 답변을 받는다. */
    suspend fun research(prompt: String, youtubeUrl: String? = null): AnalyzeResponse =
        api.research(ResearchRequest(prompt, youtubeUrl))

    suspend fun pipelinePhotoToGmail(
        uri: Uri,
        instruction: String,
        to: String,
        subject: String,
        docType: String
    ): PipelineAckResponse {
        val part = uriToPart(uri)
        return api.pipelinePhotoToGmail(
            file = part,
            instruction = text(instruction),
            to = text(to),
            subject = text(subject),
            docType = text(docType)
        )
    }

    /**
     * 사진/파일 여러 장(+ 유튜브 링크, 주소록 매칭 등)을 문서로 정리한다.
     * outputMode: "link" | "email" | "both" — link/both일 때 imageFilenames로 결과 이미지가 온다.
     * matchContacts는 켜져 있을 때만 파트를 실어 보낸다(꺼져 있으면 아예 필드 자체를 안 보냄).
     */
    suspend fun pipelinePhotoToDocument(
        uris: List<Uri>,
        instruction: String,
        docType: String,
        outputMode: String,
        to: String,
        subject: String,
        youtubeUrl: String,
        matchContacts: Boolean
    ): PhotoDocumentResponse {
        val parts = urisToParts(uris)
        return api.pipelinePhotoToDocument(
            photos = parts,
            instruction = textOrNull(instruction),
            docType = text(docType),
            outputMode = text(outputMode),
            to = textOrNull(to),
            subject = textOrNull(subject),
            youtubeUrl = textOrNull(youtubeUrl),
            matchContacts = if (matchContacts) text("true") else null
        )
    }

    /**
     * 무료 AI가 만든 문서 초안(docStructure)을 Claude로 검토/보완해서 다시 만든다.
     * pipelinePhotoToDocument()의 응답에서 받은 docStructure/docType을 그대로 넘기면 된다.
     */
    suspend fun refineWithClaude(
        docStructure: com.google.gson.JsonElement,
        docType: String,
        outputMode: String,
        to: String,
        subject: String,
        instruction: String
    ): PhotoDocumentResponse = api.refineWithClaude(
        RefineWithClaudeRequest(
            docStructure = docStructure,
            docType = docType,
            outputMode = outputMode,
            to = to.ifBlank { null },
            subject = subject.ifBlank { null },
            instruction = instruction.ifBlank { null }
        )
    )

    /** 결과 이미지들의 내용을 보고 파일명으로 쓸 제목을 자동 제안받는다. */
    suspend fun suggestTitles(filenames: List<String>): Map<String, String> {
        if (filenames.isEmpty()) return emptyMap()
        return api.suggestTitles(SuggestTitlesRequest(filenames)).titles
    }

    /** 선택 + 제목 확정된 결과 이미지들을 서버 보관함에 저장한다. */
    suspend fun saveOutputs(items: List<Pair<String, String>>): List<ArchiveEntry> {
        val body = SaveOutputsRequest(items.map { (filename, title) -> SaveOutputItem(filename, title) })
        return api.saveOutputs(body).saved
    }

    /** 보관함을 기간/검색어로 조회한다. period: "today"|"week"|"month"|"all" */
    suspend fun getArchive(period: String?, from: String? = null, to: String? = null, q: String? = null): List<ArchiveGroup> =
        api.getArchive(period = period, from = from, to = to, q = q).groups

    /** 폰 연락처(vCard 텍스트)를 서버 주소록으로 동기화한다. */
    suspend fun syncContacts(ownerId: String, vcf: String): Pair<Boolean, Int> {
        val res = api.syncContacts(ContactsSyncRequest(ownerId = ownerId, vcf = vcf))
        return res.ok to res.count
    }

    // ── 가계부(장부) ──

    /** 영수증/서류 사진에서 거래 항목 초안을 뽑아낸다 (아직 저장 안 됨). */
    suspend fun extractLedgerEntries(uris: List<Uri>, instruction: String): List<LedgerEntryDraft> {
        val parts = urisToParts(uris)
        return api.extractLedgerEntries(parts, textOrNull(instruction)).entries
    }

    /** 확인/수정된 항목들을 이름 붙인 장부에 실제로 저장(누적)한다. */
    suspend fun saveLedgerEntries(ledgerName: String, entries: List<LedgerEntryDraft>): LedgerSaveResponse =
        api.saveLedgerEntries(LedgerSaveRequest(ledgerName, entries))

    /** 지금까지 등록된 장부 목록(이름/건수/잔액)을 조회한다. */
    suspend fun listLedgers(): List<LedgerSummary> = api.listLedgers().ledgers

    /** 특정 장부의 내역을 월별로 조회한다. */
    suspend fun getLedgerEntries(ledgerName: String, query: String? = null): LedgerEntriesResponse =
        api.getLedgerEntries(ledgerName, query?.ifBlank { null })

    /** 잘못 저장한 항목 하나를 삭제한다. */
    suspend fun deleteLedgerEntry(ledgerName: String, id: String): LedgerDeleteResponse =
        api.deleteLedgerEntry(LedgerDeleteRequest(ledgerName, id))

    // ── 캘린더 일정 등록 ──

    /** 달력/일정표 사진에서 일정 목록을 뽑아낸다 (아직 등록 안 됨). */
    suspend fun extractCalendarEvents(uris: List<Uri>, instruction: String): List<CalendarEventDraft> {
        val parts = urisToParts(uris)
        return api.extractEvents(parts, textOrNull(instruction)).events
    }

    /** 확인/수정된 일정 목록을 실제로 구글 캘린더에 등록한다. */
    suspend fun registerCalendarEvents(events: List<CalendarEventDraft>): RegisterEventsResponse =
        api.registerEvents(RegisterEventsRequest(events))
}
