package com.dracave.title.gui;
import com.dracave.title.util.SoundUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
public final class GuiSound {
    private final FileConfiguration config;
    public GuiSound(FileConfiguration config) {
        this.config = config;
    }
    private String sound(String key, String fallback) {
        String value = config.getString("gui.sounds." + key, fallback);
        return value != null && !value.isBlank() ? value : fallback;
    }
    public void open(Player player) {
        SoundUtils.play(player, sound("open", "BLOCK_NOTE_BLOCK_PLING"), 0.6f, 1.2f);
    }
    public void click(Player player) {
        SoundUtils.play(player, sound("click", "UI_BUTTON_CLICK"), 0.5f, 1.0f);
    }
    public void switchPage(Player player) {
        SoundUtils.play(player, sound("switch-page", "BLOCK_NOTE_BLOCK_BASS"), 0.5f, 1.0f);
    }
    public void success(Player player) {
        SoundUtils.play(player, sound("success", "ENTITY_PLAYER_LEVELUP"), 0.5f, 1.2f);
    }
    public void error(Player player) {
        SoundUtils.play(player, sound("error", "BLOCK_NOTE_BLOCK_BASS"), 0.5f, 0.7f);
    }
    public void delete(Player player) {
        SoundUtils.play(player, sound("delete", "BLOCK_NOTE_BLOCK_BASEDRUM"), 0.6f, 1.0f);
    }
}
