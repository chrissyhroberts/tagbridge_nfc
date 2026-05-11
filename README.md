# TagBridge

TagBridge is an offline-first Android NFC utility for mobile field data collection workflows.

The app allows NFC tags to be scanned directly into systems such as:

- ODK Collect
- KoboCollect
- Ona
- CommCare
- SurveyCTO
  
- Other Android intent-compatible platforms


<p float="left">
  <img src="https://github.com/user-attachments/assets/2689e776-ee57-42ea-bf83-152787a617a5" width="23%" />
  <img src="https://github.com/user-attachments/assets/ba4ca234-15a6-41b8-9a21-0e85d6da215c" width="23%" />
  <img src="https://github.com/user-attachments/assets/20129c88-09ac-4e3d-949e-8f515e59219e" width="23%" />
  <img src="https://github.com/user-attachments/assets/1c29f951-b773-41a2-b80c-075b539377d8" width="23%" />
</p>


# Features

- Offline NFC scanning
- Android intent integration
- Support for multiple NFC technologies
- JSON and key-value return formats
- NFC inspection mode
- Intent generator for XLSForms
- Flexible field extraction
- Lightweight and minimal interface
- No cloud dependency
- Manual Write to NFC tags

TagBridge is designed for low-resource, offline, and operational environments including Clinical research, Epidemiology, Laboratory workflows, Asset tracking, Sample management, Participant identification, Field surveys

---

# Installation

## Android Sideload Installation

TagBridge is currently distributed as an APK file.

1. Download the latest APK from the GitHub Releases page.
2. Transfer the APK to your Android device if necessary.
3. Open the APK on the device.
4. Allow installation from unknown sources if prompted.
5. Install the application.

Depending on the Android version and manufacturer, the wording of the security prompts may differ slightly.

---

# Basic Usage

## Scan NFC Into ODK / Kobo

In an XLSForm:

```text
ex:uk.ac.lshtm.tagbridge.SCAN_NFC
```

When the question is opened:

1. TagBridge launches
2. The user scans an NFC tag
3. The value is returned automatically into the form

---

# Return Formats

## Single Value

Returns a single selected field - Use the appearance column

Example:

```text
ex:uk.ac.lshtm.tagbridge.SCAN_NFC(value_field='tag_id_hex', format='single')
```

---

## JSON Return

Returns structured JSON.

Example:

```text
ex:uk.ac.lshtm.tagbridge.SCAN_NFC(return_fields='tag_id_hex,tag_id_dec,tech_list', format='json')
```

Example returned value  - Use the appearance column

```json
{
  "tag_id_hex": "04B9448A655A80",
  "tag_id_dec": "133742",
  "tech_list": "NfcA,MifareUltralight"
}
```
---

# Field-List Group Integration

TagBridge also supports ODK field-list workflows using `body::intent` or `bind::intent`.

Example:

```csv
type,name,label,appearance,body::intent
begin_group,nfc_group,NFC Data,field-list,"uk.ac.lshtm.tagbridge.SCAN_NFC(return_fields='tag_id_hex,tag_id_dec,tech_list')"
text,tag_id_hex,Tag ID Hex,,
text,tag_id_dec,Tag ID Decimal,,
text,tech_list,Tech List,,
end_group,,,,
```

Important:

- `appearance` uses `ex:`
- `body::intent` and `bind::intent` do not use `ex:`

---

# Available Fields

| Field | Description |
|---|---|
| tag_id_hex | NFC identifier in hexadecimal |
| tag_id_dec | NFC identifier in decimal |
| tech_list | Available NFC technologies |
| ndef_text | Extracted text payload |
| ndef_uri | Extracted URI payload |
| record_count | Number of NDEF records |
| size_bytes | Payload size |
| max_size_bytes | Maximum writable capacity |
| is_writable | Whether tag is writable |
| can_make_readonly | Whether tag can be permanently locked |
| mime_types | MIME record types |
| external_types | External record types |
| raw_ndef_json | Full raw record structure |
| summary | Human-readable summary |

---

# Inspection Mode

When TagBridge is opened normally rather than from ODK/Kobo, it enters inspection mode.

Inspection mode allows the user to:

- Scan arbitrary NFC tags
- View raw NFC metadata
- View NDEF records
- Explore payload structures
- Select fields interactively
- Generate XLSForm-compatible intent strings to copy-paste into XLS Form.
- Copy generated intents to clipboard

This is useful for rapid development and debugging.

---

# NFC Compatibility

TagBridge currently supports reading:

- NDEF tags
- NTAG tags
- MIFARE Ultralight
- ISO-DEP devices
- NFC-A
- NFC-B
- NFC-F
- NFC-V

Some secure devices such as passports and payment cards may expose randomised identifiers rather than stable IDs. This is expected behaviour and is designed to prevent passive tracking.


---

# Development Notes

This project was developed through a hybrid workflow combining human-directed design and extensive use of AI-assisted coding tools.

The overall architecture, workflow design, NFC/ODK integration concepts, testing process, and operational requirements were directed by Chrissy Roberts. Large portions of implementation code, debugging support, refactoring, and documentation were generated iteratively using large language models and then tested, modified, and integrated into the final application.

This project should therefore be understood as an example of collaborative human-AI software development rather than purely manual software engineering.

---

# Planned Features

Planned future development includes:

- Full NFC writing support
- Continuous scan mode
- Duplicate suppression
- Inventory workflows
- Signed payloads
- Optional write-once locking
- Structured participant records
- Asset registration workflows

---

# Building From Source

## Requirements

- Android SDK 35+
- Java 17
- Gradle 8.9+

---

## Build

```bash
./gradlew clean assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/
```

---

# License

MIT License
