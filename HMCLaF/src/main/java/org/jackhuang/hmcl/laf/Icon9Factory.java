/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hmcl.laf.utils;

import java.util.HashMap;
import org.jb2011.ninepatch4j.NinePatch;

/**
 *
 * @author huang
 */
public class Icon9Factory extends RawCache<NinePatch> {

    /**
     * 相对路径根（默认是相对于本类的相对物理路径）.
     */
    public final static String IMGS_ROOT = "imgs/np";

    @Override
    protected NinePatch getResource(String relativePath, Class baseClass) {
        return NinePatchHelper.createNinePatch(baseClass.getResource(relativePath), false);
    }

    public Icon9Factory(String namespace) {
        ns = namespace;
        init();
    }

    String ns;

    /**
     * Gets the raw.
     *
     * @param relativePath the relative path
     * @return the raw
     */
    public NinePatch getRaw(String relativePath) {
        return getRaw(relativePath, this.getClass());
    }

    HashMap<String, NinePatch> icons = new HashMap<>();

    protected void put(String namespace, String key, String filename) {
        if (!ns.equals(namespace))
            return;
        icons.put(namespace + ":" + key, getRaw(IMGS_ROOT + "/" + filename + ".9.png"));
    }

    public NinePatch get(String key) {
        return icons.get(ns + ":" + key);
    }
    
    public NinePatch get(String key, String state) {
        return Icon9Factory.this.get(concat(key, state));
    }

    public NinePatch getWithEnabled(String key, boolean enabled) {
        return get(key, enabled ? "" : "disabled");
    }
    
    public NinePatch getWithHorizontal(String key, boolean isHorizontal) {
        return get(key, isHorizontal ? "" : "vertical");
    }

    public NinePatch getWithButtonState(String key, boolean enabled, boolean pressed) {
        return get(key, (enabled ? (pressed ? "pressed" : "normal") : "disabled"));
    }
    
    public NinePatch getWithScrollState(String key, boolean pressed, boolean over) {
        return get(key, (pressed ? "pressed" : (over ? "rollover" : "")));
    }
    
    public NinePatch getWithComboState(String key, boolean enabled, boolean pressed, boolean over) {
        return get(key, (enabled ? (pressed ? "pressed" : (over ? "rollover" : "normal")) : "disabled"));
    }

    private String concat(String a, String b) {
        if (a.isEmpty() && b.isEmpty())
            return "";
        else if (a.isEmpty())
            return b;
        else if (b.isEmpty())
            return a;
        else
            return a + "_" + b;
    }

    private void init() {
        put("slider_track", "", "slider_track2");
        put("slider_track", "vertical", "slider_track2_v");
        put("slider_track", "disabled", "slider_track2_dark");
        put("slider_track", "vertical_disabled", "slider_track2_v_dark");
        put("slider_track", "foreground", "slider_track2_forgroud");
        put("slider_track", "foreground_disabled", "slider_track2_forgroud_disable");
        put("slider_track", "vertical_foreground", "slider_track2_forgroud_v");
        put("slider_track", "vertical_foreground_disabled", "slider_track2_forgroud_v_disable");

        put("button_arrow", "normal", "button_arrow");
        put("button_arrow", "pressed", "button_arrow_pressed");
        put("button_arrow", "rollover", "button_arrow_rollover");
        put("button_arrow", "disabled", "button_arrow_disable");

        put("progress_bar", "bg", "progress_bar_bg");
        put("progress_bar", "bg_vertical", "progress_bar_bg_v");
        put("progress_bar", "green", "progress_bar_green");
        put("progress_bar", "blue_vertical", "progress_bar_grean_v");
        
        put("split_touch", "bg1", "split_touch_bg1");
        
        put("list", "selected_icon_bg", "list_cell_selected_bg2");
        
        put("spinner", "", "spinner1_bg");
        put("spinner", "button_up", "spinner1_btn_up_bg");
        put("spinner", "button_down", "spinner1_btn_down_bg");
        put("spinner", "button_up_pressed", "spinner1_btn_up_pressed_bg");
        put("spinner", "button_down_pressed", "spinner1_btn_down_pressed_bg");
        put("spinner", "disabled", "spinner1_disable_bg");
        
        put("button", "normal", "btn_special_default");
        put("button", "disabled", "btn_special_disabled");
        put("button", "pressed", "btn_general_pressed");
        put("button", "rollover", "btn_general_rover");
        
        put("toggle_button", "selected", "toggle_button_selected");
        put("toggle_button", "rollover", "toggle_button_rover");
        
        put("scroll_bar", "vertical", "scroll_bar_v");
        put("scroll_bar", "vertical_rollover", "scroll_bar_rover_v");
        put("scroll_bar", "vertical_pressed", "scroll_bar_pressed_v");
        put("scroll_bar", "horizontal", "scroll_bar_h");
        put("scroll_bar", "horizontal_rollover", "scroll_bar_rover_h");
        put("scroll_bar", "horizontal_pressed", "scroll_bar_rover_h");
        put("scroll_bar", "arrow_bottom", "arrow_toBottom");
        put("scroll_bar", "arrow_top", "arrow_toTop");
        put("scroll_bar", "arrow_left", "arrow_toLeft");
        put("scroll_bar", "arrow_right", "arrow_toRight");
        put("scroll_bar", "arrow_bottom_rollover", "arrow_toBottom_rover");
        put("scroll_bar", "arrow_top_rollover", "arrow_toTop_rover");
        put("scroll_bar", "arrow_left_rollover", "arrow_toLeft_rover");
        put("scroll_bar", "arrow_right_rollover", "arrow_toRight_rover");
        put("scroll_bar", "scroll_pane_border", "scroll_pane_bg1");
        
        put("popup", "popup", "shadow_bg_popup");
        put("popup", "tooltip", "shadow_bg_tooltip2");
        
        put("table", "scroll_border", "table_scrollborder1");
        put("table", "header_cell", "table_header_bg1");
        put("table", "header_cell_separator", "table_header_separator1");
        
        put("text", "normal", "bg_login_text_normal");
        put("text", "pressed", "bg_login_text_pressed");
        put("text", "disabled", "bg_login_text_disable");
        put("text", "white", "null_white_bg");
        
        put("toolbar", "north", "toolbar_bg1");
        put("toolbar", "south", "toolbar_bg1_SOUTH");
        put("toolbar", "west", "toolbar_bg1_WEST");
        put("toolbar", "east", "toolbar_bg1_EAST");
        
        put("menu", "selected", "menu_bg");
        
        put("widget", "panel", "query_item_bg_2");
        put("widget", "hint_light_blue", "hint_bg_lightblue");
        put("widget", "hint_light_gray", "hint_bg_lightblue_gray");
        put("widget", "tips", "tips_bg");
        put("widget", "orange_balloon", "orange_baloon1");
        put("widget", "border_shadow1", "shadow1");
        put("widget", "border_shadow2", "shadow2");
        put("widget", "border_plain_gray", "plain_gray1");
        put("widget", "border_shadow3", "frame_shadow_border4");
        
        put("combo", "normal", "combo_normal");
        put("combo", "disabled", "combo_disabled");
        put("combo", "rollover", "combo_over");
        put("combo", "pressed", "combo_pressed");
    }
}
