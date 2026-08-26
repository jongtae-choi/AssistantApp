package com.jongtae.assistant.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jongtae.assistant.data.model.ArchiveGroup
import com.jongtae.assistant.ui.theme.AccentBlue2
import com.jongtae.assistant.ui.theme.AccentRose
import com.jongtae.assistant.ui.theme.DarkBg
import com.jongtae.assistant.ui.theme.DarkBorder
import com.jongtae.assistant.ui.theme.DarkBorder2
import com.jongtae.assistant.ui.theme.DarkCard
import com.jongtae.assistant.ui.theme.TxtPrimary
import com.jongtae.assistant.ui.theme.TxtSecondary
import com.jongtae.assistant.ui.theme.TxtTertiary

private val PERIODS = listOf("today" to "오늘", "week" to "이번 주", "month" to "이번 달", "all" to "전체")

@Composable
fun ArchiveScreen(viewModel: AssistantViewModel) {
    val ui by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── 검색 ──
        OutlinedTextField(
            value = ui.archiveQuery,
            onValueChange = { viewModel.setArchiveQuery(it) },
            placeholder = { Text("제목·지시사항으로 검색") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TxtSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.searchArchive() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue2,
                unfocusedBorderColor = DarkBorder2,
                focusedTextColor = TxtPrimary,
                unfocusedTextColor = TxtPrimary
            )
        )
        Spacer(Modifier.height(12.dp))

        // ── 기간 필터 ──
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PERIODS.forEach { (value, label) ->
                val selected = ui.archivePeriod == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) AccentBlue2 else DarkCard)
                        .border(1.dp, if (selected) AccentBlue2 else DarkBorder, RoundedCornerShape(999.dp))
                        .clickable { viewModel.setArchivePeriod(value) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) androidx.compose.ui.graphics.Color.White else TxtSecondary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        when {
            ui.isLoadingArchive -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = com.jongtae.assistant.ui.theme.AccentAmber)
            }
            ui.archiveError != null -> Text(ui.archiveError ?: "", color = AccentRose, fontSize = 12.sp)
            ui.archiveGroups.isEmpty() -> Text("저장된 이미지가 없습니다", color = TxtTertiary, fontSize = 12.sp)
            else -> ui.archiveGroups.forEach { group ->
                ArchiveGroupCard(group = group, viewModel = viewModel)
                Spacer(Modifier.height(20.dp))
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ArchiveGroupCard(group: ArchiveGroup, viewModel: AssistantViewModel) {
    Text(group.dateLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TxtSecondary)
    Spacer(Modifier.height(10.dp))

    // 저장 시각별로 다시 묶어서, 같은 시각에 저장된 것들을 카드 하나로 보여준다
    val bySavedAt = group.items.groupBy { it.savedAtLabel ?: "" }
    bySavedAt.forEach { (label, items) ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$label 저장", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TxtPrimary)
                Text("${items.size}장", fontSize = 11.sp, color = TxtTertiary)
            }
            Spacer(Modifier.height(10.dp))
            val urls = items.map { viewModel.archiveEntryUrl(it.url) }
            items.chunked(5).forEachIndexed { rowIdx, rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEachIndexed { colIdx, entry ->
                        val globalIndex = rowIdx * 5 + colIdx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF1E2A42))
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .clickable { viewModel.openViewer(urls, globalIndex) }
                        ) {
                            AsyncImage(
                                model = viewModel.archiveEntryUrl(entry.url),
                                contentDescription = entry.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                items.joinToString(" · ") { it.title },
                fontSize = 11.sp, color = TxtSecondary
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
