import java.util.HashMap;
import java.util.Map;

public class KVStore {
    Map<String,String> store = new HashMap<>();
    public void applyCommand(String c)
    {
        if(c.startsWith("PUT "))
        {
            String[] pairs = c.substring(4).split("=",2);
            store.put(pairs[0],pairs[1]);
        }else if(c.startsWith("DEL "))
        {
            store.remove(c.substring(4));
        }
    }
    public void displayStore()
    {
        for (Map.Entry<?, ?> entry : store.entrySet()) {
            System.out.printf("%-15s : %s%n", entry.getKey(), entry.getValue());
        }
    }

}
