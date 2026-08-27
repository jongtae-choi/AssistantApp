package com.jongtae.assistant.ui.main

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.jongtae.assistant.data.model.CalendarEventDraft
import com.jongtae.assistant.ui.theme.AccentAmber
import com.jongtae.assistant.ui.theme.AccentBlue2
import com.jongtae.assistant.ui.theme.AccentEmerald
import com.jongtae.assistant.ui.theme.AccentRose
import com.jongtae.assistant.ui.theme.DarkBg
import com.jongtae.assistant.ui.theme.DarkBorder
import com.jongtae.assistant.ui.theme.DarkBorder2
import com.jongtae.assistant.ui.theme.DarkCard
import com.jongtae.assistant.ui.theme.TxtPrimary
import com.jongtae.assistant.ui.theme.TxtSecondary
import com.jongtae.assistant.ui.theme.TxtTertiary

@Composable
fun CalendarScreen(
    viewModel: AssistantViewModel,
    onPickImages: () -> Unit,
    onTakePhoto: () -> Unit
) {
    val ui by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "달력·일정표·손글씨 메모 사진을 올리면 일정을 읽어서 구글 캘린더에 등록해드려요.",
            fontSize = 12.sp, color = TxtSecondary
        )
        Spacer(Modifier.height(16.dp))

        // ── 사진 선택 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("사진 선택", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TxtPrimary)
                if (ui.calendarPickedUris.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.calendarPickedUris.size}개 선택됨", fontSize = 12.sp, color = TxtSecondary)
                }
            }
            if (ui.calendarPickedUris.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.clearCalendarPickedUris() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose)
                ) { Text("전체 삭제", fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (ui.calendarPickedUris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("달력/일정표 사진을 선택해주세요", color = TxtTertiary, fontSize = 12.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.calendarPickedUris.forEach { uri ->
                    CalendarPhotoThumb(uri = uri, onRemove = { viewModel.removeCalendarPickedUri(uri) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onPickImages, modifier = Modifier.weight(1f)) { Text("사진 선택", fontSize = 12.sp) }
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("카메라 촬영", fontSize = 12.sp) }
        }

        // ── 지시사항 ──
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = ui.calendarInstruction,
            onValueChange = { viewModel.setCalendarInstruction(it) },
            placeholder = { Text("예: 이번 학기 학사일정만 읽어줘 (선택)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            colors = darkFieldColors()
        )

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { viewModel.extractCalendarEvents() },
            enabled = !ui.isExtractingEvents,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue2)
        ) {
            Text(if (ui.isExtractingEvents) "일정 읽는 중..." else "사진에서 일정 읽기")
        }

        if (ui.calendarExtractError != null) {
            Spacer(Modifier.height(8.dp))
            Text(ui.calendarExtractError ?: "", color = AccentRose, fontSize = 12.sp)
        }
        if (ui.isExtractingEvents) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentAmber)
            }
        }

        // ── 추출된 일정 확인/수정 ──
        if (ui.calendarDraftEvents.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DarkBorder))
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("추출된 일정 (${ui.calendarDraftEvents.size}건)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TxtPrimary)
                OutlinedButton(onClick = { viewModel.addBlankCalendarDraftEvent() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("직접 추가", fontSize = 11.sp)
                }
            }
            Text("틀린 부분은 직접 고친 뒤 등록해주세요.", fontSize = 10.sp, color = TxtTertiary)
            Spacer(Modifier.height(12.dp))

            ui.calendarDraftEvents.forEachIndexed { index, event ->
                CalendarEventEditorCard(
                    event = event,
                    onChange = { viewModel.updateCalendarDraftEvent(index, it) },
                    onDelete = { viewModel.removeCalendarDraftEvent(index) }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (ui.calendarRegisterError != null) {
                Text(ui.calendarRegisterError ?: "", color = AccentRose, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.confirmRegisterCalendarEvents() },
                enabled = !ui.isRegisteringEvents,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
            ) {
                Text(if (ui.isRegisteringEvents) "등록 중..." else "구글 캘린더에 등록")
            }
        }

        if (ui.calendarRegisterMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(ui.calendarRegisterMessage ?: "", color = AccentEmerald, fontSize = 12.sp)
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun CalendarPhotoThumb(uri: Uri, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
    ) {
        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(DarkBg)
                .border(1.dp, DarkBorder2, CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "삭제", tint = TxtSecondary, modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun CalendarEventEditorCard(
    event: CalendarEventDraft,
    onChange: (CalendarEventDraft) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = event.title,
                    onValueChange = { onChange(event.copy(title = it)) },
                    placeholder = { Text("일정 제목") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = darkFieldColors()
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, contentDescription = "삭제", tint = TxtTertiary)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = event.date,
                onValueChange = { onChange(event.copy(date = it)) },
                placeholder = { Text("날짜 (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors()
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("종일 일정", fontSize = 12.sp, color = TxtSecondary)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = event.allDay,
                    onCheckedChange = { onChange(event.copy(allDay = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue2)
                )
            }

            if (!event.allDay) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = event.startTime ?: "",
                        onValueChange = { onChange(event.copy(startTime = it)) },
                        placeholder = { Text("시작 (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = darkFieldColors()
                    )
                    OutlinedTextField(
                        value = event.endTime ?: "",
                        onValueChange = { onChange(event.copy(endTime = it)) },
                        placeholder = { Text("종료 (HH:MM, 선택)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = darkFieldColors()
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = event.description,
                onValueChange = { onChange(event.copy(description = it)) },
                placeholder = { Text("설명 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                colors = darkFieldColors()
            )
        }
    }
}
