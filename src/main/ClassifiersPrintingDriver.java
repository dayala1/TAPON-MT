package main;
import java.io.IOException;
import java.util.Map;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.ml.classification.RandomForestClassificationModel;
import org.apache.spark.mllib.classification.LogisticRegressionModel;
import org.json.simple.parser.ParseException;

public class ClassifiersPrintingDriver {

	public static void main(String[] args) throws IOException, ParseException {
		JavaSparkContext context;
		SparkConf sparkConf;
		String classifierPath;
		
		sparkConf = new SparkConf();
		sparkConf.setAppName("SparkHandler");
		sparkConf.setMaster(sparkConf.get("master", "local[*]"));
		context = new JavaSparkContext(sparkConf);
		RandomForestClassificationModel model = RandomForestClassificationModel.load("C:/Users/Boss/Documents/RandomForestNew/classifiersAndTables/modelClassifiers/10-domains/fold-1/classifiersNoHint/attributes/awardAgencyCode");
		System.out.println(model.toDebugString());
		context.close();
		
	}

}
