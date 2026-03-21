package com.bluemix.clients_lead.core.common.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bluemix.clients_lead.domain.model.Client
import java.io.File
import java.io.FileWriter

object CsvExporter {
    fun exportClients(context: Context, clients: List<Client>) {
        try {
            val file = File(context.cacheDir, "clients_export_${System.currentTimeMillis()}.csv")
            val writer = FileWriter(file)
            
            // Header
            writer.append("ID,Name,Email,Phone,Address,Pincode,Latitude,Longitude,Status,Last Visit Type,Last Visit Date\n")
            
            // Data
            clients.forEach { client ->
                writer.append("${client.id},")
                writer.append("\"${client.name.replace("\"", "'")}\",")
                writer.append("${client.email ?: ""},")
                writer.append("${client.phone ?: ""},")
                writer.append("\"${(client.address ?: "").replace("\"", "'")}\",")
                writer.append("${client.pincode ?: ""},")
                writer.append("${client.latitude ?: ""},")
                writer.append("${client.longitude ?: ""},")
                writer.append("${client.status ?: ""},")
                writer.append("${client.lastVisitType ?: ""},")
                writer.append("${client.lastVisitDate ?: ""}\n")
            }
            
            writer.flush()
            writer.close()
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Clients Export")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Share CSV"))
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
