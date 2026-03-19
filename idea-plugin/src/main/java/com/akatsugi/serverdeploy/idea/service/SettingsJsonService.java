package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SettingsJsonService {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public String toJson(ServerDeploySettingsState state) {
        return gson.toJson(state);
    }

    public ServerDeploySettingsState fromJson(String json) {
        ServerDeploySettingsState state = gson.fromJson(json, ServerDeploySettingsState.class);
        return state == null ? new ServerDeploySettingsState() : state;
    }
}