class RequestVoteRequest {
    int term;
    int candidateId;
    int lastLogIndex;
    int lastLogTerm;
    RequestVoteRequest(int t,int id,int lli,int llt)
    {
        this.term = t;
        this.candidateId = id;
        this.lastLogIndex = lli;
        this.lastLogTerm = llt;
    }
}

class RequestVoteResponse {
    int term;
    boolean voteGranted;
}