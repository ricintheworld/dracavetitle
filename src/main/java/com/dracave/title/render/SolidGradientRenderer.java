package com.dracave.title.render;
import java.util.List;
import java.util.Locale;
public final class SolidGradientRenderer {
    private SolidGradientRenderer() {
    }
    public static String render(String display, List<String> colors, double cycle,
                                com.dracave.title.model.TitleAnimation.GradientMode mode) {
        if (colors.size() < 2) {
            return display;
        }
        double phase = GradientColor.phaseForCycle(cycle, mode);
        String color = GradientColor.lerpClosed(colors, phase);
        return "<" + color + ">" + display + "</" + color + ">";
    }
}
