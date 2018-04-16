package featuresCalculation;

import dataset.Dataset;
import dataset.Record;
import dataset.Slot;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.simple.parser.ParseException;
import utils.FileUtilsCust;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FeaturesTablesBuilder implements Serializable{
	private static CSVPrinter csvPrinterDatasets;
	private static CSVPrinter csvPrinterAttributes;
	private static CSVPrinter csvPrinterRecords;
	private static final long serialVersionUID = 1295466241822143370L;
	
	//Constructors---------------------------------------------------

	public FeaturesTablesBuilder() {
		//this.tablesMap = new HashMap<String, List<FeaturesTable>>();
	}
	
	//Properties-----------------------------------------------------
	
	//private Map<String, List<FeaturesTable>> tablesMap;
	
	//public Map<String, List<FeaturesTable>> getTablesMap() {
		//return tablesMap;
	//}
	
	//Interface methods----------------------------------------------
	
	public void initialize(String outputFolder, Set<Feature<?>> datasetFeatures, Set<Feature<?>> recordFeatures, Set<Feature<?>> attributeFeatures) throws IOException, ParseException {
		//List<FeaturesTable> datasetTables;
		//List<FeaturesTable> recordTables;
		//List<FeaturesTable> attributeTables;
		//FeaturesTable featuresTable;
		File outFile;
		String featureName;
		List<String> features;
		FileWriter fileWriter;

		/*
		datasetTables = new ArrayList<FeaturesTable>();
		recordTables = new ArrayList<FeaturesTable>();
		attributeTables = new ArrayList<FeaturesTable>();
		outFile = new File(String.format("resources/%s/attributes", outputFolder));
		if (!outFile.exists()) {
			outFile.mkdirs();
		}
		outFile = new File(String.format("resources/%s/records", outputFolder));
		if (!outFile.exists()) {
			outFile.mkdirs();
		}*/
		outFile = new File(outputFolder);
		if (!outFile.exists()) {
			outFile.mkdirs();
		}

		outFile = new File(String.format("%s/datasets.csv", outputFolder));
		if(!outFile.exists()) {
			outFile.getParentFile().mkdirs();
			fileWriter = new FileWriter(outFile, true);
			csvPrinterDatasets = new CSVPrinter(fileWriter, CSVFormat.DEFAULT.withRecordSeparator("\n"));
			features = new ArrayList<String>();
			features.add("name");
			for (Feature feature : datasetFeatures) {
				featureName = feature.toString();
				features.add(featureName);
			}
			csvPrinterDatasets.printRecord(features);
		}
		
		outFile = new File(String.format("%s/records.csv", outputFolder));
		if (!outFile.exists()) {
			outFile.getParentFile().mkdirs();
			fileWriter = new FileWriter(outFile, true);
			csvPrinterRecords = new CSVPrinter(fileWriter, CSVFormat.DEFAULT.withRecordSeparator("\n"));
			features = new ArrayList<String>();
			features.add("name");
			for (Feature feature : recordFeatures) {
				featureName = feature.toString();
				features.add(featureName);
			}
			csvPrinterRecords.printRecord(features);
		}
		
		outFile = new File(String.format("%s/attributes.csv", outputFolder));
		if (!outFile.exists()) {
			outFile.getParentFile().mkdirs();
			fileWriter = new FileWriter(outFile, true);
			csvPrinterAttributes = new CSVPrinter(fileWriter, CSVFormat.DEFAULT.withRecordSeparator("\n"));
			features = new ArrayList<String>();
			features.add("name");
			for (Feature feature : attributeFeatures) {
				featureName = feature.toString();
				features.add(featureName);
			}
			csvPrinterAttributes.printRecord(features);
		}
	}
	
	public void addVector(Featurable featurable, FeaturesVector featuresVector, String outputFolder) throws IOException {
		List<FeaturesTable> datasetTables;
		List<FeaturesTable> recordTables;
		List<FeaturesTable> attributeTables;
		List<FeaturesTable> featuresTables;
		List<String> lineValues;
		FeaturesTable featuresTable;
		Dataset dataset;
		Slot slot;
		String slotClass;
		String folder;
		File folderFile;
		File file;
		File[] files;
		String fileName;
		String className;
		
		//datasetTables = tablesMap.get("datasets");
		//recordTables = tablesMap.get("records");
		//attributeTables = tablesMap.get("attributes");
		
		if(featurable instanceof Dataset){
			lineValues = new ArrayList<String>();
			lineValues.add(featurable.getName().replace(',', '-'));
			lineValues.addAll(featuresVector.getRawValues());
			csvPrinterDatasets.printRecord(lineValues);
		} else {
			slot = (Slot)featurable;
			slotClass = slot.getSlotClass();
			
			//Selection of the list that will be used
			
			//Addition of the new vector to each Table
			featuresVector.setVectorClass(slotClass);
		    lineValues = new ArrayList<String>();
			lineValues.add(featurable.getName().replace(',', '-'));
			lineValues.addAll(featuresVector.getRawValues());

			if(slot instanceof Record) {
				csvPrinterRecords.printRecord(lineValues);
			} else {
				csvPrinterAttributes.printRecord(lineValues);
			}
		}
	}

	public void closeWriters() throws IOException {
		csvPrinterDatasets.close();
		csvPrinterRecords.close();
		csvPrinterAttributes.close();
	}
	
	//Ancillary methods----------------------------------------------
	
	
}
