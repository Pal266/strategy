package com.pidluzsnij.strategy.ui.definition;

import com.pidluzsnij.strategy.ui.resource.UiResourcePath;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A complete, validated UI definition: the authored logical size and the components in definition order,
 * which is also their rendering order (later components are drawn over earlier ones). Instances exist only
 * after the whole definition has been validated.
 */
public record UiDefinition(UiResourcePath source, int logicalWidth, int logicalHeight, List<UiComponent> components) {

    public UiDefinition {
        if (logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("the logical size must be positive");
        }
        components = List.copyOf(components);
        Set<String> ids = new HashSet<>();
        for (UiComponent component : components) {
            if (!ids.add(component.id())) {
                throw new IllegalArgumentException("duplicate component identifier");
            }
        }
    }

    /** @return the image resources referenced by the components, in first-reference order */
    public Set<UiResourcePath> imageResources() {
        Set<UiResourcePath> images = new LinkedHashSet<>();
        for (UiComponent component : components) {
            if (component instanceof UiComponent.Image image) {
                images.add(image.image());
            } else if (component instanceof UiComponent.Button button) {
                for (VisualState state : VisualState.values()) {
                    images.add(button.image(state));
                }
            }
        }
        return images;
    }

    /** @return the text styles of the components, in definition order */
    public List<TextStyle> textStyles() {
        return components.stream()
                .<TextStyle>mapMulti((component, sink) -> {
                    if (component instanceof UiComponent.Text text) {
                        sink.accept(text.text());
                    } else if (component instanceof UiComponent.Button button) {
                        button.label().ifPresent(sink);
                    }
                })
                .toList();
    }

    /** @return the component with identifier {@code id} */
    public UiComponent component(String id) {
        return components.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no component with that identifier"));
    }
}
