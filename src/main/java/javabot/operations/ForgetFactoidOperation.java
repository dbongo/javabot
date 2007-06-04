package javabot.operations;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import javabot.BotEvent;
import javabot.Database;
import javabot.Message;
import javabot.util.Arrays;

public class ForgetFactoidOperation implements BotOperation {
    private final Database database;

    public ForgetFactoidOperation(final Database factoidDatabase) {
        database = factoidDatabase;
    }

    public List<Message> handleMessage(BotEvent event) {
        List<Message> messages = new ArrayList<Message>();
        String channel = event.getChannel();
        String message = event.getMessage();
        String sender = event.getSender();
        String[] messageParts = message.split(" ");
        if("forget".equals(messageParts[0])) {
            int length = Array.getLength(messageParts);
            String key = Arrays.toString(Arrays.subset(messageParts, 1, length), " ");
            key = key.toLowerCase();
            if(database.hasFactoid(key)) {
                messages.add(new Message(channel, "I forgot about " + key
                    + ", " + sender + ".", false));
                database.forgetFactoid(sender, key);
            } else {
                messages.add(new Message(channel, "I never knew about "
                    + key + " anyway, " + sender + ".", false));
            }
        }
        return messages;
    }

    public List<Message> handleChannelMessage(BotEvent event) {
        return new ArrayList<Message>();
    }
}