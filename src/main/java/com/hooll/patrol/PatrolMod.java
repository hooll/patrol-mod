package com.hooll.patrol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatrolMod implements ClientModInitializer {
    public static final String MOD_ID = "patrol-mod";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        PatrolManager.load();
        PatrolHud.register();
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !PatrolManager.handleChat(message));
        ClientTickEvents.END_CLIENT_TICK.register(PatrolManager::tick);
        LOG.info("patrol-mod loaded");
    }
}
