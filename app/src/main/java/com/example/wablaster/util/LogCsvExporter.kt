package com.example.wablaster.util

import com.example.wablaster.data.local.entity.SendLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogCsvExporter {
    fun toCsvString(logs: List<SendLogEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Waktu,Nomor,Nama,Status,Varian,Pesan,Keterangan Gagal\n")
        for (log in logs) {
            val time = dateFormat.format(Date(log.sentAt))
            val phone = escapeCsv(log.phone)
            val name = escapeCsv(log.contactName)
            val status = escapeCsv(log.status)
            val variant = "Varian ${log.variantIndex + 1}"
            val message = escapeCsv(log.renderedMessage)
            val error = escapeCsv(log.errorMessage ?: "-")
            sb.append("$time,$phone,$name,$status,$variant,$message,$error\n")
        }
        return sb.toString()
    }

    fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
