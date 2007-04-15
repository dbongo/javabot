package javabot.operations;

import com.rickyclarkson.java.util.Arrays;
import javabot.BotEvent;
import javabot.Message;
import javabot.dao.FactoidDao;
import javabot.dao.model.factoids;

import java.util.ArrayList;
import java.util.List;

/**
 * A little more intelligence creeping in. This searches the entire set of key/value pairs to find
 * out which factoid has the most matches for the inputs provided.
 */
public class GuessOperation2 implements BotOperation {
    private String[] ignoreList = {"you", "and", "are", "to", "that", "your", "do", "have", "a", "the", "be", "but", "can", "i", "who", "how", "get", "by", "is", "of", "out", "me", "an", "for", "use", "he", "she", "it"};
    private String ignoreString = Arrays.toString(ignoreList, "|");
    private FactoidDao m_dao = null;

    public GuessOperation2(FactoidDao factoidDao) {
        m_dao = factoidDao;
    }

    /**
     * @see javabot.operations.BotOperation#handleMessage(javabot.BotEvent)
     */
    public List<Message> handleMessage(BotEvent event) {
        List<Message> messages = new ArrayList<Message>();
        String message = event.getMessage().toLowerCase();
        String channel = event.getChannel();
        if (!message.startsWith("guess ")) {
            return messages;
        }
        message = message.substring("guess ".length());
        for (String anIgnoreList : ignoreList) {
            message = message.replaceAll(" " + anIgnoreList + " ", " ");
            message = message.replaceAll("^(" + anIgnoreList + ") ", " ");
            message = message.replaceAll("^(" + anIgnoreList + ")$", "");
            message = message.replaceAll(" (" + anIgnoreList + ")$", " ");
        }
        message = message.toLowerCase();
        String[] words = message.split(" +");
        words = (String[]) Arrays.removeAll(words, " ");
        words = (String[]) Arrays.removeAll(words, "");
        int maxMatches = 0;
        String bestKey = "";
        for (factoids factoid : m_dao.getFactoids()) {
            String nextKey = factoid.getName();
            //			String nextValue=database.getFactoid(nextKey);
            String next = nextKey;
            next = next.toLowerCase();
            if (next.indexOf("karma ") == 0) {
                continue;
            }
            for (String word : words) {
                int currentMatches = 0;
                if (word.length() < 3) {
                    continue;
                }
                for (int b = 0; b < next.length(); b++) {
                    int index = next.indexOf(word);
                    if (index == -1) {
                        continue;
                    }
                    currentMatches++;
                    b += word.length();
                }
                if (currentMatches > maxMatches) {
                    bestKey = nextKey;
                    maxMatches = currentMatches;
                }
            }
        }
        if (bestKey.equals("")) {
            messages.add(new Message(channel, "No appropriate factoid found.", false));
            return messages;
        }
        messages.add(new Message(channel, "I guess the factoid '" + bestKey + "' might be appropriate:", false));
        messages.addAll(new GetFactoidOperation2(m_dao).handleMessage(new BotEvent(event.getChannel(), event.getSender(), event.getLogin(), event
                .getHostname(), bestKey)));

        return messages;
    }

    public List<Message> handleChannelMessage(BotEvent event) {
        return new ArrayList<Message>();
    }
}