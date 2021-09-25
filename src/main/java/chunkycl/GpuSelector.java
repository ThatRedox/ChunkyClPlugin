package chunkycl;

import chunkycl.renderer.RendererInstance;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jocl.CL;
import org.jocl.cl_device_id;
import se.llbit.chunky.PersistentSettings;
import java.util.Arrays;

public class GpuSelector extends Stage {

    @SuppressWarnings("unchecked")
    public GpuSelector() {
        // Build scene
        RendererInstance instance = RendererInstance.get();
        ClDevice[] devices = new ClDevice[instance.devices.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = new ClDevice(instance.devices[i], i);
        }

        TableView<ClDevice> table = new TableView<>();
        table.setPrefWidth(433);
        table.setPrefHeight(200);
        table.setItems(FXCollections.observableList(Arrays.asList(devices)));

        TableColumn<ClDevice, String> nameCol = new TableColumn<>("Device Name");
        nameCol.setCellValueFactory(dev -> new SimpleStringProperty(dev.getValue().name));

        TableColumn<ClDevice, Double> computeCol = new TableColumn<>("Compute Capacity");
        computeCol.setCellValueFactory(dev -> new SimpleDoubleProperty(dev.getValue().computeCapacity).asObject());

        table.getColumns().setAll(nameCol, computeCol);

        VBox box = new VBox();
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setSpacing(10);
        box.setPadding(new Insets(10));

        box.getChildren().add(new Label("Select OpenCL device to use:"));
        box.getChildren().add(table);

        HBox buttons = new HBox();
        buttons.setAlignment(Pos.TOP_RIGHT);
        buttons.setSpacing(10);

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnMouseClicked(event -> this.close());
        buttons.getChildren().add(cancelButton);

        Button selectButton = new Button("Select Device");
        selectButton.setDefaultButton(true);
        selectButton.setTooltip(new Tooltip("Restart Chunky for changes to take effect."));
        selectButton.setOnMouseClicked(event -> {
            if (!table.getSelectionModel().isEmpty()) {
                PersistentSettings.settings.setInt("clDevice", table.getSelectionModel().getSelectedItem().index);
                PersistentSettings.save();
                this.close();
            }
        });
        buttons.getChildren().add(selectButton);

        box.getChildren().add(buttons);

        Scene scene = new Scene(box);

        // Apply scene
        this.setTitle("Select OpenCL Device");
        this.setScene(scene);
    }

    private static class ClDevice {
        protected final String name;
        protected final double computeCapacity;
        protected final int index;

        public ClDevice(cl_device_id device, int index) {
            long computeSpeed = (long) RendererInstance.getInts(device, CL.CL_DEVICE_MAX_CLOCK_FREQUENCY, 1)[0] *
                                (long) RendererInstance.getInts(device, CL.CL_DEVICE_MAX_COMPUTE_UNITS, 1)[0] * 32;

            name = RendererInstance.getString(device, CL.CL_DEVICE_NAME);
            computeCapacity = computeSpeed/1000.0;  // Approximate GFlops
            this.index = index;
        }
    }
}
