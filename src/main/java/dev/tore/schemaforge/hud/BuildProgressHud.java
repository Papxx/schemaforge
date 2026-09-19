/*
 * This file is part of SchemaForge (https://github.com/Papxx/schemaforge).
 * Copyright (C) 2026 Papxx
 *
 * SchemaForge is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 3 of the License.
 *
 * SchemaForge is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SchemaForge.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.tore.schemaforge.hud;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.core.BuildSession;
import dev.tore.schemaforge.core.ProgressReport;
import dev.tore.schemaforge.modules.SchemaPrinter;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import java.util.List;
import java.util.Optional;

/**
 * Build progress, blocks/min, ETA, state and top missing materials (P3-05).
 * The text itself comes from {@link ProgressReport}; this class only draws it and offers the usual HUD settings.
 * Shell from P0-04; registered in {@link SchemaForgeAddon}.
 */
public final class BuildProgressHud extends HudElement {
    public static final HudElementInfo<BuildProgressHud> INFO = new HudElementInfo<>(SchemaForgeAddon.HUD_GROUP, "build-progress", "Shows SchemaForge build progress.", BuildProgressHud::new);

    /** What the HUD editor shows, so the element can be placed without a running build. */
    private static final List<String> SAMPLE = List.of(
        "BUILDING blockclasses 62%",
        "210 placed, 129 left - 48/min - ETA 3m",
        "missing: 64x stone, 12x oak_planks, 4x torch");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScale = settings.createGroup("Scale");
    private final SettingGroup sgBackground = settings.createGroup("Background");

    private final Setting<Boolean> hideWhenIdle = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-idle")
        .description("Show nothing while no build is running.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Text shadow.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Colour of the progress lines.")
        .defaultValue(new SettingColor())
        .build());

    private final Setting<SettingColor> missingColor = sgGeneral.add(new ColorSetting.Builder()
        .name("missing-color")
        .description("Colour of the line listing missing materials.")
        .defaultValue(new SettingColor(225, 175, 45))
        .build());

    private final Setting<Boolean> customScale = sgScale.add(new BoolSetting.Builder()
        .name("custom-scale")
        .description("Applies a custom scale to this hud element.")
        .defaultValue(false)
        .build());

    private final Setting<Double> scale = sgScale.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Custom scale.")
        .visible(customScale::get)
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build());

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays background.")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .visible(background::get)
        .defaultValue(new SettingColor(25, 25, 25, 50))
        .build());

    public BuildProgressHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<String> lines = isInEditor() ? SAMPLE : lines();
        if (lines.isEmpty()) {
            setSize(0, 0);
            return;
        }

        double lineHeight = renderer.textHeight(shadow.get(), getScale());
        double width = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double end = renderer.text(line, x, y + i * lineHeight, colorOf(line), shadow.get(), getScale());
            width = Math.max(width, end - x);
        }
        setSize(width, lineHeight * lines.size());

        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
    }

    /** Empty while nothing ran and the element is set to hide. */
    private List<String> lines() {
        SchemaPrinter printer = Modules.get().get(SchemaPrinter.class);
        Optional<BuildSession.Status> status = printer.lastStatus();
        if (status.isEmpty() && hideWhenIdle.get()) return List.of();
        return ProgressReport.lines(status, printer.shortages(), printer.isActive());
    }

    private Color colorOf(String line) {
        return line.startsWith("missing:") ? missingColor.get() : textColor.get();
    }

    private double getScale() {
        return customScale.get() ? scale.get() : Hud.get().getTextScale();
    }
}
