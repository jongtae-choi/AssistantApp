package com.jongtae.assistant.ui.main

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jongtae.assistant.ui.theme.AccentAmber
import com.jongtae.assistant.ui.theme.AccentBlue
import com.jongtae.assistant.ui.theme.AccentBlue2
import com.jongtae.assistant.ui.theme.AccentRose
import com.jongtae.assistant.ui.theme.DarkBg
import com.jongtae.assistant.ui.theme.DarkBorder
import com.jongtae.assistant.ui.theme.DarkBorder2
import com.jongtae.assistant.ui.theme.DarkCard
import com.jongtae.assistant.ui.theme.DarkSurface
import com.jongtae.assistant.ui.theme.TxtPrimary
import com.jongtae.assistant.ui.theme.TxtSecondary
import com.jongtae.assistant.ui.theme.TxtTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantApp(
    viewModel: AssistantViewModel,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onRequestContactsPermission: () -> Unit
) {
    val ui by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TxtPrimary
                ),
                navigationIcon = {
                    if (ui.showArchive || showSettings) {
                        IconButton(onClick = {
                            if (ui.showArchive) viewModel.closeArchive() else showSettings = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = TxtSecondary)
                        }
                    }
                },
                title = {
                    when {
                        ui.showArchive -> Text("저장된 이미지 보관함", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TxtPrimary)
                        showSettings -> Text("서버 연결 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TxtPrimary)
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(8.dp), color = AccentBlue2, modifier = Modifier.size(30.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("P", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("개인비서", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TxtPrimary)
                        }
                    }
                },
                actions = {
                    if (ui.isConfigured && !showSettings && !ui.showArchive) {
                        IconButton(onClick = { viewModel.openArchive() }) {
                            Icon(Icons.Default.Folder, contentDescription = "보관함", tint = TxtSecondary)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "설정", tint = TxtSecondary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DarkBg)
        ) {
            when {
                showSettings || !ui.isConfigured -> SettingsForm(
                    initialBaseUrl = ui.baseUrl,
                    initialToken = ui.token,
                    initialEmail = ui.defaultEmail,
                    onSave = { url, token, email ->
                        viewModel.saveSettings(url, token, email)
                        showSettings = false
                    },
                    autoSyncContacts = ui.autoSyncContacts,
                    hasContactsPermission = ui.hasContactsPermission,
                    lastSyncAt = ui.lastSyncAt,
                    lastSyncCount = ui.lastSyncCount,
                    isSyncingContacts = ui.isSyncingContacts,
                    contactsSyncError = ui.contactsSyncError,
                    onToggleAutoSync = { enabled ->
                        if (enabled && !ui.hasContactsPermission) onRequestContactsPermission()
                        viewModel.setAutoSyncContacts(enabled)
                    },
                    onSyncNow = {
                        if (!ui.hasContactsPermission) onRequestContactsPermission() else viewModel.syncContactsNow()
                    }
                )
                ui.showArchive -> ArchiveScreen(viewModel = viewModel)
                else -> MainContent(
                    viewModel = viewModel,
                    onPickImages = onPickImages,
                    onPickFiles = onPickFiles,
                    onTakePhoto = onTakePhoto
                )
            }

            if (ui.viewerVisible) {
                ImageViewerOverlay(urls = ui.viewerUrls, startIndex = ui.viewerStartIndex, onClose = { viewModel.closeViewer() })
            }
        }
    }

    if (ui.showSaveDialog) {
        SaveTitlesSheet(viewModel = viewModel)
    }
}

@Composable
private fun MainContent(
    viewModel: AssistantViewModel,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onTakePhoto: () -> Unit
) {
    val ui by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── 1. 사진/파일 선택 (다중) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("1. 사진/파일 선택", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TxtPrimary)
                if (ui.pickedUris.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.pickedUris.size}개 선택됨", fontSize = 12.sp, color = TxtSecondary)
                }
            }
            if (ui.pickedUris.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.clearPickedUris() },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = AccentRose)
                ) { Text("전체 삭제", fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (ui.pickedUris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("사진/파일을 선택해주세요", color = TxtTertiary, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.pickedUris.forEach { uri ->
                    PickedFileThumb(uri = uri, onRemove = { viewModel.removePickedUri(uri) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onPickImages, modifier = Modifier.weight(1f)) { Text("사진 선택", fontSize = 12.sp) }
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("카메라 촬영", fontSize = 12.sp) }
            OutlinedButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) { Text("PDF/파일", fontSize = 12.sp) }
        }

        // ── 2. 지시사항 ──
        Spacer(Modifier.height(24.dp))
        Text("2. 지시사항 (선택)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TxtPrimary)
        Spacer(Modifier.height(10.dp))

        Text("유튜브 링크 (선택)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TxtSecondary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = ui.youtubeUrl,
            onValueChange = { viewModel.setYoutubeUrl(it) },
            placeholder = { Text("https://youtube.com/watch?v=...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkFieldColors()
        )
        Spacer(Modifier.height(4.dp))
        Text("영상 자막을 함께 참고해서 분석·문서화해요", fontSize = 10.sp, color = TxtTertiary)

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ui.instruction,
            onValueChange = { viewModel.setInstruction(it) },
            placeholder = { Text("예: 이 영수증들 항목별로 정리해서 조사해줘") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            colors = darkFieldColors()
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            androidx.compose.material3.Checkbox(
                checked = ui.showEvidence,
                onCheckedChange = { viewModel.setShowEvidence(it) },
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = AccentBlue2)
            )
            Column(Modifier.padding(top = 12.dp)) {
                Text("근거표시", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TxtPrimary)
                Text(
                    "체크하면 지시사항 뒤에 \"근거표시\"를 붙여서 함께 전달해요. 답변에 근거(출처·참고자료)를 표시하도록 요청합니다.",
                    fontSize = 10.sp, color = TxtTertiary
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x4D0B3D5E))
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text("CLAUDE API 전송 내용", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.jongtae.assistant.ui.theme.BadgeTxt)
            Spacer(Modifier.height(3.dp))
            if (ui.youtubeUrl.isNotBlank()) {
                Text("유튜브: ${ui.youtubeUrl}", fontSize = 11.sp, color = TxtSecondary)
            }
            Text(ui.combinedInstruction.ifBlank { "(지시사항 없음)" }, fontSize = 11.sp, color = TxtPrimary)
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { viewModel.analyze() },
            enabled = !ui.isAnalyzing,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AccentBlue2)
        ) {
            Text(if (ui.isAnalyzing) "분석 중..." else "분석하기")
        }

        if (ui.analyzeError != null) {
            Spacer(Modifier.height(8.dp))
            Text(ui.analyzeError ?: "", color = AccentRose, fontSize = 12.sp)
        }
        if (ui.isAnalyzing) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = AccentAmber)
        }
        if (ui.resultText != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(ui.resultText ?: "", fontSize = 13.sp, color = TxtPrimary)
                    if (ui.citations.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(DarkBorder))
                        Spacer(Modifier.height(10.dp))
                        Text("참고 링크", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TxtSecondary)
                        ui.citations.forEach { c ->
                            Text("· ${c.title ?: c.url}", fontSize = 11.sp, color = TxtSecondary)
                        }
                    }
                }
            }
        }

        // ── 3. 문서로 정리해서 메일로 받기 ──
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(DarkBorder))
        Spacer(Modifier.height(20.dp))
        Text("3. 문서로 정리해서 메일로 받기", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TxtPrimary)

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.docType == "excel", onClick = { viewModel.setDocType("excel") }, label = { Text("엑셀") })
            FilterChip(selected = ui.docType == "pptx", onClick = { viewModel.setDocType("pptx") }, label = { Text("PPT") })
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x402A3A55))
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Contacts, contentDescription = null, tint = TxtSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("주소록 정보 활용", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TxtPrimary)
                    Text("받는 사람을 연락처에서 자동으로 찾아요", fontSize = 10.sp, color = TxtTertiary)
                }
            }
            androidx.compose.material3.Switch(
                checked = ui.matchContacts,
                onCheckedChange = { viewModel.setMatchContacts(it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = AccentBlue2)
            )
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = ui.to,
            onValueChange = { viewModel.setTo(it) },
            label = { Text("받는 사람 이메일") },
            placeholder = { Text("비워두면 결과 이미지 링크만 생성돼요") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkFieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ui.subject,
            onValueChange = { viewModel.setSubject(it) },
            label = { Text("메일 제목") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkFieldColors()
        )

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { viewModel.runPipeline() },
            enabled = !ui.isRunningPipeline,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AccentBlue2)
        ) {
            Text(if (ui.isRunningPipeline) "생성 중..." else "문서 생성 + 메일 발송")
        }
        if (ui.pipelineError != null) {
            Spacer(Modifier.height(8.dp))
            Text(ui.pipelineError ?: "", color = AccentRose, fontSize = 12.sp)
        }
        if (ui.pipelineMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(ui.pipelineMessage ?: "", color = com.jongtae.assistant.ui.theme.AccentEmerald, fontSize = 12.sp)
        }

        // ── 생성된 이미지 결과 ──
        if (ui.resultImageFilenames.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DarkBorder))
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("생성된 이미지", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TxtPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.resultImageFilenames.size}장", fontSize = 12.sp, color = TxtSecondary)
                }
                OutlinedButton(onClick = { viewModel.selectAllResultImages() }) { Text("전체 선택", fontSize = 11.sp) }
            }
            Spacer(Modifier.height(12.dp))

            val urls = ui.resultImageFilenames.map { viewModel.outputImageUrl(it) }
            ui.resultImageFilenames.chunked(4).forEach { rowFiles ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowFiles.forEach { filename ->
                        val index = ui.resultImageFilenames.indexOf(filename)
                        val selected = filename in ui.selectedResultImages
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkCard)
                                .border(if (selected) 1.5.dp else 1.dp, if (selected) AccentBlue else DarkBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.openViewer(urls, index) }
                        ) {
                            AsyncImage(
                                model = viewModel.outputImageUrl(filename),
                                contentDescription = filename,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(5.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) AccentBlue else Color(0x800A0F1E))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .clickable { viewModel.toggleResultImageSelection(filename) }
                            )
                        }
                    }
                    // 마지막 줄이 4개가 안 채워졌을 때 빈 칸으로 정렬 맞추기
                    repeat(4 - rowFiles.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "이미지를 누르면 원본 크기로 볼 수 있고, 체크하면 여러 장을 골라 서버에 저장할 수 있어요.",
                fontSize = 11.sp, color = TxtTertiary
            )

            if (ui.selectedResultImages.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(com.jongtae.assistant.ui.theme.DarkCard2)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${ui.selectedResultImages.size}장 선택됨", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TxtPrimary)
                    Button(
                        onClick = { viewModel.openSaveDialog() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AccentBlue2)
                    ) { Text("서버에 저장", fontSize = 12.sp) }
                }
            }
            if (ui.lastSavedMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(ui.lastSavedMessage ?: "", color = com.jongtae.assistant.ui.theme.AccentEmerald, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x382A3A55))
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .clickable { viewModel.openArchive() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = com.jongtae.assistant.ui.theme.BadgeTxt, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("저장된 이미지 보관함", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TxtPrimary)
                }
                Text("날짜별로 조회 →", fontSize = 11.sp, color = TxtTertiary)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PickedFileThumb(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val mime = remember(uri) { context.contentResolver.getType(uri) }
    val isImage = mime?.startsWith("image/") == true

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
    ) {
        if (isImage) {
            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Description, contentDescription = null, tint = TxtTertiary, modifier = Modifier.size(28.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(DarkSurface)
                .border(1.dp, DarkBorder2, CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "삭제", tint = TxtSecondary, modifier = Modifier.size(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveTitlesSheet(viewModel: AssistantViewModel) {
    val ui by viewModel.uiState.collectAsState()
    ModalBottomSheet(onDismissRequest = { viewModel.dismissSaveDialog() }, containerColor = DarkSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text("저장할 이미지 제목 확인", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TxtPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "이미지 내용을 분석해 제목을 자동으로 만들었어요. 파일명으로 저장되니 필요하면 직접 수정하세요.",
                fontSize = 11.sp, color = TxtSecondary
            )
            Spacer(Modifier.height(16.dp))

            if (ui.isSuggestingTitles) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentAmber)
                }
            } else {
                ui.selectedResultImages.toList().forEach { filename ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(DarkCard)
                                .border(1.dp, DarkBorder, RoundedCornerShape(9.dp))
                        ) {
                            AsyncImage(
                                model = viewModel.outputImageUrl(filename),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = ui.draftTitles[filename] ?: "",
                            onValueChange = { viewModel.updateDraftTitle(filename, it) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = darkFieldColors()
                        )
                    }
                }
            }

            if (ui.saveError != null) {
                Text(ui.saveError ?: "", color = AccentRose, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.dismissSaveDialog() }, modifier = Modifier.weight(1f)) { Text("취소") }
                Button(
                    onClick = { viewModel.confirmSaveSelected() },
                    enabled = !ui.isSavingOutputs && !ui.isSuggestingTitles,
                    modifier = Modifier.weight(2f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AccentBlue2)
                ) {
                    Text(if (ui.isSavingOutputs) "저장 중..." else "${ui.selectedResultImages.size}장 저장")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageViewerOverlay(urls: List<String>, startIndex: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                    }
                    Text("${pagerState.currentPage + 1} / ${urls.size}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.size(40.dp))
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                    AsyncImage(
                        model = urls.getOrNull(page),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(urls.size) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (active) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (active) AccentBlue else Color.White.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentAmber,
    unfocusedBorderColor = DarkBorder2,
    focusedLabelColor = AccentAmber,
    focusedTextColor = TxtPrimary,
    unfocusedTextColor = TxtPrimary,
    cursorColor = AccentAmber
)
