package model.randomForest;

import org.apache.spark.ml.classification.ProbabilisticClassificationModel;

import java.io.Serializable;

public interface Classifier<T extends ProbabilisticClassificationModel> extends Serializable{

	//Properties-----------------------------------------------------

	public T getModel();
	public void setModel(T model);
	public String getName();
	public void setName(String name);

}
