# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java/Maven project that provides:
1. **Wordlist Generator** - Creates DDSSSC-style (Date + Sequence + Check digit) password dictionaries for credential cracking testing
2. **HTTP Login Cracker** - Multi-threaded HTTP login brute-force tool with HMAC/AES encryption, captcha solving, and SQLite progress tracking

It is a port of a Python tool (UJN_lib_scaper).

## Build & Run Commands

```bash
# Build (produces jar at target/wordlist-generator-0.1.0-jar-with-dependencies.jar)
mvn package

# Run tests
mvn test

# Run single test
mvn test -Dtest=WordlistGeneratorTest
```

## Architecture

### Wordlist Generator
- **Main class**: `com.userzbb.wordlist.WordlistGenerator`
- **Test class**: `com.userzbb.wordlist.WordlistGeneratorTest`

### HTTP Login Cracker
- **Main class**: `com.userzbb.cracker.HttpCracker`
- **Package**: `com.userzbb.cracker.*`

### Key Classes/Functions

#### WordlistGenerator
- `WordlistGenerator.main(String[])` — CLI entry, argument parsing, output file handling
- `parseDaySpec(String)` — Parses day specification (single day, range `01-05`, or comma list `01,03`)
- `generateDDSSSC(BufferedWriter, List<Integer>, char, int, int)` — Core generation: nested loops over days → sequences → check digits
- `generateRecursive(BufferedWriter, char[], char[], int, int)` — Generic charset brute-force (not DDSSSC mode)
- `resolveCharset(String)` — Maps charset name to character array

#### HTTP Cracker
- `HttpCracker.main(String[])` — CLI entry, argument parsing, thread pool management
- `HttpWorker` — Worker thread: fetch captcha → solve → encrypt → POST login
- `CryptoUtils.encryptAES(String)` — AES-128-CBC encryption
- `CryptoUtils.generateHeaders(String)` — HMAC-SHA256 signature headers
- `CaptchaSolver.solve()` — Fetch and OCR solve captcha
- `DatabaseManager` — SQLite: found_passwords, crack_progress tables
- `ProgressTracker` — High-water mark tracking for resume capability

### DDSSSC Format

Output: `day_key,password` (e.g., `M_08,080010`)
- Male (M): odd sequence numbers only
- Female (F): even sequence numbers only
- ALL: generates both M_ and F_ entries

### HTTP Cracker CLI Options

```
Usage: java -jar wordlist-generator.jar <username> [options]
  -g, --gender <M|F|ALL>  Gender filter (default: ALL)
  -d, --day <spec>        Day specification (default: 01-31)
  -s, --max-seq <N>       Maximum sequence number (default: 500)
  -t, --threads <N>       Worker threads (default: 5)
  -o, --output <path>     Output file for found passwords
  -h, --help              Show help
```

### HTTP Cracker Login Results
- `SUCCESS` — Login successful
- `FAIL_PASS` — Wrong password
- `FAIL_CAPTCHA` — Captcha error
- `FAIL_LOCK` — Account locked
- `FAIL_RATE_LIMIT` — Rate limited
- `ERROR` — Other errors

### Maven Configuration

- Java 11 target
- Dependencies:
  - JUnit Jupiter 5.10.0 (test scope)
  - Apache HttpClient 5.3
  - SQLite JDBC 3.45.1.0
  - BouncyCastle BCprov 1.70
  - Tesseract OCR 5.11.0
- Assembly plugin creates fat JAR with dependencies
