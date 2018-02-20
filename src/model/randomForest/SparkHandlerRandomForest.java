package model.randomForest;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.ml.classification.MultilayerPerceptronClassificationModel;
import org.apache.spark.ml.classification.MultilayerPerceptronClassifier;
import org.apache.spark.ml.classification.RandomForestClassificationModel;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.ml.linalg.VectorUDT;
import org.apache.spark.ml.linalg.Vectors;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.json.simple.parser.ParseException;

import dataset.Attribute;
import dataset.Record;
import dataset.Slot;
import featuresCalculation.ClassesConfiguration;
import featuresCalculation.FeaturesVector;
import jersey.repackaged.com.google.common.collect.Lists;
import utils.FileUtilsCust;
import utils.RandomForestParsing.RandomForestParser;
import utils.RandomForestParsing.Tree;

public class SparkHandlerRandomForest implements Serializable {
	private static final long serialVersionUID = 4424575592980018563L;

	// Constructors---------------------------------------------------
	public SparkHandlerRandomForest(){
		this.featureNamesAttributesHF = new HashMap<>();
		this.featureNamesAttributesHB = new HashMap<>();
		this.featureNamesRecordsHF = new HashMap<>();
		this.featureNamesRecordsHB = new HashMap<>();
	}

	// Properties-----------------------------------------------------
	private String tablesFolder;
	private String classifiersFolder;
	private String classesFilePath;
	private Integer numberOfFeatures;
	// no default. Using 40
	private Integer maxDepth;
	// default = 32
	private Integer maxBins;
	// More trees +quality and +time. Using 100
	private Integer numTrees;
	private ClassesConfiguration classesConfiguration;
	private Map<Integer, String> featureNamesAttributesHF;
	private Map<Integer, String> featureNamesAttributesHB;
	private Map<Integer, String> featureNamesRecordsHF;
	private Map<Integer, String> featureNamesRecordsHB;

	public Map<Integer, String> getFeatureNamesAttributesHF() {
		return featureNamesAttributesHF;
	}

	public Map<Integer, String> getFeatureNamesAttributesHB() {
		return featureNamesAttributesHB;
	}

	public Map<Integer, String> getFeatureNamesRecordsHF() {
		return featureNamesRecordsHF;
	}

	public Map<Integer, String> getFeatureNamesRecordsHB() {
		return featureNamesRecordsHB;
	}

	public Integer getMaxDepth() {
		return maxDepth;
	}

	public void setMaxDepth(Integer maxDepth) {
		assert maxDepth != null;
		assert maxDepth > 0;

		this.maxDepth = maxDepth;
	}

	public Integer getMaxBins() {

		return maxBins;
	}

	public void setMaxBins(Integer maxBins) {
		assert maxBins != null;
		assert maxBins > 0;

		this.maxBins = maxBins;
	}

	public Integer getNumTrees() {
		return numTrees;
	}

	public void setNumTrees(Integer numTrees) {
		assert numTrees != null;
		assert numTrees > 0;

		this.numTrees = numTrees;
	}

	public String getTablesFolder() {
		return tablesFolder;
	}

	public void setTablesFolder(String trainingTablesFolder) {
		assert trainingTablesFolder != null;

		this.tablesFolder = trainingTablesFolder;
	}

	public String getClassifiersFolder() {
		return classifiersFolder;
	}

	public void setClassifiersFolder(String classifiersFolder) {
		assert classifiersFolder != null;

		this.classifiersFolder = classifiersFolder;
	}

	public String getClassesFilePath() {
		return classesFilePath;
	}

	public void setClassesFilePath(String classesFilePath) {
		assert classesFilePath != null;

		this.classesFilePath = classesFilePath;
	}

	public Integer getNumberOfFeatures() {
		return numberOfFeatures;
	}

	public void setNumberOfFeatures(Integer numberOfFeatures) {
		this.numberOfFeatures = numberOfFeatures;
	}

	public ClassesConfiguration getClassesConfiguration() {
		return classesConfiguration;
	}

	public void setClassesConfiguration(ClassesConfiguration classesConfiguration) {
		assert classesConfiguration != null;

		this.classesConfiguration = classesConfiguration;
	}

	// Internal state-------------------------------------------------

	private final static Level loggerLevel = Level.ERROR;
	// all these measures have default values, but they can be changed.
	// "gini" or "entropy"
	private final static String impurity = "gini";
	// So far, no justification anywhere... using 0.01
	private final static Double minInfoGain = 0.01;
	// Helps with deep trees
	private final static Boolean useNodeIdCache = true;
	// Here start the parameters for the multiclass classifier
	private final Integer numIterations = 500;
	private final int[] intermediaryLayers = {};
	private volatile static JavaSparkContext context;
	private volatile static SparkContext sparkContext;
	private volatile static SparkSession sparkSession;
	private volatile static Integer numUsers = 0;
	private List<ClassifierRandomForest> attributeClassifiersHintFree;
	private List<ClassifierRandomForest> recordClassifiersHintFree;
	private List<ClassifierRandomForest> attributeClassifiersHintBased;
	private List<ClassifierRandomForest> recordClassifiersHintBased;
	private MultilayerPerceptronClassificationModel attributeMulticlassClassifierHintFree;
	private MultilayerPerceptronClassificationModel recordMulticlassClassifierHintFree;
	private MultilayerPerceptronClassificationModel attributeMulticlassClassifierHintBased;
	private MultilayerPerceptronClassificationModel recordMulticlassClassifierHintBased;

	static {
		Logger.getLogger("org").setLevel(loggerLevel);
		Logger.getLogger("akka").setLevel(loggerLevel);
	}

	// Interface methods----------------------------------------------

	public synchronized void closeContext() {
		numUsers--;
		if(numUsers == 0) {
			context.close();
		}
	}

	public void loadFeatureNames() throws IOException {
		File tableFile;
		String header;
		BufferedReader reader;
		List<String> featureList;

		tableFile = new File(String.format("%s/trainingTablesHint/attributes.csv", tablesFolder));
		reader = new BufferedReader(new FileReader(tableFile));
		header = reader.readLine();
		featureList = Arrays.asList(header.trim().split(","));
		featureList = featureList.subList(1, featureList.size()-1);
		for (int i = 0; i < featureList.size(); i++) {
			featureNamesAttributesHB.put(i, featureList.get(i));
		}

		tableFile = new File(String.format("%s/trainingTablesNoHint/attributes.csv", tablesFolder));
		reader = new BufferedReader(new FileReader(tableFile));
		header = reader.readLine();
		featureList = Arrays.asList(header.trim().split(","));
		featureList = featureList.subList(1, featureList.size()-1);
		for (int i = 0; i < featureList.size(); i++) {
			featureNamesAttributesHF.put(i, featureList.get(i));
		}

		tableFile = new File(String.format("%s/trainingTablesHint/records.csv", tablesFolder));
		reader = new BufferedReader(new FileReader(tableFile));
		header = reader.readLine();
		featureList = Arrays.asList(header.trim().split(","));
		featureList = featureList.subList(1, featureList.size()-1);
		for (int i = 0; i < featureList.size(); i++) {
			featureNamesRecordsHB.put(i, featureList.get(i));
		}

		tableFile = new File(String.format("%s/trainingTablesNoHint/records.csv", tablesFolder));
		reader = new BufferedReader(new FileReader(tableFile));
		header = reader.readLine();
		featureList = Arrays.asList(header.trim().split(","));
		featureList = featureList.subList(1, featureList.size()-1);
		for (int i = 0; i < featureList.size(); i++) {
			featureNamesRecordsHF.put(i, featureList.get(i));
		}
	}

	public void writeClassifiersString(String folderPath) throws IOException {
		File classifierFile;
		BufferedWriter writer;

		for (ClassifierRandomForest model:attributeClassifiersHintBased) {
			classifierFile = new File(String.format("%s/hintBased/attributes/%s/%s.txt", folderPath, model.getName(), model.getName()));
			classifierFile.getParentFile().mkdirs();
			classifierFile.createNewFile();
			writer = new BufferedWriter(new FileWriter(classifierFile));
			writer.write(model.getModel().toDebugString());
			writer.close();

			//Trees parsing
			List<javafx.util.Pair<String, List<String>>> treeStrings;
			treeStrings = RandomForestParser.separateTrees(classifierFile);
			List<Tree> trees = treeStrings.stream().map(t -> new Tree(t, featureNamesAttributesHB)).collect(Collectors.toList());
			RandomForestParser.makeTreeViz(trees, classifierFile.getParent());
		}

		new File(String.format("%s/hintBased/records/", folderPath)).mkdirs();
		for (ClassifierRandomForest model:recordClassifiersHintBased) {
			classifierFile = new File(String.format("%s/hintBased/records/%s/%s.txt", folderPath, model.getName(), model.getName()));
			classifierFile.getParentFile().mkdirs();
			classifierFile.createNewFile();
			writer = new BufferedWriter(new FileWriter(classifierFile));
			writer.write(model.getModel().toDebugString());
			writer.close();

			//Trees parsing
			List<javafx.util.Pair<String, List<String>>> treeStrings;
			treeStrings = RandomForestParser.separateTrees(classifierFile);
			List<Tree> trees = treeStrings.stream().map(t -> new Tree(t, featureNamesRecordsHB)).collect(Collectors.toList());
			RandomForestParser.makeTreeViz(trees, classifierFile.getParent());
		}

		new File(String.format("%s/hintFree/attributes/", folderPath)).mkdirs();
		for (ClassifierRandomForest model:attributeClassifiersHintFree) {
			classifierFile = new File(String.format("%s/hintFree/attributes/%s/%s.txt", folderPath, model.getName(), model.getName()));
			classifierFile.getParentFile().mkdirs();
			classifierFile.createNewFile();
			writer = new BufferedWriter(new FileWriter(classifierFile));
			writer.write(model.getModel().toDebugString());
			writer.close();

			//Trees parsing
			List<javafx.util.Pair<String, List<String>>> treeStrings;
			treeStrings = RandomForestParser.separateTrees(classifierFile);
			List<Tree> trees = treeStrings.stream().map(t -> new Tree(t, featureNamesAttributesHF)).collect(Collectors.toList());
			RandomForestParser.makeTreeViz(trees, classifierFile.getParent());
		}

		new File(String.format("%s/hintFree/records/", folderPath)).mkdirs();
		for (ClassifierRandomForest model:recordClassifiersHintFree) {
			classifierFile = new File(String.format("%s/hintFree/records/%s/%s.txt", folderPath, model.getName(), model.getName()));
			classifierFile.getParentFile().mkdirs();
			classifierFile.createNewFile();
			writer = new BufferedWriter(new FileWriter(classifierFile));
			writer.write(model.getModel().toDebugString());
			writer.close();

			//Trees parsing
			List<javafx.util.Pair<String, List<String>>> treeStrings;
			treeStrings = RandomForestParser.separateTrees(classifierFile);
			List<Tree> trees = treeStrings.stream().map(t -> new Tree(t, featureNamesRecordsHF)).collect(Collectors.toList());
			RandomForestParser.makeTreeViz(trees, classifierFile.getParent());
		}
	}

	public void loadClassifiers(boolean hints) {
		assert classifiersFolder != null;

		File classifiersFolderFile;
		File[] classifiersFiles;
		RandomForestClassificationModel cm;
		String classifierPath;
		ClassifierRandomForest model;
		int i;

		i = 0;
		if (!hints) {
			this.attributeClassifiersHintFree = Lists.newArrayList();
			this.recordClassifiersHintFree = Lists.newArrayList();

			classifiersFolderFile = new File(String.format("%s/classifiersNoHint/attributes", classifiersFolder));
			classifiersFiles = classifiersFolderFile.listFiles();
			for (File file : classifiersFiles) {
				cm = RandomForestClassificationModel.load(file.getAbsolutePath());
				model = new ClassifierRandomForest();
				model.setModel(cm);
				model.setName(file.getName());
				attributeClassifiersHintFree.add(model);
				System.out.println(String.format("Loaded classifier %s", i));
				i++;
			}

			classifiersFolderFile = new File(String.format("%s/classifiersNoHint/records", classifiersFolder));
			classifiersFiles = classifiersFolderFile.listFiles();
			for (File file : classifiersFiles) {
				cm = RandomForestClassificationModel.load(file.getAbsolutePath());
				model = new ClassifierRandomForest();
				model.setModel(cm);
				model.setName(file.getName());
				recordClassifiersHintFree.add(model);
				System.out.println(String.format("Loaded classifier %s", i));
				i++;
			}

			classifierPath = String.format("%s/classifiersNoHint/attributes_multiclass", classifiersFolder);
			attributeMulticlassClassifierHintFree = MultilayerPerceptronClassificationModel.load(classifierPath);
			classifierPath = String.format("%s/classifiersNoHint/records_multiclass", classifiersFolder);
			if (recordClassifiersHintFree.size() > 0) {
				recordMulticlassClassifierHintFree = MultilayerPerceptronClassificationModel.load(classifierPath);
			}
		} else {
			this.attributeClassifiersHintBased = Lists.newArrayList();
			this.recordClassifiersHintBased = Lists.newArrayList();

			classifiersFolderFile = new File(String.format("%s/classifiersHint/attributes", classifiersFolder));
			classifiersFiles = classifiersFolderFile.listFiles();
			for (File file : classifiersFiles) {
				cm = RandomForestClassificationModel.load(file.getAbsolutePath());
				model = new ClassifierRandomForest();
				model.setModel(cm);
				model.setName(file.getName());
				attributeClassifiersHintBased.add(model);
				System.out.println(String.format("Loaded classifier %s", i));
				i++;
			}

			classifiersFolderFile = new File(String.format("%s/classifiersHint/records", classifiersFolder));
			classifiersFiles = classifiersFolderFile.listFiles();
			for (File file : classifiersFiles) {
				cm = RandomForestClassificationModel.load(file.getAbsolutePath());
				model = new ClassifierRandomForest();
				model.setModel(cm);
				model.setName(file.getName());
				recordClassifiersHintBased.add(model);
				System.out.println(String.format("Loaded classifier %s", i));
				i++;
			}

			classifierPath = String.format("%s/classifiersHint/attributes_multiclass", classifiersFolder);
			attributeMulticlassClassifierHintBased = MultilayerPerceptronClassificationModel.load(classifierPath);
			classifierPath = String.format("%s/classifiersHint/records_multiclass", classifiersFolder);
			if (recordClassifiersHintBased.size() > 0) {
				recordMulticlassClassifierHintBased = MultilayerPerceptronClassificationModel.load(classifierPath);
			}
		}
	}

	public synchronized void createNewContext() {
		numUsers++;
		if(numUsers == 1) {
			SparkConf sparkConf;
			sparkConf = new SparkConf();
			sparkConf.setAppName("SparkHandler");
			sparkConf.setMaster(sparkConf.get("master", "local[*]"));
			sparkConf.set("spark.network.timeout", "99999999");
			context = new JavaSparkContext(sparkConf);
			sparkContext = context.sc();
			sparkSession = new SparkSession(sparkContext);
		}
		while(sparkSession==null){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public Pair<String, LinkedHashMap<String, Double>> classify(Slot slot, boolean useProbabilities, boolean hints)
			throws IOException, ParseException {
		assert slot != null;
		assert this.classifiersFolder != null;

		Pair<String, LinkedHashMap<String, Double>> result;
		String predictedClass;
		MultilayerPerceptronClassificationModel model;
		String slotClass;
		LinkedHashMap<String, Double> classificationsMap;
		LinkedHashMap<String, Double> ranking;
		Double[] classificationsTemp;
		double[] classifications;
		Vector vector;
		Integer prediction;

		if (slot instanceof Attribute) {
			if (hints) {
				model = attributeMulticlassClassifierHintBased;
			} else {
				model = attributeMulticlassClassifierHintFree;
			}
		} else {
			if (hints) {
				model = recordMulticlassClassifierHintBased;
			} else {
				model = recordMulticlassClassifierHintFree;
			}
		}

		classificationsMap = applyClassifiersMap(slot, useProbabilities, hints);
		classificationsTemp = classificationsMap.values().toArray(new Double[0]);

		classifications = new double[classificationsTemp.length];
		int i = 0;
		for (Double d : classificationsTemp) {
			classifications[i] = d;
			i++;
		}

		ranking = new LinkedHashMap<>();
		classificationsMap.entrySet().stream().sorted(Entry.<String, Double> comparingByValue().reversed())
				.forEachOrdered(x -> ranking.put(x.getKey(), x.getValue()));

		vector = Vectors.dense(classifications);
		prediction = (int)model.predict(vector);
		if (slot instanceof Attribute) {
			predictedClass = classesConfiguration.getAttributeClassesMapping().inverse().get(prediction);
		} else {
			predictedClass = classesConfiguration.getRecordClassesMapping().inverse().get(prediction);
		}
		

		result = Pair.of(predictedClass, ranking);

		return result;
	}

	public LinkedHashMap<String, Double> classifyWithProbabilities(Slot slot, int numberOfPredictions, boolean hints)
			throws IOException, ParseException {
		assert slot != null;
		assert this.classifiersFolder != null;

		LinkedHashMap<String, Double> result;
		Map<String, Double> probabilities;

		result = new LinkedHashMap<String, Double>();

		probabilities = applyClassifiersMap(slot, true, hints);
		probabilities.entrySet().stream().sorted(Entry.<String, Double> comparingByValue().reversed())
				.limit(numberOfPredictions).forEachOrdered(x -> result.put(x.getKey(), x.getValue()));

		return result;
	}

	public void createBinaryClassifiers(boolean hints) throws IOException, ParseException {
		assert this.tablesFolder != null;
		assert this.classifiersFolder != null;

		File outFile;
		File tableFile;
		String firstLine;
		RandomForestClassificationModel model;

		if (hints) {
			outFile = new File(String.format("%s/classifiersHint/attributes", classifiersFolder));
		} else {
			outFile = new File(String.format("%s/classifiersNoHint/attributes", classifiersFolder));
		}
		outFile.mkdirs();

		if (hints) {
			outFile = new File(String.format("%s/classifiersHint/records", classifiersFolder));
		} else {
			outFile = new File(String.format("%s/classifiersNoHint/records", classifiersFolder));
		}
		outFile.mkdirs();

		// Attributes
		if (hints) {
			tableFile = new File(String.format("%s/trainingTablesHint/attributes.csv", tablesFolder));
		} else {
			tableFile = new File(String.format("%s/trainingTablesNoHint/attributes.csv", tablesFolder));
		}
		for (String attributeClass : classesConfiguration.getAttributeClasses()) {
			model = trainBinaryClassifier(tableFile.getAbsolutePath(), attributeClass);
			if (hints) {
				model.save(String.format("%s/classifiersHint/attributes/%s", classifiersFolder, attributeClass));
			} else {
				model.save(String.format("%s/classifiersNoHint/attributes/%s", classifiersFolder, attributeClass));
			}
		}
		// Records
		if (hints) {
			tableFile = new File(String.format("%s/trainingTablesHint/records.csv", tablesFolder));
		} else {
			tableFile = new File(String.format("%s/trainingTablesNoHint/records.csv", tablesFolder));
		}
		for (String recordClass : classesConfiguration.getRecordClasses()) {
			model = trainBinaryClassifier(tableFile.getAbsolutePath(), recordClass);
			if (hints) {
				model.save(String.format("%s/classifiersHint/records/%s", classifiersFolder, recordClass));
			} else {
				model.save(String.format("%s/classifiersNoHint/records/%s", classifiersFolder, recordClass));
			}
		}

	}

	public double[] applyClassifiers(Slot slot, boolean useProbabilities, boolean hints) {
		assert slot != null;
		assert this.classifiersFolder != null;

		double[] result;
		List<ClassifierRandomForest> models;
		double classification;
		List<Double> classifications;
		Vector vector;

		vector = featuresVectorToVector(slot.getFeaturesVector());
		if (slot instanceof Record) {
			if (hints) {
				models = recordClassifiersHintBased;
			} else {
				models = recordClassifiersHintFree;
			}
		} else {
			if (hints) {
				models = attributeClassifiersHintBased;
			} else {
				models = attributeClassifiersHintFree;
			}
		}

		classifications = new ArrayList<Double>();
		for (ClassifierRandomForest model : models) {
			if (useProbabilities) {
				classification = model.getModel().predictProbability(vector).toArray()[1];
			} else {
				classification = model.getModel().predict(vector);
			}
			classifications.add(classification);
		}
		result = new double[classifications.size()];
		for (int i = 0; i < classifications.size(); i++) {
			result[i] = classifications.get(i);
		}

		return result;
	}

	public LinkedHashMap<String, Double> applyClassifiersMap(Slot slot, boolean useProbabilities, boolean hints) {
		assert slot != null;
		assert this.classifiersFolder != null;

		LinkedHashMap<String, Double> result;
		List<ClassifierRandomForest> models;
		double classification;
		Vector vector;

		result = new LinkedHashMap<String, Double>();
		vector = featuresVectorToVector(slot.getFeaturesVector());
		if (slot instanceof Record) {
			if (hints) {
				models = recordClassifiersHintBased;
			} else {
				models = recordClassifiersHintFree;
			}
		} else {
			if (hints) {
				models = attributeClassifiersHintBased;
			} else {
				models = attributeClassifiersHintFree;
			}
		}
		for (ClassifierRandomForest model : models) {
			if (useProbabilities) {
				classification = model.getModel().predictProbability(vector).toArray()[1];
			} else {
				classification = model.getModel().predict(vector);
			}
			result.put(model.getName(), classification);
		}

		return result;
	}

	public void createMulticlassClassifierCsv(boolean useProbabilities, boolean hints)
			throws IOException, ParseException {
		assert this.tablesFolder != null;
		assert this.classifiersFolder != null;

		String filePath;
		File csvFile;
		String newCsvPath;
		List<RandomForestClassificationModel> models;
		RandomForestClassificationModel model;
		String slotClass;
		List<String> featureList;
		List<String> classifierNames;
		double[] featureValues;
		double slotClassDouble;
		Vector vector;
		File classifiersFolderFile;
		File[] classifiersFiles;
		double classification;
		String line;
		String elementId;

		// Attributes
		if (hints) {
			filePath = String.format("%s/trainingTablesHint/attributes.csv", tablesFolder);
			newCsvPath = String.format("%s/trainingTablesHint/attributes_Multiclass.csv", tablesFolder);
		} else {
			filePath = String.format("%s/trainingTablesNoHint/attributes.csv", tablesFolder);
			newCsvPath = String.format("%s/trainingTablesNoHint/attributes_Multiclass.csv", tablesFolder);
		}

		FileUtilsCust.createCSV(newCsvPath);
		csvFile = new File(filePath);
		if (hints) {
			classifiersFolderFile = new File(String.format("%s/classifiersHint/attributes", classifiersFolder));
		} else {
			classifiersFolderFile = new File(String.format("%s/classifiersNoHint/attributes", classifiersFolder));
		}
		classifiersFiles = classifiersFolderFile.listFiles();
		models = Lists.newArrayList();
		classifierNames = new ArrayList<String>();
		classifierNames.add("id");
		for (File file : classifiersFiles) {
			classifierNames.add(file.getName());
			model = RandomForestClassificationModel.load(file.getAbsolutePath());
			models.add(model);
		}
		classifierNames.add("class");
		FileUtilsCust.addLine(newCsvPath, classifierNames);

		try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
			br.readLine();
			while ((line = br.readLine()) != null) {
				featureList = Arrays.asList(line.trim().split(","));
				elementId = featureList.get(0);
				featureList = featureList.subList(1, featureList.size());
				featureValues = new double[featureList.size() - 1];
				slotClass = featureList.get(featureList.size() - 1);
				for (int i = 0; i < featureList.size() - 1; i++) {
					featureValues[i] = Double.valueOf(featureList.get(i));
				}
				vector = Vectors.dense(featureValues);

				slotClassDouble = classesConfiguration.getAttributeClassesMapping().get(slotClass);

				// Classifiers application. Can't use external methods due to
				// how they are created.
				featureList = new ArrayList<String>();
				featureList.add(elementId);
				for (RandomForestClassificationModel MPCModel : models) {
					if (useProbabilities) {
						classification = MPCModel.predictProbability(vector).toArray()[1];
					} else {
						classification = MPCModel.predict(vector);
					}
					featureList.add(String.valueOf(classification));
				}
				featureList.add(String.valueOf(slotClassDouble));
				FileUtilsCust.addLine(newCsvPath, featureList);
				System.out.println(String.format("Added line: %s", featureList));
			}
		}

		// Records
		if (hints) {
			filePath = String.format("%s/trainingTablesHint/records.csv", tablesFolder);
			newCsvPath = String.format("%s/trainingTablesHint/records_Multiclass.csv", tablesFolder);
		} else {
			filePath = String.format("%s/trainingTablesNoHint/records.csv", tablesFolder);
			newCsvPath = String.format("%s/trainingTablesNoHint/records_Multiclass.csv", tablesFolder);
		}
		FileUtilsCust.createCSV(newCsvPath);
		csvFile = new File(filePath);
		if (hints) {
			classifiersFolderFile = new File(String.format("%s/classifiersHint/records", classifiersFolder));
		} else {
			classifiersFolderFile = new File(String.format("%s/classifiersNoHint/records", classifiersFolder));
		}
		models = Lists.newArrayList();
		classifiersFiles = classifiersFolderFile.listFiles();
		classifierNames = new ArrayList<String>();
		classifierNames.add("id");
		for (File file : classifiersFiles) {
			classifierNames.add(file.getName());
			model = RandomForestClassificationModel.load(file.getAbsolutePath());
			models.add(model);
		}
		classifierNames.add("class");
		FileUtilsCust.addLine(newCsvPath, classifierNames);
		try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
			br.readLine();
			while ((line = br.readLine()) != null) {

				featureList = Arrays.asList(line.trim().split(","));
				elementId = featureList.get(0);
				featureList = featureList.subList(1, featureList.size());
				featureValues = new double[featureList.size() - 1];
				slotClass = featureList.get(featureList.size() - 1);
				for (int i = 0; i < featureList.size() - 1; i++) {
					featureValues[i] = Double.valueOf(featureList.get(i));
				}
				vector = Vectors.dense(featureValues);

				slotClassDouble = classesConfiguration.getRecordClassesMapping().get(slotClass);

				// Classifiers application. Can't use external methods due to
				// how they are created.
				featureList = new ArrayList<String>();
				featureList.add(elementId);
				for (RandomForestClassificationModel MPCModel : models) {
					classification = MPCModel.predict(vector);
					featureList.add(String.valueOf(classification));
				}
				featureList.add(String.valueOf(slotClassDouble));
				FileUtilsCust.addLine(newCsvPath, featureList);
				System.out.println(String.format("Added line: %s", featureList));
			}
		}

	}

	public void trainMulticlassClassifier(boolean hints) throws IOException, ParseException {
		File tableFile;
		FileReader reader;
		BufferedReader bufferedReader;
		String firstLine;
		Integer numberOfFeatures;
		Integer numClasses;
		int[] layers;
		String trainingFilePath;
		JavaRDD<String> csv;
		Dataset<Row> data;
		MultilayerPerceptronClassifier trainer;
		MultilayerPerceptronClassificationModel model;

		trainer = new MultilayerPerceptronClassifier().setMaxIter(numIterations).setLabelCol("label")
				.setFeaturesCol("features");

		// Atributes
		numClasses = classesConfiguration.getAttributeClasses().size();
		if (hints) {
			trainingFilePath = String.format("%s/trainingTablesHint/attributes_Multiclass.csv", tablesFolder);
		} else {
			trainingFilePath = String.format("%s/trainingTablesNoHint/attributes_Multiclass.csv", tablesFolder);
		}
		csv = context.textFile(trainingFilePath);
		tableFile = new File(trainingFilePath);
		reader = new FileReader(tableFile);
		bufferedReader = new BufferedReader(reader);
		firstLine = bufferedReader.readLine();
		bufferedReader.close();
		numberOfFeatures = firstLine.split(",").length - 2;
		layers = new int[] { numberOfFeatures };
		layers = ArrayUtils.addAll(layers, intermediaryLayers);
		layers = ArrayUtils.add(layers, numClasses);
		data = csvToDataset(csv);
		trainer = trainer.setLayers(layers);
		model = trainer.fit(data);
		if (hints) {
			model.save(String.format("%s/classifiersHint/attributes_multiclass", classifiersFolder));
		} else {
			model.save(String.format("%s/classifiersNoHint/attributes_multiclass", classifiersFolder));
		}

		// Records
		numClasses = classesConfiguration.getRecordClasses().size();
		if (numClasses > 0) {
			if (hints) {
				trainingFilePath = String.format("%s/trainingTablesHint/records_Multiclass.csv", tablesFolder);
			} else {
				trainingFilePath = String.format("%s/trainingTablesNoHint/records_Multiclass.csv", tablesFolder);
			}
			csv = context.textFile(trainingFilePath);
			tableFile = new File(trainingFilePath);
			reader = new FileReader(tableFile);
			bufferedReader = new BufferedReader(reader);
			firstLine = bufferedReader.readLine();
			bufferedReader.close();
			numberOfFeatures = firstLine.split(",").length - 2;
			layers = new int[] { numberOfFeatures };
			layers = ArrayUtils.addAll(layers, intermediaryLayers);
			layers = ArrayUtils.add(layers, numClasses);
			data = csvToDataset(csv);
			trainer = trainer.setLayers(layers);
			model = trainer.fit(data);
			if (hints) {
				model.save(String.format("%s/classifiersHint/records_multiclass", classifiersFolder));
			} else {
				model.save(String.format("%s/classifiersNoHint/records_multiclass", classifiersFolder));
			}
		}

	}

	// Ancillary methods----------------------------------------------

	private RandomForestClassificationModel trainBinaryClassifier(String trainingFilePath, String slotClass) {
		assert context != null;
		assert trainingFilePath != null;
		assert this.numTrees != null;
		assert this.maxDepth != null;
		assert this.maxBins != null;
		assert slotClass != null;

		final RandomForestClassificationModel result;
		RandomForestClassifier trainer;
		JavaRDD<String> csv;
		Dataset<Row> trainingData;

		csv = context.textFile(trainingFilePath);
		trainingData = csvToDataset(csv, slotClass);
		trainer = new RandomForestClassifier().setNumTrees(numTrees).setSeed(1234).setLabelCol("label").setMaxBins(32).setMaxDepth(maxDepth)
				.setFeaturesCol("features");
		System.out.println(String.format("Training classifier for class %s", slotClass));
		result = trainer.fit(trainingData);
		System.out.println(result.toString());
		return result;
	}

	private Dataset<Row> csvToDataset(JavaRDD<String> data, final String slotClass) {
		assert data != null;

		Dataset<Row> result;
		JavaRDD<Row> rowsRDD;
		final String header;
		StructField structField;
		StructField[] structFields;
		StructType structType;

		structFields = new StructField[2];
		structField = DataTypes.createStructField("features", new VectorUDT(), false);
		structFields[0] = structField;
		structField = DataTypes.createStructField("label", DataTypes.DoubleType, false);
		structFields[1] = structField;
		structType = new StructType(structFields);

		header = data.first();
		data = data.filter(new Function<String, Boolean>() {
			@Override
			public Boolean call(String v1) throws Exception {
				assert v1 != null;

				Boolean result;

				result = !v1.equals(header);

				return result;
			}
		});
		rowsRDD = data.map(new Function<String, Row>() {

			private static final long serialVersionUID = -2554198184785931531L;

			@Override
			public Row call(String alert) {
				Row result;
				List<String> featureList;
				double[] featureValues;
				String featureClass;
				Double featureClassDouble;
				Vector vector;

				featureList = Arrays.asList(alert.trim().split(","));
				featureList = featureList.subList(1, featureList.size());
				featureValues = new double[featureList.size() - 1];
				featureClass = featureList.get(featureList.size() - 1);
				for (int i = 0; i < featureList.size() - 1; i++) {
					featureValues[i] = Double.valueOf(featureList.get(i));
				}
				vector = Vectors.dense(featureValues);
				featureClassDouble = featureClass.equals(slotClass) ? 1.0 : 0.0;
				result = RowFactory.create(vector, featureClassDouble);
				return result;
			}
		});

		result = sparkSession.createDataFrame(rowsRDD, structType);
		return result;
	}

	private Dataset<Row> csvToDataset(JavaRDD<String> data, final Map<String, Double> classesMapping) {
		assert data != null;

		Dataset<Row> result;
		JavaRDD<Row> rowsRDD;
		final String header;
		StructField structField;
		StructField[] structFields;
		StructType structType;

		structFields = new StructField[2];
		structField = DataTypes.createStructField("features", new VectorUDT(), false);
		structFields[0] = structField;
		structField = DataTypes.createStructField("label", DataTypes.DoubleType, false);
		structFields[1] = structField;
		structType = new StructType(structFields);

		header = data.first();
		data = data.filter(new Function<String, Boolean>() {
			@Override
			public Boolean call(String v1) throws Exception {
				assert v1 != null;

				Boolean result;

				result = !v1.equals(header);

				return result;
			}
		});
		rowsRDD = data.map(new Function<String, Row>() {

			private static final long serialVersionUID = -2554198184785931531L;

			@Override
			public Row call(String alert) {
				Row result;
				List<String> featureList;
				double[] featureValues;
				String featureClass;
				Double featureClassDouble;
				Vector vector;

				featureList = Arrays.asList(alert.trim().split(","));
				featureList = featureList.subList(1, featureList.size());
				featureValues = new double[featureList.size() - 1];
				featureClass = featureList.get(featureList.size() - 1);
				for (int i = 0; i < featureList.size() - 1; i++) {
					featureValues[i] = Double.valueOf(featureList.get(i));
				}
				vector = Vectors.dense(featureValues);
				featureClassDouble = classesMapping.get(featureClass);
				result = RowFactory.create(vector, featureClassDouble);
				return result;
			}
		});

		result = sparkSession.createDataFrame(rowsRDD, structType);
		return result;
	}

	private Dataset<Row> csvToDataset(JavaRDD<String> data) {
		assert data != null;

		Dataset<Row> result;
		JavaRDD<Row> rowsRDD;
		final String header;
		StructField structField;
		StructField[] structFields;
		StructType structType;

		structFields = new StructField[2];
		structField = DataTypes.createStructField("features", new VectorUDT(), false);
		structFields[0] = structField;
		structField = DataTypes.createStructField("label", DataTypes.DoubleType, false);
		structFields[1] = structField;
		structType = new StructType(structFields);

		header = data.first();
		data = data.filter(new Function<String, Boolean>() {
			@Override
			public Boolean call(String v1) throws Exception {
				assert v1 != null;

				Boolean result;

				result = !v1.equals(header);

				return result;
			}
		});
		rowsRDD = data.map(new Function<String, Row>() {

			private static final long serialVersionUID = -2554198184785931531L;

			@Override
			public Row call(String alert) {
				Row result;
				List<String> featureList;
				double[] featureValues;
				String featureClass;
				Double featureClassDouble;
				Vector vector;

				featureList = Arrays.asList(alert.trim().split(","));
				featureList = featureList.subList(1, featureList.size());
				featureValues = new double[featureList.size() - 1];
				featureClass = featureList.get(featureList.size() - 1);
				for (int i = 0; i < featureList.size() - 1; i++) {
					featureValues[i] = Double.valueOf(featureList.get(i));
				}
				vector = Vectors.dense(featureValues);
				featureClassDouble = Double.valueOf(featureClass);
				result = RowFactory.create(vector, featureClassDouble);
				return result;
			}
		});

		result = sparkSession.createDataFrame(rowsRDD, structType);
		return result;
	}

	private Vector featuresVectorToVector(FeaturesVector featuresVector) {
		assert featuresVector != null;

		Vector result;
		double[] doubleFeatureValues;
		List<String> featureValues;
		double value;
		String stringValue;

		featureValues = featuresVector.getRawValues();
		if (featuresVector.getVectorClass() != null) {
			featureValues = featureValues.subList(0, featureValues.size() - 1);
		}
		doubleFeatureValues = new double[featureValues.size()];
		for (int i = 0; i < featureValues.size(); i++) {
			stringValue = featureValues.get(i);
			value = Double.valueOf(stringValue);
			doubleFeatureValues[i] = value;
		}
		result = Vectors.dense(doubleFeatureValues);

		return result;
	}

}