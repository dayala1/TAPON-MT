package main;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dataset.Dataset;
import dataset.Record;
import dataset.Slot;
import model.randomForest.ModelHandlerRandomForest;
import utils.ClockMonitor;
import utils.DatasetReader;
import utils.FileUtilsCust;

public class ModelApplicationDriver {
	
	public static double incorrect = 0.0;
	public static double correct = 0.0;

	public static void main(String[] args) throws Exception {
		ClockMonitor clock;
		
		//MODEL CREATION
		Dataset dataset;
		
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
		ModelHandlerRandomForest modelHandler;
		String resultsPath;
		
		clock = new ClockMonitor();
		
		
		//MODEL APPLICATION
		datasetReader = new DatasetReader();
		testingDatasets = new ArrayList<Dataset>();
		modelHandler = new ModelHandlerRandomForest();
		
		domains = new String[]{"Articles", "Awards", "Countries", "Courseware", "Dev8d", "DigitalEconomy", "Edubase", "Epo", "Epsrc", "Restaurants"};
		classifiersTablesRoot = "E:/model/resultsCompareOneVsAll/8-folds/1";
		resultsRoot = "E:/model/resultsCompareOneVsAll/8-folds/1";
		datasetsRoot = "E:/Documents/US/Tesis/datasets";
		
		modelHandler.setClassifiersRootFolder(String.format("%s/classifiersAndTables/modelClassifiers", classifiersTablesRoot));
		modelHandler.setTablesRootFolder(String.format("%s/classifiersAndTables/modelTables", classifiersTablesRoot));
		modelHandler.loadFeaturesCalculators();
		
		for (String domain : domains) {
			for (int i = 4; i < 5; i++) {
				datasetsPath = String.format("%s/%s/%s",datasetsRoot, domain, i);
				datasetReader.addDataset(datasetsPath, 1.0, testingDatasets);
			}
		}
		
		resultsPath = String.format("%s/results", resultsRoot);
		modelHandler.createNewContext();
		
		clock.start();
		modelHandler.loadClassifiers(false);
		modelHandler.loadClassifiers(true);
		System.out.println("Starting testing");
		for (Dataset testingDataset : testingDatasets) {
			modelHandler.refineHintsUnlabelledDataset(testingDataset);
			checkHints(testingDataset);
			System.out.println("");
			modelHandler.saveResults(testingDataset, String.format("%s/results/1-iterations", resultsPath));
		}
		clock.stop();
		FileUtilsCust.createCSV(String.format("%s/results/1-iterations/applicationTime.csv", resultsPath));
		FileUtilsCust.addLine(String.format("%s/results/1-iterations/applicationTime.csv", resultsPath), clock.getCPUTime());
		modelHandler.resetFolderCount();
		for (int i = 0; i < 1; i++) {
			for (Dataset testingDataset : testingDatasets) {
				modelHandler.refineHintsOnce(testingDataset);
				checkHints(testingDataset);
				System.out.println("");
				//System.out.println("finished dataset");
				modelHandler.saveResults(testingDataset, String.format("%s/results/%s-iterations", resultsPath, i+2));
			}
			clock.stop();
			FileUtilsCust.createCSV(String.format("%s/results/%s-iterations/applicationTime.csv", resultsPath, i+2));
			FileUtilsCust.addLine(String.format("%s/results/%s-iterations/applicationTime.csv", resultsPath, i+2), clock.getCPUTime());
			modelHandler.resetFolderCount();
		}
		/*
		//CONVERGENCE
		for (Dataset testingDataset : testingDatasets) {
			modelHandler.classifySlotsUntilConvergence(testingDataset, 7);
			checkHints(testingDataset);
			//System.out.println("finished dataset");
			modelHandler.saveResults(testingDataset, String.format("%s/results/%s-domains/fold-%s/", resultsPath, numberOfDomains, testingFoldNumber));
		}
		*/
		
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
