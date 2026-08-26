package com.jongtae.assistant.data.network

import com.jongtae.assistant.data.model.AnalyzeResponse
import com.jongtae.assistant.data.model.ArchiveResponse
import com.jongtae.assistant.data.model.ContactsSyncRequest
import com.jongtae.assistant.data.model.ContactsSyncResponse
import com.jongtae.assistant.data.model.HealthResponse
import com.jongtae.assistant.data.model.PhotoDocumentResponse
import com.jongtae.assistant.data.model.PipelineAckResponse
import com.jongtae.assistant.data.model.SaveOutputsRequest
import com.jongtae.assistant.data.model.SaveOutputsResponse
import com.jongtae.assistant.data.model.SuggestTitlesRequest
import com.jongtae.assistant.data.model.SuggestTitlesResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

// server.js(개인비서) 의 /assistant/api/... 엔드포인트와 매칭.
// multer(upload.any())가 텍스트 필드는 req.body에, 파일은 req.files에 자동으로 담아주므로
// 별도의 payload JSON 래핑 없이 순수 multipart 필드로 보내면 된다.
interface AssistantApiService {

    // 사진/파일 여러 장을 한 번에 분석(빠른 조사 — 문서 생성 없이 텍스트 답변만 받는다)
    @Multipart
    @POST("assistant/api/analyze-image")
    suspend fun analyzeImage(
        @Part files: List<MultipartBody.Part>,
        @Part("instruction") instruction: RequestBody
    ): AnalyzeResponse

    @Multipart
    @POST("assistant/api/pipeline/photo-to-gmail")
    suspend fun pipelinePhotoToGmail(
        @Part file: MultipartBody.Part,
        @Part("instruction") instruction: RequestBody,
        @Part("to") to: RequestBody,
        @Part("subject") subject: RequestBody,
        @Part("docType") docType: RequestBody
    ): PipelineAckResponse

    // 사진 여러 장(+ 유튜브 링크, 주소록 매칭 등)을 문서로 정리 — 결과 이미지 여러 장을 동기 응답으로 받는다.
    // photos는 0장이어도 되지만(유튜브 링크만으로도 가능), Retrofit이 빈 리스트를 보내면 파트가 아예
    // 없는 요청이 되므로 서버 쪽 "사진, 텍스트/코드/docx 파일, 유튜브 링크 중 최소 하나" 검증과 맞는다.
    // matchContacts는 값이 있을 때만(=true일 때만) 파트를 실어 보낸다 — 문자열 "false"를 그대로 보내면
    // 서버의 `if (matchContacts)` 체크가 "빈 문자열이 아니면 true"로 판정해버리는 문제를 피하기 위함.
    @Multipart
    @POST("assistant/api/pipeline/photo-to-document")
    suspend fun pipelinePhotoToDocument(
        @Part photos: List<MultipartBody.Part>,
        @Part("instruction") instruction: RequestBody?,
        @Part("docType") docType: RequestBody,
        @Part("outputMode") outputMode: RequestBody,
        @Part("to") to: RequestBody?,
        @Part("subject") subject: RequestBody?,
        @Part("youtubeUrl") youtubeUrl: RequestBody?,
        @Part("matchContacts") matchContacts: RequestBody?
    ): PhotoDocumentResponse

    // 결과 이미지 내용을 보고 제목(파일명)을 자동 제안
    @POST("assistant/api/outputs/suggest-titles")
    suspend fun suggestTitles(@Body body: SuggestTitlesRequest): SuggestTitlesResponse

    // 선택+제목 확정된 이미지를 보관함에 저장
    @POST("assistant/api/outputs/save")
    suspend fun saveOutputs(@Body body: SaveOutputsRequest): SaveOutputsResponse

    // 보관함 조회 — period: today|week|month|all, from/to: YYYY-MM-DD, q: 검색어
    @GET("assistant/api/outputs/archive")
    suspend fun getArchive(
        @Query("period") period: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("q") q: String? = null
    ): ArchiveResponse

    // 폰 연락처(vCard 텍스트)를 서버 주소록으로 동기화
    @POST("assistant/api/contacts/sync")
    suspend fun syncContacts(@Body body: ContactsSyncRequest): ContactsSyncResponse

    @GET("assistant/api/health")
    suspend fun health(): HealthResponse
}
