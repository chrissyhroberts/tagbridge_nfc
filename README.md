# ODK NFC Connector

A lightweight Android NFC utility designed for integration with the ODK ecosystem.

This application allows ODK Collect forms to:

- Read NFC tags
- Extract tag metadata and payloads
- Return values directly into XLSForms using Android intents
- Inspect arbitrary NFC tags interactively
- Generate reusable ODK intent strings
- Support multiple NFC technologies and payload formats
- Operate entirely offline

The application was designed primarily for:

- Clinical research
- Field epidemiology
- Asset tracking
- Participant identification
- Laboratory workflows
- Inventory systems
- Low-resource and offline environments

---

# Features

## NFC Reading

The app currently supports reading:

- NDEF tags
- NTAG tags
- MIFARE Ultralight
- ISO-DEP devices
- NFC-A
- NFC-B
- NFC-F
- NFC-V
- Barcode-compatible NFC interfaces

The app automatically detects available technologies and extracts available metadata.

---

# ODK Integration

The application can be launched directly from ODK Collect using Android external app intents.

The connector supports both:

- Single-question return workflows
- Multi-field field-list workflows

---

# Single Question Mode

Use the `appearance` column with the `ex:` prefix.

Example:

```text
ex:uk.ac.lshtm.odknfcconnector.SCAN_NFC
```

Example XLSForm:

```csv
type,name,label,appearance
text,nfc_tag,Scan NFC,ex:uk.ac.lshtm.odknfcconnector.SCAN_NFC
```

---

# JSON Return Example

```csv
type,name,label,appearance
text,nfc_json,Scan NFC,"ex:uk.ac.lshtm.odknfcconnector.SCAN_NFC(return_fields='tag_id_hex,tag_id_dec,tech_list', format='json')"
```

Returned value:

```json
{
  "tag_id_hex": "04B9448A655A80",
  "tag_id_dec": "133742",
  "tech_list": "NfcA,MifareUltralight"
}
```

---

# Field-List Group Mode

Use:

- `appearance = field-list`
- `body::intent = uk.ac.lshtm.odknfcconnector.SCAN_NFC`

Important:

- `body::intent` does NOT use the `ex:` prefix
- `field-list` and `ex:` cannot reliably coexist in the same appearance field

Example:

```csv
type,name,label,appearance,body::intent
begin_group,nfc_group,NFC Data,field-list,"uk.ac.lshtm.odknfcconnector.SCAN_NFC(return_fields='tag_id_hex,tag_id_dec,tech_list')"
text,tag_id_hex,Tag ID Hex,,
text,tag_id_dec,Tag ID Decimal,,
text,tech_list,Tech List,,
end_group,,,,
```

---

# Flexible Return Formats

The connector currently supports:

| Format | Behaviour |
|---|---|
| single | Returns one selected value |
| kv | Returns semicolon-delimited key-value pairs |
| json | Returns a JSON object |

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

When launched normally rather than from ODK, the application enters inspection mode.

Inspection mode allows the user to:

- Scan arbitrary NFC tags
- View raw tag metadata
- View NDEF records
- View payload structures
- Select desired fields
- Generate ODK-compatible intent strings
- Copy generated intents to clipboard

This is useful for exploratory development and rapid form design.

---

# NFC Behaviour Notes

## Stable IDs

Simple NFC tags such as NTAG and MIFARE Ultralight generally expose stable identifiers.

These are appropriate for:

- Asset tracking
- Participant tokens
- Equipment identifiers
- Sample labels

---

## Randomised IDs

Secure devices such as:

- Passports
- Payment cards
- Some DESFire cards

may expose randomised identifiers that change between scans.

This is expected behaviour and is designed to prevent passive tracking.

Applications requiring stable identity should therefore rely on controlled NDEF payloads rather than physical tag IDs alone.

---

# Planned Features

## Writing Support

Planned NFC writing modes include:

- Text payloads
- URI payloads
- JSON payloads
- Structured participant records
- Asset metadata
- Signed payloads
- Optional write-once locking
- Verify-after-write workflows

---

## Continuous Scan Mode

Planned support for:

- Rapid inventory workflows
- Duplicate suppression
- Scan history
- Batch logging
- Offline asset auditing

---

# Building

## Requirements

- Android SDK 35+
- Java 17
- Gradle 8.9+
- Android Studio recommended

---

## Build

```bash
./gradlew clean assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

# Installation

Install via ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK directly to the Android device and install manually.

---

# Current Architecture

The application uses Android NFC Reader Mode rather than foreground dispatch.

Reader Mode proved substantially more reliable across modern Android devices when interacting with ODK Collect.

---

# Design Principles

This project prioritises:

- Offline operation
- Transparency
- Open workflows
- Interoperability
- Minimal dependencies
- Low-resource compatibility
- Field robustness
- Simple deployment

---

# Disclaimer

This application is experimental research software.

Validation is strongly recommended before use in regulated environments.

---

# License

MIT License
