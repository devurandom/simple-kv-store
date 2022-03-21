# Simple Key Value Store

This is a simple in-memory key/value store with basic transaction support.

## Usage

`clj -M:run` starts a server on TCP port 3030.  `clj -M:run --port 1234` or `env PORT=1234 clj -M:run` start it on port 1234.

## Overview

The command line is parsed in the `core` namespace.
The code to start a server and handle incoming connections is in the `server` namespace.
The `handler` namespace contains the code to parse the wire-protocol and handle the commands.

## Storage

Key / value pairs are stored in a "context" map.  One global "root" context exists.
It is shared between all clients.

To implement transactions, these contexts are stacked using a list that is attached to a "session" storage that is separate for each client.
`SET` and `DELETE` apply to the top (first in the list).
`GET` tries all context maps in the list until it finds the key.
If it cannot find the key, it looks it up in the root context.  
To make a key visible to other clients the transaction has to be `COMMIT`ed to the root context.

Transactions can be nested using `BEGIN`.
In that case `COMMIT` will propagate the values one level up the stack (instead of immediately applying the transaction to the root context).
The stack always contains at least one transaction to keep session-local keys separate from keys committed to the global store.
`ROLLBACK` simply discards the top-level transaction.

`EXIT` stops processing commands from the client and closes the connection.
