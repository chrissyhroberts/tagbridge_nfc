package uk.ac.lshtm.tagbridge

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.Locale
import kotlin.experimental.and

class MainActivity : Activity(), NfcAdapter.ReaderCallback {

    companion object {
        const val ACTION_SCAN_NFC = "uk.ac.lshtm.tagbridge.SCAN_NFC"

        private val DEFAULT_RETURN_FIELDS = listOf("tag_id_hex")

        private val ALL_FIELDS = listOf(
            "tag_id_hex",
            "tag_id_dec",
            "tech_list",
            "ndef_text",
            "ndef_uri",
            "record_count",
            "size_bytes",
            "max_size_bytes",
            "is_writable",
            "can_make_readonly",
            "mime_types",
            "external_types",
            "payload_hex_all",
            "payload_utf8_all",
            "first_payload_hex",
            "first_payload_utf8",
            "raw_ndef_json",
            "summary"
        )
    }

    private var nfcAdapter: NfcAdapter? = null
    private var launchedFromOdk = false
    private var alreadyReturnedToOdk = false
    private var continuousMode = false
    private var pendingWrite: PendingWrite? = null
    private var lastTagId: String = ""
    private var lastTagTimeMs: Long = 0L

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var resultBox: TextView
    private lateinit var checkBoxHost: LinearLayout
    private lateinit var generatedIntent: TextView
    private lateinit var debugView: TextView
    private lateinit var inventoryBox: TextView
    private lateinit var writeTextInput: EditText
    private lateinit var writeUriInput: EditText

    private var latestFields: Map<String, String> = emptyMap()
    private val inventory = linkedMapOf<String, InventoryItem>()

    private data class InventoryItem(
        var count: Int,
        var lastSeenMs: Long,
        var techList: String,
        var summary: String
    )

    private data class PendingWrite(
        val type: String,
        val value: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        launchedFromOdk =
            intent?.action == ACTION_SCAN_NFC ||
            intent?.action?.contains("SCAN_NFC") == true ||
            intent?.hasExtra("value_field") == true ||
            intent?.hasExtra("return_fields") == true ||
            intent?.hasExtra("format") == true

        buildUi()

        debug("APP STARTED")
        debug("ACTION = ${intent?.action}")
        debug("DATA = ${intent?.data}")
        debug("EXTRAS = ${bundleToString(intent?.extras)}")
        debug("launchedFromOdk = $launchedFromOdk")
        debug("value_field = ${intent?.getStringExtra("value_field") ?: "tag_id_hex"}")
        debug("return_fields = ${intent?.getStringExtra("return_fields") ?: "tag_id_hex"}")
        debug("format = ${intent?.getStringExtra("format") ?: "single"}")
    }

    override fun onResume() {
        super.onResume()
        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) {
            debug("onTagDiscovered: null tag")
            return
        }

        val parsed = parseTag(tag)
        val tagId = parsed["tag_id_hex"] ?: ""
        val now = System.currentTimeMillis()

        if (tagId == lastTagId && now - lastTagTimeMs < 900 && pendingWrite == null && !continuousMode) {
            debug("Duplicate scan suppressed: $tagId")
            return
        }
        lastTagId = tagId
        lastTagTimeMs = now
        latestFields = parsed

        val writeRequest = pendingWrite
        if (writeRequest != null) {
            val writeResult = writeNdef(tag, writeRequest)
            pendingWrite = null
            runOnUiThread {
                debug(writeResult)
                Toast.makeText(this, writeResult, Toast.LENGTH_LONG).show()
                status.text = writeResult
                renderResult(parseTag(tag))
            }
            return
        }

        runOnUiThread {
            debug("TAG DISCOVERED")
            debug("tag_id_hex = $tagId")
            debug("tech_list = ${parsed["tech_list"]}")
            renderResult(parsed)
            updateInventory(parsed)

            if (launchedFromOdk && !alreadyReturnedToOdk) {
                alreadyReturnedToOdk = true
                debug("ODK MODE: preparing return")
                Toast.makeText(this, "Returning NFC data to ODK", Toast.LENGTH_SHORT).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    finishForOdk(parsed)
                }, 250)
            }
        }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 44, 36, 36)
            setBackgroundColor(Color.rgb(20, 20, 20))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(20, 20, 20))
            addView(root)
        }
        setContentView(scroll)

        root.addView(text("TagBridge", 24f, bold = true))

        status = text(
            when {
                nfcAdapter == null -> "This device does not have NFC."
                nfcAdapter?.isEnabled == false -> "NFC is switched off."
                launchedFromOdk -> "ODK mode: tap an NFC tag to return data to Collect."
                else -> "Inspection mode: tap an NFC tag to inspect, write, or inventory."
            },
            16f
        ).apply { setPadding(0, 20, 0, 20) }
        root.addView(status)

        if (nfcAdapter?.isEnabled == false) {
            root.addView(button("OPEN NFC SETTINGS") { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) })
        }

        resultBox = text("No tag scanned yet.", 14f).apply { setPadding(0, 12, 0, 24) }
        root.addView(resultBox)

        checkBoxHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 12)
        }
        root.addView(checkBoxHost)

        generatedIntent = text("ex:$ACTION_SCAN_NFC(value_field='tag_id_hex')", 13f, Color.LTGRAY).apply {
            setPadding(0, 18, 0, 18)
        }
        root.addView(generatedIntent)

        root.addView(button("COPY GENERATED ODK INTENT") { copyIntent() })
        root.addView(button("RETURN SELECTED FIELDS AS JSON") { finishWithSelectedJson() })

        if (!launchedFromOdk) {
            addInventoryUi()
            addWriterUi()
        }

        debugView = text("DEBUG", 12f, Color.rgb(180, 255, 180)).apply {
            setBackgroundColor(Color.BLACK)
            setPadding(12, 12, 12, 12)
        }
        root.addView(debugView)

        if (launchedFromOdk) {
            generatedIntent.visibility = View.GONE
            checkBoxHost.visibility = View.GONE
        }
    }

    private fun addInventoryUi() {
        val continuous = CheckBox(this).apply {
            text = "Continuous inventory mode"
            setTextColor(Color.WHITE)
            isChecked = continuousMode
            setOnCheckedChangeListener { _, checked ->
                continuousMode = checked
                debug("continuousMode = $continuousMode")
                status.text = if (checked) {
                    "Continuous inventory mode: scan multiple assets."
                } else {
                    "Inspection mode: tap an NFC tag to inspect, write, or inventory."
                }
            }
        }
        root.addView(continuous)

        root.addView(button("CLEAR INVENTORY LIST") {
            inventory.clear()
            updateInventoryBox()
        })

        inventoryBox = text("Inventory list is empty.", 13f, Color.LTGRAY).apply {
            setPadding(0, 8, 0, 24)
        }
        root.addView(inventoryBox)
    }

    private fun addWriterUi() {
        root.addView(text("Write NDEF tag", 18f, bold = true).apply { setPadding(0, 20, 0, 8) })

        writeTextInput = EditText(this).apply {
            hint = "Text to write"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine(false)
            setBackgroundColor(Color.rgb(35, 35, 35))
            setPadding(12, 8, 12, 8)
        }
        root.addView(writeTextInput)

        root.addView(button("WRITE TEXT TO NEXT TAG") {
            val value = writeTextInput.text.toString()
            if (value.isBlank()) {
                Toast.makeText(this, "Enter text first", Toast.LENGTH_SHORT).show()
            } else {
                pendingWrite = PendingWrite("text", value)
                status.text = "Ready to write text. Tap the NFC tag now."
                debug("Pending write: text")
            }
        })

        writeUriInput = EditText(this).apply {
            hint = "URI to write, e.g. https://example.org/asset/123"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine(false)
            setBackgroundColor(Color.rgb(35, 35, 35))
            setPadding(12, 8, 12, 8)
        }
        root.addView(writeUriInput)

        root.addView(button("WRITE URI TO NEXT TAG") {
            val value = writeUriInput.text.toString()
            if (value.isBlank()) {
                Toast.makeText(this, "Enter a URI first", Toast.LENGTH_SHORT).show()
            } else {
                pendingWrite = PendingWrite("uri", value)
                status.text = "Ready to write URI. Tap the NFC tag now."
                debug("Pending write: uri")
            }
        })
    }

    private fun text(value: String, size: Float, colour: Int = Color.WHITE, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(colour)
            if (bold) setTypeface(null, 1)
        }
    }

    private fun button(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(92, 92, 96))
            setOnClickListener { action() }
        }
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter ?: run {
            debug("enableReaderMode: no NFC adapter")
            return
        }

        val flags =
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE

        adapter.enableReaderMode(this, this, flags, null)
        debug("NFC reader mode enabled")
    }

    private fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(this)
        debug("NFC reader mode disabled")
    }

    private fun parseTag(tag: Tag): Map<String, String> {
        val fields = linkedMapOf<String, String>()

        val idBytes = tag.id ?: ByteArray(0)
        fields["tag_id_hex"] = idBytes.joinToString("") { "%02X".format(it) }
        fields["tag_id_dec"] = idBytes.fold(0L) { acc, b -> (acc shl 8) + (b.toInt() and 0xff) }.toString()
        fields["tech_list"] = tag.techList.joinToString(",") { it.substringAfterLast('.') }

        val ndef = Ndef.get(tag)
        val ndefMessage = readNdefMessage(ndef)
        val records = ndefMessage?.records?.asList() ?: emptyList()

        fields["size_bytes"] = ndefMessage?.toByteArray()?.size?.toString() ?: ""
        fields["max_size_bytes"] = ndef?.maxSize?.toString() ?: ""
        fields["is_writable"] = ndef?.isWritable?.toString() ?: ""
        fields["can_make_readonly"] = ndef?.canMakeReadOnly()?.toString() ?: ""

        fields["record_count"] = records.size.toString()
        fields["ndef_text"] = records.mapNotNull { textFromRecord(it) }.joinToString(" | ")
        fields["ndef_uri"] = records.mapNotNull { uriFromRecord(it) }.joinToString(" | ")
        fields["mime_types"] = records.mapNotNull { mimeFromRecord(it) }.distinct().joinToString(",")
        fields["external_types"] = records.mapNotNull { externalTypeFromRecord(it) }.distinct().joinToString(",")
        fields["payload_hex_all"] = records.joinToString("|") { it.payload.joinToString("") { b -> "%02X".format(b) } }
        fields["payload_utf8_all"] = records.joinToString(" | ") { runCatching { String(it.payload, Charsets.UTF_8) }.getOrDefault("") }
        fields["first_payload_hex"] = records.firstOrNull()?.payload?.joinToString("") { "%02X".format(it) } ?: ""
        fields["first_payload_utf8"] = records.firstOrNull()?.payload?.let { runCatching { String(it, Charsets.UTF_8) }.getOrDefault("") } ?: ""
        fields["raw_ndef_json"] = recordsJson(records).toString()

        fields["summary"] = listOfNotNull(
            fields["tag_id_hex"]?.takeIf { it.isNotBlank() }?.let { "id=$it" },
            fields["ndef_text"]?.takeIf { it.isNotBlank() }?.let { "text=$it" },
            fields["ndef_uri"]?.takeIf { it.isNotBlank() }?.let { "uri=$it" }
        ).joinToString("; ")

        return fields
    }

    private fun readNdefMessage(ndef: Ndef?): NdefMessage? {
        if (ndef == null) return null

        return try {
            if (!ndef.isConnected) ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            closeQuietly(ndef)
            message
        } catch (e: Exception) {
            debug("NDEF read failed: ${e.message}")
            closeQuietly(ndef)
            ndef.cachedNdefMessage
        }
    }

    private fun writeNdef(tag: Tag, write: PendingWrite): String {
        val record = when (write.type) {
            "uri" -> NdefRecord.createUri(write.value)
            else -> NdefRecord.createTextRecord("en", write.value)
        }
        val message = NdefMessage(arrayOf(record))
        val size = message.toByteArray().size

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    closeQuietly(ndef)
                    "Tag is NDEF but not writable."
                } else if (ndef.maxSize < size) {
                    closeQuietly(ndef)
                    "Tag too small. Need $size bytes, max is ${ndef.maxSize}."
                } else {
                    ndef.writeNdefMessage(message)
                    closeQuietly(ndef)
                    "NDEF ${write.type} written successfully."
                }
            } catch (e: IOException) {
                closeQuietly(ndef)
                "NDEF write failed: ${e.message}"
            } catch (e: FormatException) {
                closeQuietly(ndef)
                "NDEF format error: ${e.message}"
            } catch (e: Exception) {
                closeQuietly(ndef)
                "NDEF write failed: ${e.message}"
            }
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)
                closeQuietly(formatable)
                "Tag formatted and NDEF ${write.type} written successfully."
            } catch (e: Exception) {
                closeQuietly(formatable)
                "NDEF format/write failed: ${e.message}"
            }
        }

        return "This tag is not NDEF-writable or formatable."
    }

    private fun closeQuietly(tech: android.nfc.tech.TagTechnology?) {
        try {
            tech?.close()
        } catch (_: Exception) {
        }
    }

    private fun textFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_TEXT)) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null

        val langLen = payload[0].toInt() and 0x3F
        val encoding = if ((payload[0] and 0x80.toByte()) == 0.toByte()) Charsets.UTF_8 else Charset.forName("UTF-16")
        return String(payload, 1 + langLen, payload.size - 1 - langLen, encoding)
    }

    private fun uriFromRecord(record: NdefRecord): String? {
        return try { record.toUri()?.toString() } catch (_: Exception) { null }
    }

    private fun mimeFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_MIME_MEDIA) return null
        return String(record.type, Charsets.US_ASCII).lowercase(Locale.ROOT)
    }

    private fun externalTypeFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_EXTERNAL_TYPE) return null
        return String(record.type, Charsets.US_ASCII)
    }

    private fun recordsJson(records: List<NdefRecord>): JSONArray {
        val arr = JSONArray()
        records.forEachIndexed { i, r ->
            arr.put(JSONObject().apply {
                put("index", i)
                put("tnf", r.tnf)
                put("type", r.type.joinToString("") { "%02X".format(it) })
                put("mime", mimeFromRecord(r) ?: "")
                put("external_type", externalTypeFromRecord(r) ?: "")
                put("text", textFromRecord(r) ?: "")
                put("uri", uriFromRecord(r) ?: "")
                put("payload_hex", r.payload.joinToString("") { "%02X".format(it) })
                put("payload_utf8_guess", runCatching { String(r.payload, Charsets.UTF_8) }.getOrDefault(""))
            })
        }
        return arr
    }

    private fun renderResult(fields: Map<String, String>) {
        status.text = if (launchedFromOdk) "Tag scanned. Returning to ODK..." else "Tag scanned."
        resultBox.text = fields.entries.joinToString("\n") { "${it.key}: ${it.value}" }

        checkBoxHost.removeAllViews()
        if (!launchedFromOdk) {
            ALL_FIELDS.forEach { key ->
                checkBoxHost.addView(CheckBox(this).apply {
                    text = key
                    setTextColor(Color.WHITE)
                    isChecked = key == "tag_id_hex"
                    tag = key
                    setOnCheckedChangeListener { _, _ -> updateGeneratedIntent() }
                })
            }
            updateGeneratedIntent()
        }
    }

    private fun selectedFields(): List<String> {
        return (0 until checkBoxHost.childCount)
            .map { checkBoxHost.getChildAt(it) }
            .filterIsInstance<CheckBox>()
            .filter { it.isChecked }
            .map { it.tag as String }
    }

    private fun updateGeneratedIntent() {
        val fields = selectedFields().ifEmpty { listOf("tag_id_hex") }
        generatedIntent.text = "ex:$ACTION_SCAN_NFC(value_field='${fields.first()}', return_fields='${fields.joinToString(",")}', format='single')"
    }

    private fun copyIntent() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ODK NFC intent", generatedIntent.text.toString()))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun finishForOdk(fields: Map<String, String>) {
        val requested = requestedFields()
        val valueField = intent?.getStringExtra("value_field")?.takeIf { it.isNotBlank() } ?: requested.firstOrNull() ?: "tag_id_hex"
        val format = intent?.getStringExtra("format")?.takeIf { it.isNotBlank() } ?: "single"

        val value = when (format.lowercase(Locale.ROOT)) {
            "json" -> JSONObject(requested.associateWith { fields[it] ?: "" }).toString()
            "kv" -> requested.joinToString(";") { "$it=${fields[it] ?: ""}" }
            else -> fields[valueField] ?: fields["tag_id_hex"] ?: ""
        }

        debug("finishForOdk called")
        debug("requested = ${requested.joinToString(",")}")
        debug("valueField = $valueField")
        debug("format = $format")
        debug("RETURN value = $value")

        val result = Intent().apply {
            putExtra("value", value)
            ALL_FIELDS.forEach { putExtra(it, fields[it] ?: "") }
        }

        setResult(RESULT_OK, result)
        debug("setResult OK called")
        debug("finish called")
        finish()
    }

    private fun requestedFields(): List<String> {
        val fromIntent = intent?.getStringExtra("return_fields")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        return fromIntent.ifEmpty { DEFAULT_RETURN_FIELDS }
    }

    private fun finishWithSelectedJson() {
        if (latestFields.isEmpty()) {
            Toast.makeText(this, "Scan a tag first", Toast.LENGTH_SHORT).show()
            return
        }

        val requested = selectedFields().ifEmpty { listOf("tag_id_hex") }
        val result = Intent()
        requested.forEach { result.putExtra(it, latestFields[it] ?: "") }
        result.putExtra("value", JSONObject(requested.associateWith { latestFields[it] ?: "" }).toString())
        setResult(RESULT_OK, result)
        finish()
    }

    private fun updateInventory(fields: Map<String, String>) {
        if (!continuousMode) return
        val id = fields["tag_id_hex"] ?: return
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        val item = inventory[id]
        if (item == null) {
            inventory[id] = InventoryItem(
                count = 1,
                lastSeenMs = now,
                techList = fields["tech_list"] ?: "",
                summary = fields["summary"] ?: id
            )
        } else {
            item.count += 1
            item.lastSeenMs = now
            item.techList = fields["tech_list"] ?: item.techList
            item.summary = fields["summary"] ?: item.summary
        }
        updateInventoryBox()
    }

    private fun updateInventoryBox() {
        if (!::inventoryBox.isInitialized) return
        inventoryBox.text = if (inventory.isEmpty()) {
            "Inventory list is empty."
        } else {
            inventory.entries.joinToString("\n") { (id, item) ->
                "$id  count=${item.count}  ${item.techList}"
            }
        }
    }

    private fun debug(msg: String) {
        Log.d("ODK_NFC", msg)
        runOnUiThread {
            if (::debugView.isInitialized) debugView.append("\n$msg")
        }
    }

    private fun bundleToString(bundle: Bundle?): String {
        if (bundle == null) return "null"
        return bundle.keySet().joinToString(", ") { key -> "$key=${bundle.get(key)}" }
    }
}
