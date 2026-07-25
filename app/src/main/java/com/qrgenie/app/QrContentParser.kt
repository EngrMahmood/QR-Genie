package com.qrgenie.app

sealed class QrContentType {
    data class Url(val url: String) : QrContentType()
    data class Wifi(val ssid: String, val password: String?, val encryption: String) : QrContentType()
    data class Contact(val name: String?, val phone: String?, val email: String?) : QrContentType()
    data class Email(val address: String, val subject: String?, val body: String?) : QrContentType()
    data class Phone(val number: String) : QrContentType()
    data class Sms(val number: String, val body: String?) : QrContentType()
    data class PlainText(val text: String) : QrContentType()
}

object QrContentParser {

    fun parse(raw: String): QrContentType {
        val trimmed = raw.trim()
        return parseWifi(trimmed)
            ?: parseVCard(trimmed)
            ?: parseMailto(trimmed)
            ?: parseTel(trimmed)
            ?: parseSms(trimmed)
            ?: parseUrl(trimmed)
            ?: QrContentType.PlainText(raw)
    }

    private fun field(source: String, key: String): String? {
        val regex = Regex("$key:((?:\\\\.|[^;])*);", RegexOption.IGNORE_CASE)
        val match = regex.find(source) ?: return null
        return match.groupValues[1].replace("\\;", ";").replace("\\:", ":").replace("\\\\", "\\")
    }

    private fun parseWifi(source: String): QrContentType.Wifi? {
        if (!source.startsWith("WIFI:", ignoreCase = true)) return null
        val ssid = field(source, "S") ?: return null
        val password = field(source, "P")
        val encryption = field(source, "T") ?: "WPA"
        return QrContentType.Wifi(ssid, password, encryption)
    }

    private fun parseVCard(source: String): QrContentType.Contact? {
        if (!source.contains("BEGIN:VCARD", ignoreCase = true)) return null
        val nameLine = Regex("FN:(.*)", RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)?.trim()
        val telLine = Regex("TEL[^:]*:(.*)", RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)?.trim()
        val emailLine = Regex("EMAIL[^:]*:(.*)", RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)?.trim()
        if (nameLine == null && telLine == null && emailLine == null) return null
        return QrContentType.Contact(nameLine, telLine, emailLine)
    }

    private fun parseMailto(source: String): QrContentType.Email? {
        if (!source.startsWith("mailto:", ignoreCase = true)) return null
        val withoutScheme = source.substringAfter(":")
        val address = withoutScheme.substringBefore("?")
        if (address.isBlank()) return null
        val query = withoutScheme.substringAfter("?", "")
        val params = query.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].lowercase() to parts[1] else null
        }.toMap()
        return QrContentType.Email(address, params["subject"], params["body"])
    }

    private fun parseTel(source: String): QrContentType.Phone? {
        if (!source.startsWith("tel:", ignoreCase = true)) return null
        val number = source.substringAfter(":").trim()
        if (number.isBlank()) return null
        return QrContentType.Phone(number)
    }

    private fun parseSms(source: String): QrContentType.Sms? {
        val prefix = when {
            source.startsWith("smsto:", ignoreCase = true) -> "smsto:"
            source.startsWith("sms:", ignoreCase = true) -> "sms:"
            else -> return null
        }
        val rest = source.substring(prefix.length)
        val number = rest.substringBefore(":").trim()
        if (number.isBlank()) return null
        val body = rest.substringAfter(":", "").ifBlank { null }
        return QrContentType.Sms(number, body)
    }

    private fun parseUrl(source: String): QrContentType.Url? {
        val scheme = try { android.net.Uri.parse(source).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme == "http" || scheme == "https") return QrContentType.Url(source)
        return null
    }
}
