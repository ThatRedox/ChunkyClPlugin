package chunkycl.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.ui.render.RenderControlsTab;

public class ChunkyClTab implements RenderControlsTab {
    protected final VBox box;

    public ChunkyClTab() {
        box = new VBox(10.0);
        box.setPadding(new Insets(10.0));

        Button deviceSelectorButton = new Button("Select OpenCL Device");
        deviceSelectorButton.setOnMouseClicked(event -> {
            GpuSelector selector = new GpuSelector();
            selector.show();
        });
        box.getChildren().add(deviceSelectorButton);
    }

    @Override
    public void update(Scene scene) {

    }

    @Override
    public String getTabTitle() {
        return "OpenCL";
    }

    @Override
    public Node getTabContent() {
        return box;
    }
}
