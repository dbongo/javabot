package javabot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.rickyclarkson.java.util.TypeSafeList;
import javabot.operations.BotOperation;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

public class Javabot extends PircBot {
    Map map = new HashMap();
    Map channelPreviousMessages = new HashMap();
    BotOperation[] operations;
    private String host, dictHost;
    private int port;
    private String factoidFilename;
    private String javadocSources, javadocBaseUrl;
    private String[] startStrings = null;
    private int authWait;
    private String password;
    private List channels = new TypeSafeList(new ArrayList(), String.class);
    private String htmlFile;
    private List ignores = new ArrayList();
    private PrintWriter factoidLog;


    private Javabot() throws JDOMException, IOException {
        setName("javabot");
        setLogin("javabot");
        setVersion("Javabot 1.4 by Ricky Clarkson"
            + " (ricky_clarkson@yahoo.com) and various"
            + " contributors, based on PircBot by Paul Mutton");
        loadConfig();
        loadFactoids();
    }

    private void loadConfig() throws JDOMException, IOException {
        SAXBuilder reader = new SAXBuilder(true);
        Document document = reader.build(new File("config.xml"));
        Element root = document.getRootElement();
        String verbosity = root.getAttributeValue("verbose");

        setVerbose("true".equals(verbosity));
        loadServerInfo(root);
        loadJavadocInfo(root);
        loadFactoidInfo(root);
        loadDictInfo(root);
        loadChannelInfo(root);
        loadAuthenticationInfo(root);
        loadStartStringInfo(root);
        loadOperationInfo(root);
        loadIgnoreInfo(root);
    }

    private void loadIgnoreInfo(Element root) {
        List ignoreNodes = root.getChildren("ignore");

        Iterator iterator = ignoreNodes.iterator();
        while(iterator.hasNext()) {
            Element node = (Element)iterator.next();
            ignores.add(node.getAttributeValue("name"));
        }
    }

    private void loadOperationInfo(Element root) {
        List operationNodes = root.getChildren("operation");
        Iterator iterator = operationNodes.iterator();
        operations = new BotOperation[operationNodes.size()];
        int index = 0;
        while(iterator.hasNext()) {
            Element node = (Element)iterator.next();
            try {
                Class operationClass = Class.forName(
                    node.getAttributeValue("class"));
                operations[index] = (BotOperation)operationClass.newInstance();
                System.out.println(operations[index]);
            } catch(Exception exception) {
                throw new RuntimeException(exception);
            }
            index++;
        }
    }

    private void loadStartStringInfo(Element root) {
        List startNodes = root.getChildren("message");
        Iterator iterator = startNodes.iterator();
        startStrings = new String[startNodes.size()];
        int index = 0;
        while(iterator.hasNext()) {
            Element node = (Element)iterator.next();
            startStrings[index++] = node.getAttributeValue("tag");
        }
    }

    private void loadAuthenticationInfo(Element root) {
        Element authNode = root.getChild("auth");
        authWait = Integer.parseInt(authNode.getAttributeValue("wait"));
        setNickPassword(authNode.getAttributeValue("password"));

        Element nickNode = root.getChild("nick");
        setName(nickNode.getAttributeValue("name"));
    }

    private void loadChannelInfo(Element root) {
        List channelNodes = root.getChildren("channel");
        Iterator iterator = channelNodes.iterator();
        while(iterator.hasNext()) {
            Element node = (Element)iterator.next();
            channels.add(node.getAttributeValue("name"));
        }
    }

    private void loadDictInfo(Element root) {
        Element dictNode = root.getChild("dict");
        dictHost = dictNode.getAttributeValue("host");
    }

    private void loadFactoidInfo(Element root) throws IOException {
        Element factoidsNode = root.getChild("factoids");
        factoidFilename =
            factoidsNode.getAttributeValue("filename");
        htmlFile = factoidsNode.getAttributeValue("htmlfilename");
        factoidLog = new PrintWriter(new FileWriter(
            factoidsNode.getAttributeValue("factoidChangeLog"), true));
    }

    private void loadJavadocInfo(Element root) {
        Element javadocNode = root.getChild("javadoc");
        javadocSources = root.getAttributeValue("source-list");
        javadocBaseUrl = javadocNode.getAttributeValue("base-url");
    }

    private void loadServerInfo(Element root) {
        Element serverNode = root.getChild("server");
        host = serverNode.getAttributeValue("name");
        port = Integer.parseInt(serverNode.getAttributeValue("port"));
    }

    public static void main(String[] args) throws IOException, JDOMException {
        System.out.println("Starting Javabot");
        Javabot bot = new Javabot();
        bot.setMessageDelay(2000);
        bot.connect();
    }

    private void connect() {
        boolean connected = false;
        try {
            while(!connected) {
                try {
                    connect(host, port);
                    sendRawLine("PRIVMSG NickServ :identify "
                        + getNickPassword());
                    Thread.sleep(authWait);
                    Iterator iterator = channels.iterator();
                    while(iterator.hasNext()) {
                        joinChannel((String)iterator.next());
                    }
                    connected = true;
                } catch(Exception exception) {
                    exception.printStackTrace();
                }
                Thread.sleep(1000);
            }
        } catch(InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void onMessage(String channel, String sender, String login,
        String hostname, String message) {
        String[] startStrings =
            {"~", "javabot: ", "javabot, ", "javabot "};
        for(int a = 0; a < startStrings.length; a++) {
            int length = startStrings[a].length();
            if(message.startsWith(startStrings[a])) {
                handleAnyMessage
                    (channel,
                        sender,
                        login,
                        hostname,
                        message.substring(length).trim());
                return;
            }
        }
    }

    public List getResponses(String channel, String sender, String login,
        String hostname, String message) {
        for(int a = 0; a < operations.length; a++) {
            List messages = operations[a].handleMessage(new BotEvent(this,
                channel, sender, login, hostname, message));
            if(messages.size() != 0) {
                return messages;
            }
        }
        return null;
    }

    private void handleAnyMessage(String channel, String sender, String login,
        String hostname, String message) {
        List messages = getResponses(channel, sender, login, hostname, message);
        if(messages != null) {
            Iterator iterator = messages.iterator();
            while(iterator.hasNext()) {
                Message nextMessage = (Message)iterator.next();
                if(nextMessage.isAction()) {
                    sendAction
                        (nextMessage.getDestination(),
                            nextMessage.getMessage());
                } else {
                    sendMessage
                        (nextMessage.getDestination(),
                            nextMessage.getMessage());
                }
            }
            channelPreviousMessages.put(channel, message);
        }
    }

    public void onPrivateMessage(String sender, String login, String hostname,
        String message) {
        handleAnyMessage(sender, sender, login, hostname, message);
    }

    public void onInvite(String targetNick, String sourceNick,
        String sourceLogin, String sourceHostname, String channel) {
        if(channels.contains(channel)) {
            joinChannel(channel);
        }
    }

    public void onDisconnect() {
        connect();
    }

    public void addFactoid(String sender, String key, String value) {
        map.put(key, value);
        saveFactoids();
        logFactoidChange(sender, key, value, "added");
    }

    private void logFactoidChange(String sender, String key,
        String value, String operation) {
        factoidLog.println("<br> " + new Date() + ": " + sender + " "
            + operation + " " + key + " = '" + value + "'");
        factoidLog.flush();
    }

    public boolean hasFactoid(String key) {
        return map.containsKey(key);
    }

    public String getFactoid(String key) {
        return (String)map.get(key);
    }

    public void forgetFactoid(String sender, String key) {
        String old = (String)map.get(key);
        map.remove(key);
        saveFactoids();
        logFactoidChange(sender, key, old, "removed");
    }

    public Map getMap() {
        return map;
    }

    public String getPreviousMessage(String channel) {
        if(channelPreviousMessages.containsKey(channel)) {
            return (String)channelPreviousMessages.get(channel);
        }
        return "";
    }

    public boolean isOnSameChannelAs(String nick) {
        String[] channels = getChannels();
        for(int a = 0; a < channels.length; a++) {
            if(userIsOnChannel(nick, channels[a])) {
                return true;
            }
        }
        return false;
    }

    public boolean userIsOnChannel(String nick, String channel) {
        User[] users = getUsers(channel);
        for(int a = 0; a < users.length; a++) {
            if(users[a].getNick().toLowerCase().equals(nick.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void saveFactoids() {
        try {
            FileOutputStream fos = new FileOutputStream(factoidFilename);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(map);
            oos.close();
            fos.close();
            dumpHTML();
        } catch(Exception exception) {
            exception.printStackTrace();
        }
    }

    private void dumpHTML() {
        Iterator iterator = new TreeSet(map.keySet()).iterator();
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(htmlFile));
            writer.println(new StringBuffer()
                .append("<html><body><table ")
                .append("border=\"1\"><tr><th>")
                .append("factoid</th><th>value</th></tr>")
                .toString());
            while(iterator.hasNext()) {
                String factoid = (String)iterator.next();
                String value = (String)map.get(factoid);
                value = value.replaceAll("<", "&lt;");
                value = value.replaceAll(">", "&gt;");
                writer.println("<tr><td>" + factoid
                    + "</td><td>" + value + "</td></tr>");
            }
            writer.println("</table></body></html>");
            writer.flush();
            writer.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFactoids() {
        try {
            FileInputStream fis =
                new FileInputStream(factoidFilename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            map = (Map)ois.readObject();
            ois.close();
            fis.close();
        } catch(Exception exception) {
            exception.printStackTrace();
        }
        Set keySet = new HashSet(map.keySet());
        Iterator iterator = keySet.iterator();
        while(iterator.hasNext()) {
            String next = (String)iterator.next();
            if(!next.equals(next.toLowerCase())) {
                String value = (String)map.get(next);
                map.remove(next);
                map.put(next.toLowerCase(), value);
            }
        }
    }

    /**
     * @return the number of factoids that this bot stores.
     */
    public int getNumberOfFactoids() {
        return map.size();
    }

    public String getDictHost() {
        return dictHost;
    }

    public String getJavadocSources() {
        return javadocSources;
    }

    public String getJavadocBaseUrl() {
        return javadocBaseUrl;
    }

    public void setNickPassword(String password) {
        this.password = password;
    }

    public String getNickPassword() {
        return password;
    }

    public boolean isValidSender(String sender) {
        return ignores.contains(sender);
    }

    public void addIgnore(String sender) {
        ignores.add(sender);
    }
}
