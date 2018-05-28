package main;

import com.google.common.base.Charsets;
import com.hubspot.jinjava.Jinjava;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;
import org.spark_project.guava.io.Resources;
import utils.RandomForestParsing.RandomForestParser;
import utils.RandomForestParsing.Tree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OthersDriver {

	public static void main(String[] args) throws IOException {
		System.out.println(Double.parseDouble("-3.22e-3"));

		/*
		List<Pair<String, List<String>>> treeStrings;
		treeStrings = RandomForestParser.separateTrees(new File("E:/model/classifiersAndTables/Awards/1/modelClassifiers/classifiersParsed/hintBased/attributes/email/email.txt"));
		List<Tree> trees = treeStrings.stream().map(t -> new Tree(t, null)).collect(Collectors.toList());
		File treesFolder = new File("treePages/");
		treesFolder.mkdirs();
		RandomForestParser.makeTreeViz(trees, treesFolder.getPath());*/
	}
}
