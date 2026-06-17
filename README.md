# PolyVault

PolyVault is a personal distributed file storage and knowledge graph system in Java.

This first implementation slice includes:

- Multithreaded Java socket server
- Simple custom text protocol
- Chunked upload/download
- GZIP compression strategy
- Versioned file storage
- Flexible vault tree nodes for workspaces, branches, folders, projects, notes, and files
- Lightweight graph JSON endpoint using `com.sun.net.httpserver.HttpServer`

## Run

Install a JDK with `javac` and Maven on PATH, then:

```powershell
mvn compile
java -cp target/classes com.polyvault.App server
```

In another terminal:

```powershell
java -cp target/classes com.polyvault.App client node 0 WORKSPACE "Projects"
java -cp target/classes com.polyvault.App client upload .\README.md 1 "PolyVault README"
java -cp target/classes com.polyvault.App client list 0
java -cp target/classes com.polyvault.App client graph
```

Graph API:

```text
http://localhost:8080/api/graph
```

PolyVault Studio UI:

Open `frontend/index.html` after starting the server. The Studio lets you create workspaces, branches, projects, notes, upload files, inspect nodes, and download stored files from the browser.

Socket server:

```text
localhost:5050
```

## Data Layout

```text
data/
  metadata/
    nodes.tsv
    files.tsv
    versions.tsv
  storage/
    file-1/
      v1.gz
      v2.gz
```
