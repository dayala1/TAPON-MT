package model.linearSVC;

import model.ModelHandler;
import model.SparkHandler;
import model.logisticRegression.SparkHandlerLogisticRegression;

import java.util.Map;

public class ModelHandlerLinearSVC extends ModelHandler {
	// Constructors---------------------------------------------------

    @Override
    public SparkHandler initializeSparkHandler() {
        return new SparkHandlerLinearSVC();
    }

	// Interface methods----------------------------------------------

	protected void setParams(Map<String, String> params) {
		assert sparkHandler != null;
		assert params != null;
	}

}
