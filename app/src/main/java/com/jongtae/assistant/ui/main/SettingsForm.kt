package com.jongtae.assistant.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jongtae.assistant.ui.theme.AccentAmber
import com.jongtae.assistant.ui.theme.AccentBlue2
import com.jongtae.assistant.ui.theme.AccentEmerald
import com.jongtae.assistant.ui.theme.AccentRose
import com.jongtae.assistant.ui.theme.DarkBg
import com.jongtae.assistant.ui.theme.DarkBorder
import com.jongtae.assistant.ui.theme.DarkCard
import com.jongtae.assistant.ui.theme.TxtPrimary
import com.jongtae.assistant.ui.theme.TxtSecondary
import com.jongtae.assistant.ui.theme.TxtTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsForm(
    initialBaseUrl: String,
    initialToken: String,
    initialEmail: String,
    onSave: (baseUrl: String, token: String, defaultEmail: String) -> Unit,
    autoSyncContacts: Boolean,
    hasContactsPermission: Boolean,
    lastSyncAt: Long,
    lastSyncCount: Int,
    isSyncingContacts: Boolean,
    contactsSyncError: String?,
    onToggleAutoSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    var baseUrl by remember(initialBaseUrl) { mutableStateOf(initialBaseUrl) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("서버 연결", style = MaterialTheme.typography.titleMedium, color = TxtPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "server.js(개인비서) 가 실행 중인 주소와, .env의 MY_SECRET_TOKEN과 동일한 인증 토큰을 입력하세요.\n" +
                "예: https://xxxx.ngrok-free.app",
            fontSize = 12.sp,
            color = TxtTertiary
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("서버 주소 (Base URL)") },
            placeholder = { Text("https://xxxx.ngrok-free.app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkFieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("인증 토큰 (X-My-Token)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkFieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("기본 수신 이메일 (선택)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkFieldColors()
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onSave(baseUrl, token, email) },
            enabled = baseUrl.isNotBlank() && token.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue2),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("저장하고 연결")
        }

        Spacer(Modifier.height(28.dp))

        // ── 연락처 자동 동기화 ──
        Text("연락처 동기화", style = MaterialTheme.typography.titleMedium, color = TxtPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "내 폰의 연락처를 서버에 업로드해, 문서 작성 시 이름으로 전화번호를 자동으로 찾아 활용합니다.",
            fontSize = 12.sp,
            color = TxtTertiary
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("자동 동기화", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TxtPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "앱을 사용하는 동안과 15분마다 백그라운드에서 자동 반영됩니다",
                        fontSize = 11.sp,
                        color = TxtTertiary
                    )
                }
                Switch(
                    checked = autoSyncContacts,
                    onCheckedChange = { onToggleAutoSync(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentEmerald,
                        uncheckedThumbColor = TxtTertiary,
                        uncheckedTrackColor = DarkBorder
                    )
                )
            }

            if (!hasContactsPermission) {
                Spacer(Modifier.height(10.dp))
                Text("연락처 접근 권한이 아직 허용되지 않았습니다", fontSize = 11.sp, color = AccentAmber)
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (lastSyncAt > 0L) {
                            val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                            "마지막 동기화: ${fmt.format(Date(lastSyncAt))} · ${lastSyncCount}명"
                        } else {
                            "아직 동기화한 적이 없습니다"
                        },
                        fontSize = 11.5.sp,
                        color = TxtSecondary
                    )
                    if (contactsSyncError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(contactsSyncError, fontSize = 11.sp, color = AccentRose)
                    }
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onSyncNow,
                    enabled = !isSyncingContacts
                ) {
                    if (isSyncingContacts) {
                        CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp, color = AccentBlue2)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("지금 동기화", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}
