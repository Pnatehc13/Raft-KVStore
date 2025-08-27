# Distributed Key-Value Store (Raft-based)

A simple distributed key-value store implemented in **Java**, built on top of the [Raft consensus algorithm](https://raft.github.io/).  
This project was created as a learning exercise to understand how distributed consensus and replication work in practice.

## Features
- ✅ **Leader election** — nodes elect a leader using Raft
- ✅ **Log replication** — client commands (`PUT`/`GET`) replicated across cluster
- ✅ **Persistence** — state (logs, term, votes) is saved to disk for recovery
- ✅ **Basic KV Store** — clients can store and retrieve values

## Project Structure
- `RaftNode.java` – core logic for a Raft peer (follower, candidate, leader)
- `RequestVoteRPC.java` – defines log entries with term + command
- `KVStore.java` – client-facing key-value API backed by Raft
- `AppendEntriesRPC.java` – applies committed log entries to update the store
- `PersistenceState.java` – handles saving/loading node state from disk

## Running
1. Clone the repo  
   ```bash
   git clone https://github.com/yourusername/Raft-KVStore.git
   cd Raft-KVStore
