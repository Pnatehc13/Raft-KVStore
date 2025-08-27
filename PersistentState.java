import java.util.ArrayList;
import java.util.List;
import com.google.gson.*;
import java.io.*;

public class PersistentState {
    int currentTerm;
    Integer votedFor;
    List<LogEntry> log = new ArrayList<>();
    int id;
    Gson gson = new Gson();

    PersistentState(int id) {
        this.id = id;
        load(); // try loading from file immediately
    }

    PersistentState(int id,int currentTerm,Integer votedFor , List<LogEntry> log)
    {
        this.id = id;
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.log = log;
    }

    void load()
    {
        File file = new File("node"+id+".json");
        if(file.exists())
        {
            try(FileReader reader = new FileReader(file))
            {
                PersistentState x = gson.fromJson(reader,PersistentState.class);
                this.log = x.log;
                this.votedFor = x.votedFor;
                this.currentTerm = x.currentTerm;
            }
            catch (IOException e) {e.printStackTrace();}
        }
        else
        {
            this.currentTerm = 0;
            this.votedFor = null;
            this.log = new ArrayList<>();
        }

    }
    void save()
    {
        try (FileWriter writer = new FileWriter("node"+id+".json")) { gson.toJson(this, writer); }
        catch (IOException e) { e.printStackTrace(); }
    }
}
