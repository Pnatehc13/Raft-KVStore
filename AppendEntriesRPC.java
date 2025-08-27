import java.util.ArrayList;
import java.util.List;

class AppendEntriesRequest {
    int term;
    int leaderId;
    int prevLogIndex;

    int prevLogTerm;
    List<LogEntry> entries;
    int leaderCommit;

    AppendEntriesRequest(   int currentTerm, int leaderId, int prevLogIndex, int prevLogTerm, ArrayList<LogEntry> entry,int commitIndex)
    {
        this.term = currentTerm;
        this.leaderId = leaderId;
        this.prevLogTerm = prevLogTerm;
        this.prevLogIndex = prevLogIndex;
        this.entries = entry;
        this.leaderCommit = commitIndex;
    }

}
class AppendEntriesResponse {
    int term;
    boolean success;
    int conflictTerm;   // term of the conflicting entry
    int conflictIndex;  // index of first entry with that term

    AppendEntriesResponse(int term, boolean success) {
        this.term = term;
        this.success = success;
        this.conflictTerm = -1;
        this.conflictIndex = -1;
    }

    AppendEntriesResponse(int term, boolean success, int conflictTerm, int conflictIndex) {
        this.term = term;
        this.success = success;
        this.conflictTerm = conflictTerm;
        this.conflictIndex = conflictIndex;
    }
}