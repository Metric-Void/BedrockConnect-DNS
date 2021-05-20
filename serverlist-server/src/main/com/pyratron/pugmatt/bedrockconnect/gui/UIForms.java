package main.com.pyratron.pugmatt.bedrockconnect.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nukkitx.protocol.bedrock.Bedrock;
import com.nukkitx.protocol.bedrock.packet.ModalFormRequestPacket;

import main.com.pyratron.pugmatt.bedrockconnect.BedrockConnect;
import main.com.pyratron.pugmatt.bedrockconnect.CustomServer;
import main.com.pyratron.pugmatt.bedrockconnect.CustomServerHandler;

import java.util.List;

public class UIForms {
    public static final int ERROR = 2, MAIN = 0, DIRECT_CONNECT = 1, REMOVE_SERVER = 3, DONATION = 4;

    public static int currentForm = 0;

    public static JsonArray mainMenuButtons = new JsonArray();
    public static JsonArray featuredServerButtons = new JsonArray();

    static {
        mainMenuButtons.add(UIComponents.createButton("连接到一个服务器"));
        mainMenuButtons.add(UIComponents.createButton("移除一个服务器"));
        mainMenuButtons.add(UIComponents.createButton("退出服务器列表"));

        featuredServerButtons.add(UIComponents.createButton("The Hive", "https://forum.playhive.com/uploads/default/original/1X/0d05e3240037f7592a0f16b11b57c08eba76f19c.png", "url"));
        featuredServerButtons.add(UIComponents.createButton("Mineplex", "https://www.mineplex.com/assets/www-mp/img/footer/footer_smalllogo.png", "url"));
        featuredServerButtons.add(UIComponents.createButton("CubeCraft Games", "https://i.imgur.com/aFH1NUr.png", "url"));
        featuredServerButtons.add(UIComponents.createButton("Lifeboat Network", "https://lbsg.net/wp-content/uploads/2017/06/lifeboat-square.png", "url"));
        featuredServerButtons.add(UIComponents.createButton("Mineville", "https://i.imgur.com/0K4TDut.png", "url"));
        featuredServerButtons.add(UIComponents.createButton("Galaxite", "https://i.imgur.com/VxXO8Of.png", "url"));
    }

    public static ModalFormRequestPacket createMain(List<String> servers) {
        currentForm = MAIN;
        ModalFormRequestPacket mf = new ModalFormRequestPacket();
        mf.setFormId(UIForms.MAIN);

        JsonObject out = UIComponents.createForm("form", "服务器列表");
        out.addProperty("content", "");

        JsonArray buttons = new JsonArray();
        CustomServer[] customServers = CustomServerHandler.getServers();

        if(BedrockConnect.userServers)
            buttons.addAll(mainMenuButtons);
        else
            buttons.add(UIComponents.createButton("退出服务器列表"));

        for(int i=0;i<servers.size();i++) {
            buttons.add(UIComponents.createButton(servers.get(i), "https://i.loli.net/2021/05/20/jumBzIxAXQeFywk.png", "url"));
        }

        for (CustomServer cs : customServers) {
            buttons.add(UIComponents.createButton(cs.getName(), cs.getIconUrl(), "url"));
        }

        if(BedrockConnect.featuredServers) {
            buttons.addAll(featuredServerButtons);
        }

        out.add("buttons", buttons);

        mf.setFormData(out.toString());

        return mf;
    }

    public static int getServerIndex(int btnId, CustomServer[] customServers, List<String> playerServers) {
        int serverIndex;

        if(BedrockConnect.userServers) {
            // Set server index if button selected is not a menu option
            serverIndex = btnId - mainMenuButtons.size();
        } else {
            serverIndex = btnId - 1;
        }

        return serverIndex;
    }
    public static MainFormButton getMainFormButton(int btnId, CustomServer[] customServers, List<String> playerServers) {
        int serverIndex = getServerIndex(btnId, customServers, playerServers);

        if(BedrockConnect.userServers) {
            switch (btnId) {
                case 0:
                    return MainFormButton.CONNECT;
                case 1:
                    return MainFormButton.REMOVE;
                case 2:
                    return MainFormButton.EXIT;
            }
        }

        if(btnId == 0) {
            return MainFormButton.EXIT;
        } else if(serverIndex + 1 > playerServers.size() + customServers.length) {
            return MainFormButton.FEATURED_SERVER;
        } else if (serverIndex + 1 > playerServers.size() && serverIndex - playerServers.size() < customServers.length) {
            return MainFormButton.CUSTOM_SERVER;
        } else {
            return MainFormButton.USER_SERVER;
        }
    }

    public static ModalFormRequestPacket createDirectConnect() {
        currentForm = DIRECT_CONNECT;
        ModalFormRequestPacket mf = new ModalFormRequestPacket();
        mf.setFormId(UIForms.DIRECT_CONNECT);
        JsonObject out = UIComponents.createForm("custom_form", "连接服务器");

        JsonArray inputs = new JsonArray();

        inputs.add(UIComponents.createInput("服务器地址", "请输入服务器地址或IP:"));
        inputs.add(UIComponents.createInput("服务器端口", "请输入端口:", "19132"));
        inputs.add(UIComponents.createToggle("添加到服务器列表"));

        out.add("content", inputs);
        mf.setFormData(out.toString());

        return mf;
    }

    public static ModalFormRequestPacket createRemoveServer(List<String> servers) {
        currentForm = REMOVE_SERVER;
        ModalFormRequestPacket mf = new ModalFormRequestPacket();
        mf.setFormId(UIForms.REMOVE_SERVER);
        JsonObject out = UIComponents.createForm("custom_form", "移除服务器");

        JsonArray inputs = new JsonArray();

        inputs.add(UIComponents.createDropdown(servers,"Servers", "0"));

        out.add("content", inputs);
        mf.setFormData(out.toString());

        return mf;
    }

    public static ModalFormRequestPacket createError(String text) {
        currentForm = ERROR;
        ModalFormRequestPacket mf = new ModalFormRequestPacket();
        mf.setFormId(UIForms.ERROR);
        JsonObject form = new JsonObject();
        form.addProperty("type", "custom_form");
        form.addProperty("title", "Error");
        JsonArray content = new JsonArray();
        content.add(UIComponents.createLabel(text));
        form.add("content", content);
        mf.setFormData(form.toString());
        return mf;
    }

    public static ModalFormRequestPacket createDonatelink() {
        currentForm = DONATION;
        ModalFormRequestPacket mf = new ModalFormRequestPacket();
        mf.setFormId(UIForms.DONATION);
        JsonObject form = new JsonObject();
        form.addProperty("type", "custom_form");
        form.addProperty("title", "Thank you!");
        JsonArray content = new JsonArray();
        content.add(UIComponents.createLabel(""));
        form.add("content", content);
        mf.setFormData(form.toString());
        return mf;
    }
}
