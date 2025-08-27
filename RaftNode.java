import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RaftNode {
    boolean alive = true;
    List<LogEntry> log ;
    Role role = Role.Follower;
    int id;
    List<Integer> peerIds = new ArrayList<>();
    Map<Integer,RaftNode>peers = new HashMap<>();
    long lastHeartbeatSent;

    long electionDeadlineMs;
    long heartbeatIntervalMs = 100;
    public static long now() {return System.currentTimeMillis();}

    PersistentState state = new PersistentState(id);
    KVStore kv = new KVStore();

    //
    int commitIndex = -1;
    int lastApplied = -1;

    //
    Map<Integer, Integer> nextIndex = new HashMap<>();
    Map<Integer, Integer> matchIndex = new HashMap<>();


    RaftNode(int id)
    {
        this.id = id;
    }
    RaftNode(int id, List<Integer> allIds) {
        this.id = id;
        this.state = new PersistentState(id);
        // on construction it tries to load term, votedFor, log from file
        this.log = this.state.log;
        this.state.load();
    }

    // convenience getters
    int currentTerm() { return state.currentTerm; }
    Integer votedFor() { return state.votedFor; }
    List<LogEntry> log() { return state.log; }
    boolean handleRequestVote(RequestVoteRequest rpc) {
        int lastLogIndex = log.size() - 1;
        int lastLogTerm  = lastLogIndex >= 0 ? log.get(lastLogIndex).getTerm() : 0;

        // Reject if candidate’s term is old
        if (rpc.term < state.currentTerm) return false;

        // If term is newer, step down
        if (rpc.term > state.currentTerm) {
            becomeFollower(rpc.term);
        }

        // Check if candidate's log is at least as up-to-date
        boolean logIsUpToDate =
                (rpc.lastLogTerm > lastLogTerm) ||
                        (rpc.lastLogTerm == lastLogTerm && rpc.lastLogIndex >= lastLogIndex);

        // Grant vote if not voted yet OR already voted for this candidate
        if ((state.votedFor == null || state.votedFor == rpc.candidateId) && logIsUpToDate) {
            state.votedFor = rpc.candidateId;
            resetElectionTimer();
            System.out.println("Node " + id + " voted for " + rpc.candidateId + " in term " + state.currentTerm);
            setVotedFor(rpc.candidateId);
            return true;
        }

        return false;
    }

     AppendEntriesResponse handleAppendEntries(AppendEntriesRequest rpc) {
        // 1. Reply false if term < state.currentTerm
        if (rpc.term < state.currentTerm) return new AppendEntriesResponse(state.currentTerm, false);
        // 2. If term is newer, update state.currentTerm and step down to follower
        if (rpc.term > state.currentTerm) {
            becomeFollower(rpc.term);
        } else if (rpc.term == state.currentTerm && rpc.leaderId != this.id) {
            becomeFollower(state.currentTerm);
        }
        // 3. Reply false if log doesn’t contain an entry at prevLogIndex
        //    whose term matches prevLogTerm
        if (rpc.prevLogIndex >= 0) {
            if (rpc.prevLogIndex >= log.size()) {
                // follower is missing entries
                return new AppendEntriesResponse(state.currentTerm, false, -1, log.size());
            }
            int localTerm = log.get(rpc.prevLogIndex).getTerm();
            if (localTerm != rpc.prevLogTerm) {
                // conflict: return the conflicting term + first index of that term
                int firstIndexOfTerm = rpc.prevLogIndex;
                while (firstIndexOfTerm > 0 && log.get(firstIndexOfTerm - 1).getTerm() == localTerm) {
                    firstIndexOfTerm--;
                }
                return new AppendEntriesResponse(state.currentTerm, false, localTerm, firstIndexOfTerm);
            }
        }
        // 4. If an existing entry conflicts with a new one (same index, different term),
        //    delete the existing entry and all that follow it
        int index = rpc.prevLogIndex + 1;
        boolean mismatchFound = false;

        for (LogEntry e : rpc.entries) {
            if (index < log.size() && !mismatchFound) {
                if (log.get(index).getTerm() != e.getTerm()) {
                    state.log.subList(index, state.log.size()).clear();
                    mismatchFound = true;
                    appendLog(e);
                }
            } else {appendLog(e);}
            index++;
        }
        // 5. If leaderCommit > commitIndex, update commitIndex
        // 5. Update commitIndex
        int lastNewEntryIndex = log.size() - 1;
        if (rpc.leaderCommit > commitIndex) {
            commitIndex = Math.min(rpc.leaderCommit, lastNewEntryIndex);
            applyCommitted();
        }

        // 6. Always reset election timer on valid AppendEntries
        resetElectionTimer();
         return new AppendEntriesResponse(state.currentTerm, true);
    }
    void resetElectionTimer(){
        electionDeadlineMs = now() +  ThreadLocalRandom.current().nextInt(1500, 3001);
    }
    void startElection() {
        System.out.println("Node " + id + " starting election for term " + state.currentTerm);
        role = Role.Candidate;
        state.currentTerm++;
        state.votedFor = id;
        int votes = 1;
        setCurrentTerm(state.currentTerm);
        setVotedFor(state.votedFor);

        resetElectionTimer();
        int lastLogIndex = log.isEmpty() ? -1 : log.size() - 1;
        int lastLogTerm  = log.isEmpty() ? 0  : log.get(lastLogIndex).getTerm();
        RequestVoteRequest rpc = new RequestVoteRequest(state.currentTerm, id, lastLogIndex, lastLogTerm);
        for (int peerId : peerIds) {
            if(peerId == id)continue;
            boolean granted = sendRequestVote(peers.get(peerId), rpc);
            if (granted) votes++;
        }
        System.out.println("Node " + id + " got " + votes + " votes in term " + state.currentTerm);
        if (votes > peerIds.size() / 2) {
            becomeLeader();
        }
    }
    void becomeFollower(int newTerm) {
        role = Role.Follower;
        state.currentTerm = newTerm;
        setCurrentTerm(state.currentTerm);
        state.votedFor = null;
        resetElectionTimer();
    }
    void becomeLeader() {
        role = Role.Leader;

        for (int peerId : peerIds) {
            nextIndex.put(peerId, log.size());
            matchIndex.put(peerId,-1);
        }
        matchIndex.put(id,log.size()-1);

        sendHeartbeatsOnce(); // send empty AppendEntries to all peers
        lastHeartbeatSent = now();
    }
    boolean sendRequestVote(RaftNode peer, RequestVoteRequest req) {
        return peer.handleRequestVote(req);
    }
    AppendEntriesResponse sendAppend(int peerId, AppendEntriesRequest req) {
        RaftNode peer = peers.get(peerId); // Map lookup by ID
        if (peer == null) return null;    // in case ID not found
        return peer.handleAppendEntries(req);// stub
    }
    void sendHeartbeatsOnce() {
        for (int peerId : peerIds) {
            int prevLogIndex = nextIndex.get(peerId) - 1;
            int prevLogTerm = prevLogIndex >= 0 ? log.get(prevLogIndex).getTerm() : 0;

            AppendEntriesRequest rpc = new AppendEntriesRequest(
                    state.currentTerm,
                    id,
                    prevLogIndex,
                    prevLogTerm,
                    new ArrayList<>(),
                    commitIndex
            );

            // Send RPC in-process
            AppendEntriesResponse res = sendAppend(peerId, rpc);

            if (res.term > state.currentTerm) {
                becomeFollower(res.term);
                return;
            }
            if (!res.success) {
                if (res.conflictTerm != -1) {
                    // check if leader has that term
                    int lastIndexWithConflictTerm = -1;
                    for (int i = log.size() - 1; i >= 0; i--) {
                        if (log.get(i).getTerm() == res.conflictTerm) {
                            lastIndexWithConflictTerm = i;
                            break;
                        }
                    }
                    if (lastIndexWithConflictTerm != -1) {
                        nextIndex.put(peerId, lastIndexWithConflictTerm + 1);
                    } else {
                        nextIndex.put(peerId, res.conflictIndex);
                    }
                } else {
                    nextIndex.put(peerId, res.conflictIndex);
                }
            }

        }
        tryAdvanceCommitIndex();
    }
    void tick() {
        long currentime = now();

        // 1. Followers/Candidates → check election timeout
        if (role != Role.Leader && currentime >= electionDeadlineMs) {
            startElection();
        }

        // 2. Leader → send heartbeat if interval elapsed
        if (role == Role.Leader && currentime - lastHeartbeatSent >= heartbeatIntervalMs) {
            sendHeartbeatsOnce();
            lastHeartbeatSent = currentime;
        }
    }
    int onClientCommand(Command command) {
        if(role != Role.Leader)return -1;
        int newindex = log.size();
        appendLog(new LogEntry(state.currentTerm,command));
        replicateToAllFollowers();
        return newindex;
    }
    void replicateToAllFollowers() {
        for(int peerid:peerIds)
        {
            if(peerid == id)continue;
            int ni = nextIndex.get(peerid);
            int prevLogIndex = ni -1;
            int prevLogTerm = (prevLogIndex >= 0 ? log.get(prevLogIndex).getTerm(): 0);
            ArrayList<LogEntry> entries = new ArrayList<>(log.subList(ni,log.size()));
            AppendEntriesRequest rpc = new AppendEntriesRequest(state.currentTerm,id,prevLogIndex,prevLogTerm,entries,commitIndex);
            AppendEntriesResponse res = sendAppend(peerid,rpc);
            if(res.term>state.currentTerm){becomeFollower(res.term);return;}
            if(res.success)
            {
                matchIndex.put(peerid,prevLogIndex+entries.size());
                nextIndex.put(peerid,matchIndex.get(peerid)+1);
            }
            else nextIndex.put(peerid,Math.max(0,nextIndex.get(peerid)-1));
        }
        tryAdvanceCommitIndex();
    }
    void tryAdvanceCommitIndex() {
        for (int N = log.size() - 1; N > commitIndex; N--)
        {
            if (log.get(N).getTerm() != state.currentTerm) continue;

            int count = 1;
            for (int peerid : peerIds)
            {
                if(peerid == id)continue;
                Integer mi = matchIndex.get(peerid);
                if (mi != null && mi >= N) count++;
            }

            if (count > peerIds.size() / 2) {
                commitIndex = N;
                applyCommitted();
                break;
            }
        }
    }
    void applyCommitted(){
        while (lastApplied < commitIndex) {
            lastApplied++;
            LogEntry entry = log.get(lastApplied);
            applyToStateMachine(entry.getCommand()); // Your KV store or placeholder
        }
    }
    void applyToStateMachine(Command command) {
        kv.applyCommand(command.toString());
        System.out.println("Node " + id + " applied command: " + command);
    }

    public void setPeers(Map<Integer,RaftNode> peers) {
        this.peers = peers;
    }

    public void handleClientRequest(Command cmd) {
        if (role != Role.Leader) {
            System.out.println("Redirect: Node " + id + " is not leader.");
            return;
        }

        // 1. Append to leader’s log
        appendLog(new LogEntry(state.currentTerm,cmd));

        // 2. Apply to leader’s KV store
        kv.applyCommand(cmd.getData());

        // 3. Replicate to followers
        for (RaftNode peer : peers.values()) {
            peer.replicateCommand(cmd);
        }
    }
    void setCurrentTerm(int newTerm) {
        state.currentTerm = newTerm;
        state.save();
    }

    void setVotedFor(Integer candidateId) {
        state.votedFor = candidateId;
        state.save();
    }

    void appendLog(LogEntry entry) {
        state.log.add(entry);
        state.save();
    }

    public void replicateCommand(Command cmd) {
        appendLog(new LogEntry(state.currentTerm,cmd));
        kv.applyCommand(cmd.getData());
    }

}




class LogEntry {
    private int term;
    private Command command;

    public LogEntry(int term, Command command) {
        this.term = term;
        this.command = command;
    }

    public int getTerm() { return term; }
    public Command getCommand() { return command; }

}
class Command {
    private String data; // or byte[] data

    public Command(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
    @Override
    public String toString() {
        return data;
    }

}
enum Role {
    Leader,
    Candidate,
    Follower;
}