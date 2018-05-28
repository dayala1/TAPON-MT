package main;
import java.util.*;

import dataset.Dataset;
import dataset.Record;
import dataset.Slot;
import featuresCalculation.DatasetFeaturesCalculator;
import featuresCalculation.FeaturesGroup;
import featuresCalculation.featureGroups.attribute.Kushmerick_CharacterDensityGroup;
import featuresCalculation.featureGroups.attribute.Kushmerick_MeanWordLengthGroup;
import featuresCalculation.featureGroups.attribute.Kushmerick_NumberOfOccurrencesGroup;
import featuresCalculation.featureGroups.attribute.Kushmerick_TokenDensityGroup;
import featuresCalculation.featureGroups.record.NumberOfChildrenGroup;
import model.randomForest.ModelHandlerRandomForestOneIteration;
import utils.ClockMonitor;
import utils.DatasetReader;
import utils.FileUtilsCust;

public class KushmerickTestingDriver {
	
	public static double incorrect = 0.0;
	public static double correct = 0.0;

	public static void main(String[] args) throws Exception {
		ClockMonitor clock;
		
		//MODEL CREATION
		Dataset dataset;
		Set<FeaturesGroup> slotFeaturesGroups;
		Set<FeaturesGroup> featurableFeaturesGroups;
		Set<FeaturesGroup> hintSlotFeaturesGroups;
		Set<FeaturesGroup> hintFeaturableFeaturesGroups;
		Kushmerick_CharacterDensityGroup characterDensityGroup;
		Kushmerick_TokenDensityGroup tokenDensityGroup;
		Kushmerick_NumberOfOccurrencesGroup numberOfOccurrencesGroup;
		Kushmerick_MeanWordLengthGroup meanWordLengthGroup;
		NumberOfChildrenGroup numberOfChildrenGroup;
		DatasetFeaturesCalculator datasetFeaturesCalculator;
		List<String> trainingDatasetsFolders;
		
		//MODEL APPLICATION
		List<Dataset> trainingDatasets;
		List<Dataset> testingDatasets;
		DatasetReader datasetReader;
		String classifiersTablesRoot;
		String resultsRoot;
		String datasetsRoot;
		String[] domains;
		int testingFoldNumber;
		Integer numberOfDomains;
		String datasetsPath;
		ModelHandlerRandomForestOneIteration modelHandler;
		String resultsPath;
		
		clock = new ClockMonitor();
		
		//MODEL CREATION
		slotFeaturesGroups = new HashSet<FeaturesGroup>();
		hintSlotFeaturesGroups = new HashSet<FeaturesGroup>();
		characterDensityGroup = new Kushmerick_CharacterDensityGroup();
		tokenDensityGroup = new Kushmerick_TokenDensityGroup();
		numberOfOccurrencesGroup = new Kushmerick_NumberOfOccurrencesGroup();
		meanWordLengthGroup = new Kushmerick_MeanWordLengthGroup();
		numberOfChildrenGroup = new NumberOfChildrenGroup();
		trainingDatasetsFolders = new ArrayList<String>();
		featurableFeaturesGroups = new HashSet<FeaturesGroup>();
		hintFeaturableFeaturesGroups = new HashSet<FeaturesGroup>();
		
		//MODEL APPLICATION
		datasetReader = new DatasetReader();
		trainingDatasets = new ArrayList<Dataset>();
		testingDatasets = new ArrayList<Dataset>();
		modelHandler = new ModelHandlerRandomForestOneIteration();
		domains = new String[]{"Awards-propios"};
		numberOfDomains = domains.length;
		classifiersTablesRoot = "E:/model/KushResultsFinal";
		resultsRoot = "E:/model/KushResultsFinal";
		datasetsRoot = "E:/Documents/US/Tesis";
		testingFoldNumber = 10;
		
		modelHandler.setClassifiersRootFolder(String.format("%s/classifiersAndTables/modelClassifiers/%s-domains/fold-%s", classifiersTablesRoot, numberOfDomains, testingFoldNumber));
		modelHandler.setTablesRootFolder(String.format("%s/classifiersAndTables/modelTables/%s-domains/fold-%s", classifiersTablesRoot, numberOfDomains, testingFoldNumber));
		
		slotFeaturesGroups.add(characterDensityGroup);
		slotFeaturesGroups.add(tokenDensityGroup);
		slotFeaturesGroups.add(numberOfOccurrencesGroup);
		slotFeaturesGroups.add(meanWordLengthGroup);
		slotFeaturesGroups.add(numberOfChildrenGroup);
		
		trainingDatasetsFolders = new ArrayList<String>();
		
		for (String domain : domains) {
			for (int i = 1; i < 11; i++) {
				datasetsPath = String.format("%s/datasets/%s/%s",datasetsRoot, domain, i);
				if (i == testingFoldNumber) {
					datasetReader.addDataset(datasetsPath, 1.0, testingDatasets);
				} else {
					datasetReader.addDataset(datasetsPath, 1.0, trainingDatasets);
					trainingDatasetsFolders.add(datasetsPath);
				}
			}
		}
		
		resultsPath = String.format("%s/results", resultsRoot);
		modelHandler.setFeaturableFeaturesGroups(featurableFeaturesGroups);
		modelHandler.setSlotFeaturesGroups(slotFeaturesGroups);
		modelHandler.setHintsFeaturableFeaturesGroups(hintFeaturableFeaturesGroups);
		modelHandler.setHintsSlotFeaturesGroups(hintSlotFeaturesGroups);
		clock.start();
		modelHandler.trainModel(trainingDatasets, new HashMap<>());
		clock.stop();
		FileUtilsCust.createCSV(String.format("%s/%s-domains/fold-%s/trainingTime.csv", resultsPath, numberOfDomains, testingFoldNumber));
		FileUtilsCust.addLine(String.format("%s/%s-domains/fold-%s/trainingTime.csv", resultsPath, numberOfDomains, testingFoldNumber), clock.getCPUTime());
		modelHandler.createNewContext();

		modelHandler.loadClassifiers(false);
		clock.start();
		System.out.println("Starting testing");
		for (Dataset testingDataset : testingDatasets) {
			modelHandler.refineHintsUnlabelledDataset(testingDataset);
			checkHints(testingDataset);
			modelHandler.saveResults(testingDataset, String.format("%s/%s-domains/fold-%s/1-iterations", resultsPath, numberOfDomains, testingFoldNumber));
		}
		clock.stop();
		FileUtilsCust.createCSV(String.format("%s/%s-domains/fold-%s/1-iterations/applicationTime.csv", resultsPath, numberOfDomains, testingFoldNumber));
		FileUtilsCust.addLine(String.format("%s/%s-domains/fold-%s/1-iterations/applicationTime.csv", resultsPath, numberOfDomains, testingFoldNumber), clock.getCPUTime());
		
		modelHandler.closeContext();
	}
	
	public static void checkHints(Dataset dataset) {
		assert dataset != null;
		List<Slot> children;
		
		children = dataset.getSlots();
		for (Slot child : children) {
			checkHints(child);
		}
	}
	
	public static void checkHints(Slot slot) {
		assert slot != null;
		List<Slot> children;
		
		//System.out.println(String.format("Slot of class %s classified as %s", slot.getSlotClass(), slot.getHint()));
		
		if(slot.getSlotClass().equals(slot.getHint())) {
			correct++;
		} else {
			incorrect++;
		}
		
		if (slot instanceof Record) {
			children = ((Record)slot).getSlots();
			for (Slot child : children) {
				checkHints(child);
			}
		}
	}
	
}
