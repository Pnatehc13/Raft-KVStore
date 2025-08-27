import java.util.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int numNodes = 5;
        Map<Integer, RaftNode> nodes = new HashMap<>();
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) ids.add(i);

        // initialize nodes
        for (int i = 0; i < numNodes; i++) {
            RaftNode n = new RaftNode(i, ids);
            nodes.put(i, n);
        }
        for (RaftNode n : nodes.values()) n.setPeers(nodes);

        // set random election timers
        for (RaftNode n : nodes.values()) n.resetElectionTimer();

        System.out.println("---- Starting cluster ----");

        RaftNode leader = null;
        while (leader == null) {
            // let each node progress
            for (RaftNode n : nodes.values()) {
                n.tick();
            }

            // check if leader elected
            for (RaftNode n : nodes.values()) {
                if (n.role == Role.Leader) {
                    leader = n;
                    break;
                }
            }

            Thread.sleep(50);
        }

        System.out.println("Leader elected: Node " + leader.id);
        for (RaftNode n : nodes.values()) {
            System.out.println("Node " + n.id + " role=" + n.role + " term=" + n.state.currentTerm);
        }

        // ---- Simulate client requests ----
        System.out.println("\n---- Sending client commands ----");
        for (int i = 1; i <= 3; i++) {
            if(i == 2){
                leader.role = Role.Follower;
            }
            Command cmd = new Command("set x=" + i);
            System.out.println("Client sends: " + cmd.getData());
            leader.onClientCommand(cmd);

            // Let cluster replicate
            for (int t = 0; t < 10; t++) {
                for (RaftNode n : nodes.values()) {
                    n.tick();
                }
                Thread.sleep(50);
            }
        }

        // ---- Print final logs & KV state ----
        System.out.println("\n---- Final State ----");
        for (RaftNode n : nodes.values()) {
            System.out.println("Node " + n.id + " log:");
            for (int j = 0; j < n.log.size(); j++) {
                System.out.println("  [" + j + "] " + n.log.get(j).getCommand());
            }
        }
        leader.role = Role.Follower;
        leader = null;

    }
}
