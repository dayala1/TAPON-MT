package javaFX.controllers;

import experimentation.ResultsDataset;
import javaFX.Colors.Color;
import javaFX.Colors.Colors;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.util.ResourceBundle;

public class MatrixVizController implements Initializable {
    private ResultsDataset results;

    @FXML
    private AnchorPane aPane;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    public void setResults(ResultsDataset results){
        this.results = results;

        int matSpacing = 1;
        int matSize = 6;
        int matTotalSpace = matSpacing + matSize;

        Rectangle rectangle;
        Color color;
        String class1;
        String class2;
        Integer initialY = matTotalSpace * results.getSlotClasses().size();
        Tooltip tooltip;
        for (int i = 0; i < results.getSlotClasses().size() ; i++) {
            class1 = results.getSlotClasses().get(i);
            for (int j = 0; j < results.getSlotClasses().size() ; j++) {
                class2 = results.getSlotClasses().get(j);
                rectangle = new Rectangle(i*matTotalSpace, initialY - j*matTotalSpace, matSize, matSize);
                color = Colors.interpolateViridis(results.getSimilarityMatrix().get(class1, class2));
                rectangle.fillProperty().setValue(Paint.valueOf(color.toHexString()));

                tooltip = new Tooltip(String.format("%s === %s -> %s",  class1, class2, results.getSimilarityMatrix().get(class1, class2)));
                Tooltip.install(rectangle, tooltip);
                aPane.getChildren().add(rectangle);
            }
        }
        System.out.println(String.format("Loaded similarity matrix with %s classes", results.getSlotClasses().size()));
    }
}
