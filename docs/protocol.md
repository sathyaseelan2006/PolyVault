# PolyVault Socket Protocol

Each request starts with one UTF-8 line:

```text
COMMAND key=value key="quoted value"
```

For `UPLOAD`, the request line is followed by exactly `size` raw bytes.

## Commands

Create a node:

```text
CREATE_NODE parentId=0 type=WORKSPACE title="Projects"
```

Upload a file:

```text
UPLOAD parentId=1 filename=README.md title="PolyVault README" type=doc size=1024
```

Download latest version:

```text
DOWNLOAD fileId=1 version=latest
```

List children:

```text
LIST parentId=0
```

Graph JSON:

```text
GRAPH
```

Delete a file record:

```text
DELETE fileId=1
```

## Responses

Simple response:

```text
OK message="Created"
ERR message="Missing fileId"
```

Body response:

```text
OK size=123 contentType=application/json

<body bytes>
```
