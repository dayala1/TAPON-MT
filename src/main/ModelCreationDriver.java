package main;
import java.util.*;

import dataset.Dataset;
import dataset.Record;
import dataset.Slot;
import featuresCalculation.DatasetFeaturesCalculator;
import featuresCalculation.FeaturesGroup;
import featuresCalculation.featureGroups.attribute.CharacterDensityGroup;
import featuresCalculation.featureGroups.attribute.CommonPrefixSuffixLengthGroup;
import featuresCalculation.featureGroups.attribute.EditDistanceGroup;
import featuresCalculation.featureGroups.attribute.NumberOfOccurrencesGroup;
import featuresCalculation.featureGroups.attribute.NumericValueGroup;
import featuresCalculation.featureGroups.attribute.TokenDensityGroup;
import featuresCalculation.featureGroups.attribute.TyperScoreGroup;
import featuresCalculation.featureGroups.featurable.Hint_MinimumTreeDistanceGroup;
import featuresCalculation.featureGroups.record.Hint_DensityOfSlotGroup;
import featuresCalculation.featureGroups.record.NumberOfChildrenGroup;
import featuresCalculation.featureGroups.record.Hint_NumberOfSlotsRecordGroup;
import featuresCalculation.featureGroups.slot.Hint_DensityOfBrothersGroup;
import featuresCalculation.featureGroups.slot.NodeDepthGroup;
import featuresCalculation.featureGroups.slot.NumberOfBrothersGroup;
import featuresCalculation.featureGroups.slot.OliveiraDaSilvaGroup;
import model.randomForest.ModelHandlerRandomForest;
import utils.ClockMonitor;
import utils.DatasetReader;
import utils.FileUtilsCust;

public class ModelCreationDriver {

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
		CharacterDensityGroup characterDensityGroup;
		TokenDensityGroup tokenDensityGroup;
		NumberOfOccurrencesGroup numberOfOccurrencesGroup;
		OliveiraDaSilvaGroup oliveiraDaSilvaGroup;
		Hint_NumberOfSlotsRecordGroup numberOfSlotsRecordGroup;
		CommonPrefixSuffixLengthGroup commonPrefixSuffixLengthGroup;
		Hint_DensityOfSlotGroup densityOfSlotsGroup;
		NodeDepthGroup nodeDepthGroup;
		EditDistanceGroup editDistanceGroup;
		TyperScoreGroup typerScoreGroup;
		Hint_MinimumTreeDistanceGroup minimumTreeDistanceGroup;
		NumberOfBrothersGroup numberOfBrothersGroup;
		NumberOfChildrenGroup numberOfChildrenGroup;
		Hint_DensityOfBrothersGroup densityofBrothersGroup;
		NumericValueGroup numericValueGroup;
		DatasetFeaturesCalculator datasetFeaturesCalculator;

		//MODEL APPLICATION
		List<Dataset> trainingDatasets;
		List<Dataset> testingDatasets;
		DatasetReader datasetReader;
		String classifiersTablesRoot;
		String resultsRoot;
		String datasetsRoot;
		String datasetsPath;
		ModelHandlerRandomForest modelHandler;

		clock = new ClockMonitor();

		//MODEL CREATION
		//MODEL APPLICATION
		datasetReader = new DatasetReader();

		String[] domains = new String[]{"Articles", "Awards", "Countries", "Courseware", "Dev8d", "DigitalEconomy", "Edubase", "Epo", "Epsrc", "Restaurants"};
		Integer numberOfDomains = domains.length;
		classifiersTablesRoot = "E:/model/resultsCompareOneVsAll";
		Integer numberOfFolds = 8;
		datasetsRoot = "E:/Documents/US/Tesis";
		Set<Integer> trainingFolds;

		for (int i = 1; i < 2; i++) {
			trainingFolds = new HashSet<>();
			for (int j = i; j < i+numberOfFolds; j++) {
				trainingFolds.add(j%10+1);
			}
			System.out.println(trainingFolds);

			modelHandler = new ModelHandlerRandomForest();
			modelHandler.setClassifiersRootFolder(String.format("%s/%s-folds/%s/classifiersAndTables/modelClassifiers", classifiersTablesRoot, numberOfFolds, i));
			modelHandler.setTablesRootFolder(String.format("%s/%s-folds/%s/classifiersAndTables/modelTables", classifiersTablesRoot, numberOfFolds, i));

			slotFeaturesGroups = new HashSet<FeaturesGroup>();
			hintSlotFeaturesGroups = new HashSet<FeaturesGroup>();
			featurableFeaturesGroups = new HashSet<FeaturesGroup>();
			hintFeaturableFeaturesGroups = new HashSet<FeaturesGroup>();

			characterDensityGroup = new CharacterDensityGroup();
			tokenDensityGroup = new TokenDensityGroup();
			numberOfOccurrencesGroup = new NumberOfOccurrencesGroup();
			numberOfSlotsRecordGroup = new Hint_NumberOfSlotsRecordGroup();
			commonPrefixSuffixLengthGroup = new CommonPrefixSuffixLengthGroup();
			densityOfSlotsGroup = new Hint_DensityOfSlotGroup();
			nodeDepthGroup = new NodeDepthGroup();
			editDistanceGroup = new EditDistanceGroup();
			typerScoreGroup = new TyperScoreGroup();
			numberOfBrothersGroup = new NumberOfBrothersGroup();
			numberOfChildrenGroup = new NumberOfChildrenGroup();
			minimumTreeDistanceGroup = new Hint_MinimumTreeDistanceGroup();
			densityofBrothersGroup = new Hint_DensityOfBrothersGroup();
			numericValueGroup = new NumericValueGroup();

			slotFeaturesGroups.add(characterDensityGroup);
			slotFeaturesGroups.add(tokenDensityGroup);
			slotFeaturesGroups.add(numberOfOccurrencesGroup);
			slotFeaturesGroups.add(commonPrefixSuffixLengthGroup);
			slotFeaturesGroups.add(nodeDepthGroup);
			slotFeaturesGroups.add(editDistanceGroup);
			slotFeaturesGroups.add(typerScoreGroup);
			slotFeaturesGroups.add(numberOfBrothersGroup);
			slotFeaturesGroups.add(numberOfChildrenGroup);
			slotFeaturesGroups.add(numericValueGroup);
			hintSlotFeaturesGroups.add(numberOfSlotsRecordGroup);
			hintSlotFeaturesGroups.add(densityOfSlotsGroup);
			hintSlotFeaturesGroups.add(densityofBrothersGroup);
			hintFeaturableFeaturesGroups.add(minimumTreeDistanceGroup);

			trainingDatasets = new ArrayList<Dataset>();

			for (String domain : domains) {
				for (Integer trainingFold : trainingFolds) {
					datasetsPath = String.format("%s/Datasets/%s/%s",datasetsRoot, domain, trainingFold);
					datasetReader.addDataset(datasetsPath, 1.0, trainingDatasets);
				}
			}

			modelHandler.setFeaturableFeaturesGroups(featurableFeaturesGroups);
			modelHandler.setSlotFeaturesGroups(slotFeaturesGroups);
			modelHandler.setHintsFeaturableFeaturesGroups(hintFeaturableFeaturesGroups);
			modelHandler.setHintsSlotFeaturesGroups(hintSlotFeaturesGroups);
			clock.start();
			modelHandler.trainModel(trainingDatasets, new HashMap<>());
			clock.stop();

			FileUtilsCust.createCSV(String.format("%s/trainingTime.csv",modelHandler.getClassifiersRootFolder()));
			FileUtilsCust.addLine(String.format("%s/trainingTime.csv",modelHandler.getClassifiersRootFolder()), clock.getCPUTime());
			modelHandler.closeContext();
		}


		//

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
