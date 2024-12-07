package dev.nebula;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

class Args {
    @Parameter(names = {"--username", "-u"}, description = "username", required = true)
    public String USERNAME;

    @Parameter(names = {"--password", "-p"}, description = "password", required = true)
    public String PASSWORD;

    @Parameter(names = {"--OWNER", "-o"}, description = "owner", required = false)
    public String OWNER;

    @Parameter(names = {"--btn-x", "-bx"}, description = "button x", required = false)
    public int BTN_X;

    @Parameter(names = {"--btn-y", "-by"}, description = "button y", required = false)
    public int BTN_Y;

    @Parameter(names = {"--btn-z", "-bz"}, description = "button z", required = false)
    public int BTN_Z;

    @Parameter(names = {"--btn-dir", "-bd"}, description = "button direction", required = false)
    public int BTN_DIR;

    @Parameter(names = {"--msg-delay", "-md"}, description = "message delay", required = false)
    public Long MSG_DELAY;

    @Parameter(names = {"--msg-file", "-mf"}, description = "message file", required = false)
    public String MSG_FILE;

    @Parameter(names = "--help", help = true)
    public boolean help;

    @Parameter(names = {"--bot", "-b"})
    public boolean bot;

}

public class MinecraftClientTitleTest {

    public static final Logger log = LoggerFactory.getLogger(MinecraftClientTitleTest.class);
    public static Args arguments = new Args();
    private static final String HOST = "2b2t.xin"; // 服务器地址
    private static final int PORT = 25565; // 服务器端口
    public static String server = "";
    public static boolean logined = false;
    public static ArrayList<String> messages;
    public static int message_index = 0;
    public static int entity_id;
    public static Long last_message_time = System.currentTimeMillis();
    private static Long t = System.currentTimeMillis();
    public static Map<String, String> players = new HashMap<>();
    private static final JsonObject problem = JsonParser.parseString(new BufferedReader(new InputStreamReader(Objects.requireNonNull(MinecraftClientTitleTest.class.getClassLoader().getResourceAsStream("problems.json")), StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"))).getAsJsonObject();

    public static ArrayList<String> to_strings(Component component) {
        ArrayList<String> result = new ArrayList<>();
        String content = component.toString().replaceFirst("^TextComponentImpl\\{content=\"", "").split("\", style=")[0];
        if (!content.isEmpty()) result.add(content);
        if (component.children().isEmpty()) {
            return result;
        }
        component.children().forEach(component1 -> {
            result.addAll(to_strings(component1));
        });
        return result;
    }

    public static SessionAdapter heart_beat = new SessionAdapter() {
        @Override
        public void packetReceived(Session session, Packet packet) {
        }
    };

    public static SessionAdapter auto_message = new SessionAdapter() {
        @Override
        public void packetReceived(Session session, Packet packet) {
            if (System.currentTimeMillis() - last_message_time < arguments.MSG_DELAY) return;
            last_message_time = System.currentTimeMillis();
            log.info("message");
            message_index++;
            message_index %= messages.size();
            String message = messages.get(message_index);

            if (message.contains("%player")) {
                if (players.isEmpty()) return;
                int playerIndex = new Random().nextInt(players.size());
                message = message.replace("%player", players.values().toArray()[playerIndex].toString());
            }
            if (message.startsWith("/")) {
                session.send(new ServerboundChatCommandPacket(
                    message.substring(1)
                ));
            }
            else {
                sendCommand(session, message);
            }
        }
    };

    public static SessionAdapter auto_join = new SessionAdapter() {
        @Override
        public void packetReceived(Session session, Packet packet) {
            if (server.equals("login") && logined) {
                if (System.currentTimeMillis() - t < 1000) return;
                session.send(new ServerboundSetCarriedItemPacket(2));
                session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, (int) Instant.now().toEpochMilli(), 0f, 0f));
                t = System.currentTimeMillis();
            }
            if (packet instanceof ClientboundPlayerInfoUpdatePacket) {
                if (packet.toString().contains("ADD_PLAYER, INITIALIZE_CHAT, UPDATE_GAME_MODE, UPDATE_LISTED, UPDATE_LATENCY, UPDATE_DISPLAY_NAME")) {
                    String displayName = packet.toString().split(", name=")[1].split(", ")[0];
                    log.info("{} joined the game", displayName);
                    return;
                }
                if (packet.toString().contains("UPDATE_DISPLAY_NAME")) {
                    String display_name = packet.toString().split("children=\\[TextComponentImpl\\{content=\"")[1].split("\"")[0];
                    if (display_name.equals("◈")) {
                        display_name = packet.toString().split("children=\\[]}, TextComponentImpl\\{content=\"")[1].split("\"")[0];
                    }
                    String id = packet.toString().split("entries=\\[PlayerListEntry\\(profileId=")[1].split(", ")[0];
                    players.put(id, display_name);
                }
            }
            if (packet instanceof ClientboundPlayerInfoRemovePacket) {
                String id = packet.toString().replaceFirst("ClientboundPlayerInfoRemovePacket\\(profileIds=\\[", "").replace("])", "");
                log.info("{} left the game", players.get(id));
                players.remove(id);
            }
            if (packet instanceof ClientboundSetTimePacket){
                TpsTracker.INSTANCE.onPacketReceive();
            }
        }
    };


    public static SessionAdapter auto_login = new SessionAdapter() {
                    @Override
                    public void packetReceived(Session session, Packet packet) {
                        if (packet instanceof ClientboundLoginPacket) {
                            log.info(packet.toString());
                            entity_id=((ClientboundLoginPacket) packet).getEntityId();
                            players.clear();
                            if (packet.toString().contains(", gameMode=ADVENTURE")) {
                                server = "login";
                            }
                            if (packet.toString().contains(", gameMode=SURVIVAL")) {
                                server = "xin";
                                logined = true;
                            }
                        }
                        if (packet instanceof ClientboundSetTitleTextPacket titlePacket) {
                            // 获取标题内容
                            Component title = titlePacket.getText();
                            log.info("Received Title: {}", title);

                            // 检查标题内容并发送命令
                            if (title.toString().contains("指令:§b§l/L <密码>")) {
                                if (logined) return;
                                String command = "/l " + arguments.PASSWORD; // 你的命令
                                sendCommand(session, command); // 发送命令
                                logined = true;
                            }

                            if (title.toString().contains("登陆成功")) {
                                log.info("login successes");
                                // 设置物品槽
                                session.send(new ServerboundSetCarriedItemPacket(2));
                                session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, (int) Instant.now().toEpochMilli(), 0f, 0f)); // 使用物品
                            }
                        }
                        if (packet instanceof ClientboundSystemChatPacket systemChatPacket) {
                            Component text = systemChatPacket.getContent();
                            if (systemChatPacket.isOverlay()) {
                                if (!text.toString().startsWith("TextComponentImpl{content=\"§0§lPosition in queue: §6§l"))
                                    return;
                                String position = text.toString().replace("TextComponentImpl{content=\"§0§lPosition in queue: §6§l", "")
                                        .replace("\", style=StyleImpl{obfuscated=not_set, bold=not_set, strikethrough=not_set, underlined=not_set, italic=not_set, color=null, clickEvent=null, hoverEvent=null, insertion=null, font=null}, children=[]}", "");
                                log.info("目前位置:{}", position);
                                return;
                            }
                            if (problem.has(to_strings(text).get(0))){
                                sendCommand(session, String.valueOf(problem.get(to_strings(text).get(0))));
                            }
                            message_process.on_message(to_strings(text), session);

                        }
                    }

                    @Override
                    public void disconnected(DisconnectedEvent event) {
                        log.info("Disconnected: {}", event.getReason(), event.getCause());
                        logined = false;
                    }
                };

    public static void main(String[] args) throws InterruptedException {
        JCommander jCommander = JCommander.newBuilder()
         .addObject(arguments)
         .build();
        jCommander.parse(args);
        messages = new ArrayList<>();

        // 使用 try-with-resources 确保资源自动关闭
        try (BufferedReader br = new BufferedReader(new FileReader(arguments.MSG_FILE))) {
            String line;
            // 循环读取每一行，直到文件结束
            while ((line = br.readLine()) != null) {
                // 将每一行添加到 ArrayList 中
                messages.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("Username: {}", arguments.USERNAME);
        log.info("Owner: {}", arguments.OWNER);
        log.info("Direction: {}", Direction.from(arguments.BTN_DIR));
        MinecraftProtocol protocol = new MinecraftProtocol(arguments.USERNAME);
        Session client = new TcpClientSession(HOST, PORT, protocol);
        client.addListener(auto_join);
        client.addListener(auto_login);
        client.addListener(auto_message);
        client.addListener(heart_beat);
        client.connect();
        try {
            Thread.sleep(2000); // 短暂休眠，避免占用 CPU 资源
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (true) {
            if (!client.isConnected()) {
                logined = false;
                client = new TcpClientSession(HOST, PORT, protocol);
                client.addListener(auto_join);
                client.addListener(auto_login);
                client.addListener(auto_message);
                client.addListener(heart_beat);
                client.connect();
                log.info("Connected to {}", HOST);
                try {
                    Thread.sleep(2000); // 短暂休眠，避免占用 CPU 资源
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 发送命令的方法
    public static void sendCommand(Session session, String command) {
        if (logined){
            command += String.format("(%03d) ", new Random().nextInt(1000));
        }
        log.info("Sending command: {}", command);
        // 创建聊天包（命令通过聊天包发送）
        ServerboundChatPacket chatPacket = new ServerboundChatPacket(
                command,
                Instant.now().toEpochMilli(),
                0L,
                null,
                0,
                new BitSet()
        );
        try {
            session.send(chatPacket); // 发送聊天包
            log.info("Command sent: {}", command);
        } catch (Exception e) {
            log.error("Failed to send command: {}", e.getMessage());
        }
    }
}
/***
 * 致终于来到这里的勇敢的人：
 * 你是被上帝选中的人，是英勇的、不敌辛苦的、不眠不休的来修改我们这最棘手的代码的编程骑士。
 * 你，我们的救世主，人中之龙，我要对你说：永远不要放弃，永远不要对自己失望，永远不要逃走，辜负了自己，
 * 永远不要哭啼，永远不要说再见，永远不要说谎来伤害自己。
 */