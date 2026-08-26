package com.jongtae.assistant.data.contacts

import android.content.Context
import android.provider.ContactsContract

/**
 * 기기 연락처(이름 + 전화번호가 있는 것만)를 서버 /api/contacts/sync가 기대하는
 * vCard(.vcf) 텍스트로 변환한다. READ_CONTACTS 권한이 이미 허용된 상태에서만 호출해야 한다.
 */
object ContactsVCardBuilder {

    /** 반환값: (vCard 전체 텍스트, 포함된 사람 수) */
    fun buildFromDevice(context: Context): Pair<String, Int> {
        val phonesByContact = LinkedHashMap<String, MutableList<String>>()
        val nameByContact = HashMap<String, String>()

        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val number = cursor.getString(numberIdx)?.trim().orEmpty()
                if (name.isEmpty() || number.isEmpty()) continue
                nameByContact[id] = name
                phonesByContact.getOrPut(id) { mutableListOf() }.add(number)
            }
        }

        val sb = StringBuilder()
        var count = 0
        for ((id, phones) in phonesByContact) {
            val name = nameByContact[id] ?: continue
            if (phones.isEmpty()) continue
            sb.append("BEGIN:VCARD\n")
            sb.append("VERSION:3.0\n")
            sb.append("FN:").append(name).append('\n')
            for (p in phones.distinct()) {
                sb.append("TEL:").append(p).append('\n')
            }
            sb.append("END:VCARD\n")
            count++
        }
        return sb.toString() to count
    }
}
