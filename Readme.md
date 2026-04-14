# 📁 MyFTP — Multithreaded FTP Client & Server

A high-performance, concurrent FTP system built from scratch in Java using raw sockets and manually managed threads — **without any thread-safe data structures from `java.util.concurrent`**. Supports multiple simultaneous clients, real-time transfer termination, and background command execution.

---

## 📌 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Compilation](#compilation)
  - [Running the Server](#running-the-server)
  - [Running the Client](#running-the-client)
- [Supported Commands](#supported-commands)
- [Concurrency Design](#concurrency-design)
- [Project Structure](#project-structure)
- [Team](#team)

---

## Overview

MyFTP is a multithreaded File Transfer Protocol implementation built entirely in Java using `java.net.Socket`, `ServerSocket`, and raw `Thread` objects. All synchronization is implemented manually using `synchronized` blocks and `wait()`/`notify()` — **no `ConcurrentHashMap`, `BlockingQueue`, or any other thread-safe data structures from `java.util.concurrent` are used**, in compliance with project requirements.

**Core capabilities:**
- **Multiple simultaneous clients** handled concurrently via a thread-per-client model
- **Background command execution** on the client using the `&` operator
- **Mid-transfer termination** of long-running `get`/`put` via a dedicated terminate port
- **Thread-safe file operations** with manual `synchronized` locking to prevent data corruption

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                     FTP SERVER                        │
│                                                       │
│   nport (e.g. 8080)         tport (e.g. 8081)        │
│        │                           │                  │
│   ┌────▼──────┐             ┌──────▼──────┐          │
│   │ Listener  │             │  Terminate  │          │
│   │  Thread   │             │   Listener  │          │
│   └────┬──────┘             └──────┬──────┘          │
│        │ (spawns per client)        │                 │
│   ┌────▼──────┐         ┌──────────▼──────────┐      │
│   │  Client   │         │  Lookup command ID   │      │
│   │  Handler  │◄────────│  Set status flag     │      │
│   │  Thread   │         │  → "TERMINATE"       │      │
│   └───────────┘         └──────────────────────┘      │
│  (one per connected client, handles all commands)     │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│                     FTP CLIENT                        │
│                                                       │
│   Main Thread (prompt loop)                           │
│        │                                             │
│        ├── Regular command   → executes inline        │
│        │                                             │
│        ├── command &         → spawns worker Thread   │
│        │                       (non-blocking)         │
│        │                                             │
│        └── terminate <ID>   → connects to tport       │
│                                sends terminate signal  │
└──────────────────────────────────────────────────────┘
```

**Key Design Decisions:**
- **Dual-port design** separates normal command traffic from termination signals so a blocked transfer never blocks cancellation
- **Command-ID system** maps each `get`/`put` to a unique integer ID for precise termination targeting
- **Periodic checkpointing** every 1000 bytes during transfers lets threads respond to termination without busy-waiting
- **Manual `synchronized` blocks** on shared resources replace thread-safe collections entirely

---

## Features

| Feature | Description |
|---|---|
| 🔀 Multi-client support | Server handles N clients simultaneously via thread-per-client model |
| ⚡ Background execution | Append `&` to any command to run it in a background thread |
| 🛑 Transfer termination | Cancel an in-progress `get` or `put` using its command ID |
| 🧹 Automatic cleanup | Partial files from terminated transfers are deleted automatically |
| 🔒 Manual synchronization | `synchronized` blocks and object monitor locks — no `java.util.concurrent` |
| 📂 Full directory support | `cd`, `mkdir`, `ls`, `pwd` all work correctly across sessions |

---

## Getting Started

### Prerequisites

- Linux (tested on Ubuntu / Nike lab machines)
- Java JDK 8 or higher
- `javac` and `java` available on PATH

Verify your Java installation:
```bash
java -version
javac -version
```

### Compilation

```bash
# Compile all source files
javac *.java
```

Or compile individually:
```bash
javac myftpserver.java
javac myftp.java
```

### Running the Server

```bash
java myftpserver <nport> <tport>
```

**Example:**
```bash
java myftpserver 8080 8081
```

- `nport` — port for normal FTP commands (`get`, `put`, `ls`, etc.)
- `tport` — dedicated port for `terminate` commands only

### Running the Client

```bash
java myftp <server-hostname> <nport> <tport>
```

**Example:**
```bash
java myftp localhost 8080 8081
```

Once connected, you will see the prompt:
```
mytftp>
```

---

## Supported Commands

| Command | Description | Background (`&`) Support |
|---|---|---|
| `get <filename>` | Download a file from server | ✅ Yes |
| `put <filename>` | Upload a file to server | ✅ Yes |
| `delete <filename>` | Delete a file on the server | ✅ Yes |
| `ls` | List files in current server directory | ✅ Yes |
| `cd <directory>` | Change server directory | ❌ No |
| `mkdir <directory>` | Create a directory on server | ✅ Yes |
| `pwd` | Print server working directory | ✅ Yes |
| `terminate <command-ID>` | Terminate a running command by ID | — |
| `quit` | Disconnect from server | — |

**Background execution example:**
```
mytftp> get largefile.zip &
[Command ID: 42] Running in background...
mytftp> ls
file1.txt  file2.zip  largefile.zip
mytftp> terminate 42
Command 42 terminated. Partial file deleted.
mytftp>
```

---

## Concurrency Design

All synchronization is implemented **manually** using Java's built-in `synchronized` keyword and object monitor locks. No classes from `java.util.concurrent` are used anywhere in the codebase.

### Mutex Strategy

| Operation | Locking Required | Reason |
|---|---|---|
| Concurrent `put` to same file | ✅ Yes | Prevents interleaved writes and file corruption |
| Concurrent `put` to different files | ❌ No | Independent resources, no conflict |
| Concurrent `get` (reads) | ❌ No | Read-only, non-destructive |
| `delete` while `get` is active | ✅ Yes | File must not be removed mid-read |
| Command-ID map access | ✅ Yes | Shared across all client handler threads |
| `mkdir` / `ls` / `pwd` | ❌ No | Atomic at OS level or stateless reads |

### Shared State (All Manually Synchronized)

```java
HashMap<Integer, String> commandStatus   // "ACTIVE" or "TERMINATE"
HashMap<String, Object>  fileLocks       // per-filename lock objects
int                      commandCounter  // incremented inside synchronized block
```

### Terminate Flow

```
1. User types:        terminate 42
2. Client opens a new connection to tport
3. Client sends:      "terminate 42"
4. Server terminate listener receives command ID 42
5. Server looks up commandStatus map (inside synchronized block)
6. Sets commandStatus.put(42, "TERMINATE")
7. Worker thread for command 42 checks flag every 1000 bytes
8. Detects "TERMINATE" → stops transfer → deletes partial file
9. Worker thread returns to idle / exits
```

---

## Project Structure

```
.
├── MyFTPServer.java       # Server: dual-port listeners, client handler threads,
│                          #         terminate logic, shared state management
├── MyFTPClient.java       # Client: prompt loop, background threading,
│                          #         terminate relay via tport
└── README.md              # This file
```

---

## Team

**Project Group Members:**
- [Your Name]
- [Partner Name (if applicable)]

> *"This project was done in its entirety by [Project group members names]. We hereby state that we have not received unauthorized help of any form."*

**Course:** Computer Networks — Programming Project 2
**Platform:** Tested on Linux (Nike lab environment)
**Language:** Java (JDK 8+)

---

## Notes

- Each client maintains a **single connection** to `nport` — avoids `cd` conflicts across background threads
- **No `java.util.concurrent` data structures** used anywhere, per project constraints
- Syntax validation is not enforced — valid command syntax is assumed
- User authentication and permission checks are not implemented
- Built and tested exclusively on **Linux**; behavior on Windows/macOS is not guaranteed