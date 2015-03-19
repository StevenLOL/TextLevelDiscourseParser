package mstparser;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Scanner;

import wl.Main;

public class DependencyParser {

    public static ParserOptions options;
    public static boolean evaluting = false;

    private DependencyPipe pipe;
    private DependencyDecoder decoder;
    private Parameters params;
    
//  added later
    public static iFeature[] ifeatures = null;
    public static iFeature singleFeature = null;
    public static File[] featureFiles = null; 
    public static int currentFeature = -1;
    public static WebParser webParser = null;
    
    public DependencyParser(DependencyPipe pipe, ParserOptions options) {
	this.pipe=pipe;
	DependencyParser.options = options;

	// Set up arrays
	params = new Parameters(pipe.dataAlphabet.size());
	decoder = options.secondOrder ? 
	    new DependencyDecoder2O(pipe) : new DependencyDecoder(pipe);
    }

    public void train(int[] instanceLengths, String trainfile, File train_forest) 
	throws IOException {
		
	//System.out.print("About to train. ");
	//System.out.print("Num Feats: " + pipe.dataAlphabet.size());
		
	int i = 0;
	for(i = 0; i < options.numIters; i++) {
			
	    System.out.print(" Iteration "+i);
	    //System.out.println("========================");
	    //System.out.println("Iteration: " + i);
	    //System.out.println("========================");
	    System.out.print("[");

	    long start = System.currentTimeMillis();

	    trainingIter(instanceLengths,trainfile,train_forest,i+1);

	    long end = System.currentTimeMillis();
	    //System.out.println("Training iter took: " + (end-start));
	    System.out.println("|Time:"+(end-start)+"]");			
	}

	params.averageParams(i*instanceLengths.length);
		
    }

    private void trainingIter(int[] instanceLengths, String trainfile, 
			      File train_forest, int iter) throws IOException {

	int numUpd = 0;
	ObjectInputStream in = new ObjectInputStream(new FileInputStream(train_forest));
	boolean evaluateI = true;

	int numInstances = instanceLengths.length;

	for(int i = 0; i < numInstances; i++) {
	    if((i+1) % 500 == 0) {
		System.out.print((i+1)+",");
		//System.out.println("  "+(i+1)+" instances");
	    }

	    int length = instanceLengths[i];

	    // Get production crap.
	    FeatureVector[][][] fvs = new FeatureVector[length][length][2];
	    double[][][] probs = new double[length][length][2];
	    FeatureVector[][][][] nt_fvs = new FeatureVector[length][pipe.types.length][2][2];
	    double[][][][] nt_probs = new double[length][pipe.types.length][2][2];
	    FeatureVector[][][] fvs_trips = new FeatureVector[length][length][length];
	    double[][][] probs_trips = new double[length][length][length];
	    FeatureVector[][][] fvs_sibs = new FeatureVector[length][length][2];
	    double[][][] probs_sibs = new double[length][length][2];

	    DependencyInstance inst;

	    if(options.secondOrder) {
		inst = ((DependencyPipe2O)pipe).readInstance(in,length,fvs,probs,
							     fvs_trips,probs_trips,
							     fvs_sibs,probs_sibs,
							     nt_fvs,nt_probs,params);
	    }

	    else
		inst = pipe.readInstance(in,length,fvs,probs,nt_fvs,nt_probs,params);

	    double upd = (double)(options.numIters*numInstances - (numInstances*(iter-1)+(i+1)) + 1);
	    int K = options.trainK;
	    Object[][] d = null;
	    if(options.decodeType.equals("proj")) {
		if(options.secondOrder)
		    d = ((DependencyDecoder2O)decoder).decodeProjective(inst,fvs,probs,
									fvs_trips,probs_trips,
									fvs_sibs,probs_sibs,
									nt_fvs,nt_probs,K);
		else
		    d = decoder.decodeProjective(inst,fvs,probs,nt_fvs,nt_probs,K);
	    }
	    if(options.decodeType.equals("non-proj")) {
		if(options.secondOrder)
		    d = ((DependencyDecoder2O)decoder).decodeNonProjective(inst,fvs,probs,
								       fvs_trips,probs_trips,
								       fvs_sibs,probs_sibs,
								       nt_fvs,nt_probs,K);
		else
		    d = decoder.decodeNonProjective(inst,fvs,probs,nt_fvs,nt_probs,K);
	    }
	    params.updateParamsMIRA(inst,d,upd);

	}

	//System.out.println("");	
	//System.out.println("  "+numInstances+" instances");

	System.out.print(numInstances);
		
	in.close();

    }

    ///////////////////////////////////////////////////////
    // Saving and loading models
    ///////////////////////////////////////////////////////
    public void saveModel(String file) throws IOException {
	ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
	out.writeObject(params.parameters);
	out.writeObject(pipe.dataAlphabet);
	out.writeObject(pipe.typeAlphabet);
	out.close();
    }

    public void loadModel(String file) throws Exception {
	ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
	params.parameters = (double[])in.readObject();
	pipe.dataAlphabet = (Alphabet)in.readObject();
	pipe.typeAlphabet = (Alphabet)in.readObject();
	in.close();
	pipe.closeAlphabets();
    }

    //////////////////////////////////////////////////////
    // Get Best Parses ///////////////////////////////////
    //////////////////////////////////////////////////////
    public void outputParses () throws IOException {

	String tFile = options.testfile;
	String file = options.outfile;

	long start = System.currentTimeMillis();

	pipe.initInputFile(tFile);
	pipe.initOutputFile(file);

	System.out.print("Processing Sentence: ");
	DependencyInstance instance = pipe.nextInstance();
	int cnt = 0;
	while(instance != null) {
	    cnt++;
	    System.out.print(cnt+" ");
	    String[] forms = instance.forms;
			
	    int length = forms.length;

	    FeatureVector[][][] fvs = new FeatureVector[forms.length][forms.length][2];
	    double[][][] probs = new double[forms.length][forms.length][2];
	    FeatureVector[][][][] nt_fvs = new FeatureVector[forms.length][pipe.types.length][2][2];
	    double[][][][] nt_probs = new double[forms.length][pipe.types.length][2][2];
	    FeatureVector[][][] fvs_trips = new FeatureVector[length][length][length];
	    double[][][] probs_trips = new double[length][length][length];
	    FeatureVector[][][] fvs_sibs = new FeatureVector[length][length][2];
	    double[][][] probs_sibs = new double[length][length][2];
	    if(options.secondOrder)
		((DependencyPipe2O)pipe).fillFeatureVectors(instance,fvs,probs,
							    fvs_trips,probs_trips,
							    fvs_sibs,probs_sibs,
							    nt_fvs,nt_probs,params);
	    else
		pipe.fillFeatureVectors(instance,fvs,probs,nt_fvs,nt_probs,params);

	    int K = options.testK;
	    Object[][] d = null;
	    if(options.decodeType.equals("proj")) {
		if(options.secondOrder)
		    d = ((DependencyDecoder2O)decoder).decodeProjective(instance,fvs,probs,
									fvs_trips,probs_trips,
									fvs_sibs,probs_sibs,
									nt_fvs,nt_probs,K);
		else
		    d = decoder.decodeProjective(instance,fvs,probs,nt_fvs,nt_probs,K);
	    }
	    if(options.decodeType.equals("non-proj")) {
		if(options.secondOrder)
		    d = ((DependencyDecoder2O)decoder).decodeNonProjective(instance,fvs,probs,
								       fvs_trips,probs_trips,
								       fvs_sibs,probs_sibs,
								       nt_fvs,nt_probs,K);
		else
		    d = decoder.decodeNonProjective(instance,fvs,probs,nt_fvs,nt_probs,K);
	    }

	    String[] res = ((String)d[0][1]).split(" ");

	    String[] pos = instance.cpostags;

	    String[] formsNoRoot = new String[forms.length-1];
	    String[] posNoRoot = new String[formsNoRoot.length];
	    String[] labels = new String[formsNoRoot.length];
	    int[] heads = new int[formsNoRoot.length];

	    Arrays.toString(forms);
	    Arrays.toString(res);
	    for(int j = 0; j < formsNoRoot.length; j++) {
		formsNoRoot[j] = forms[j+1];
		posNoRoot[j] = pos[j+1];

		String[] trip = res[j].split("[\\|:]");
		labels[j] = pipe.types[Integer.parseInt(trip[2])];
		heads[j] = Integer.parseInt(trip[0]);
	    }

	    pipe.outputInstance(new DependencyInstance(formsNoRoot, posNoRoot, labels, heads));

	    instance = pipe.nextInstance();
	}
	pipe.close();
		
	long end = System.currentTimeMillis();
	System.out.println("Took: " + (end-start));

    }

    /**
     * this is a function to generate our own feature vectors.
     * @throws IOException
     */
    public static void generateFeatureVector() throws IOException {
    	

//    	version control is important... git, a sad story...
//		File input = new File("./feat.txt");
//		Scanner cin = new Scanner(input);
//		DependencyParser.ifeatures = new iFeature[400];
//   		System.err.println("Adding features...");
//   		int current = 0;
//   		featureFiles = new File[400];
//   		while(cin.hasNextLine()){
//   			File featFile = new File(cin.nextLine().trim());
////   	    avoid loading too many files from memory
//   			if(options.train || current>340)
//   				ifeatures[current] = new iFeature(featFile);
//   			featureFiles[current] = featFile;
//   			current++;
//   			
//   			if(current%20 == 0){
//   				System.err.println("Processing "+current+" files...");
//   			}// end if clause
//   		}// end while loop
    	
   		
//   	end for above part
   		
//    	to run when memory is limited
    	if(featureFiles != null) return;
    	
    	if(options.preload == false)
    		System.err.println("Loading feature files...");
    	
//		create an array to hold all files
		ArrayList<File> fileArray = new ArrayList<File>();
		fileArray.clear();
		File inputDir = new File(Main.TRAIN_PATH);
//		invalid directory path
		if(inputDir.isDirectory() == false){
			System.err.println("Invalid input directory path: "+Main.TRAIN_PATH);
			System.exit(1);
		}
//		add all file path ends with ".feat"
		for(File file : inputDir.listFiles()){
			if(file.toString().endsWith(Main.FEAT_SUFFIX))
				fileArray.add(file);
		}// end for loop
		
		int trainNumber = fileArray.size();
		
//		for test data directory
		inputDir = new File(Main.TEST_PATH);
		if(inputDir.isDirectory() == false){
			System.err.println("Invalid input directory path: "+Main.TEST_PATH);
			System.exit(1);
		}
		for(File file : inputDir.listFiles()){
			if(file.toString().endsWith(Main.FEAT_SUFFIX))
				fileArray.add(file);
		}// end for loop
    	
		featureFiles = new File[fileArray.size()];
		for(int i=0; i<fileArray.size(); i++)
			featureFiles[i] = fileArray.get(i);
		
		if(options.preload == false)
			System.err.println("Finishing loading "+fileArray.size()+" files.");
    	
//    	return;
    	if(options.preload == true){
    		if(ifeatures != null)
    			return;
    		System.err.println("Adding features...");
    		ifeatures = new iFeature[fileArray.size()+10];
    		int beginIndex = 0;
    		if(options.train == false)
    			beginIndex = trainNumber;
    		for(int i=beginIndex; i<fileArray.size(); i++){
    			File file = fileArray.get(i);
    			ifeatures[i] = new iFeature(file);
//    		
//    			output some hints
    			if((i+1)%20 == 0)
    				System.err.println("Loading "+(i+1)+" files.");
//    		
    		}// end for loop
    	}// end if clause
//    	
    	return;
    }// end method generateFeatureVector
    
    public void findTopFeatures(){
    	gnu.trove.TIntObjectHashMap featMap = this.pipe.dataAlphabet.featureMap;
    	int numFeat = featMap.size();
    	double[] w = this.params.parameters;
//    	error occurs
    	if(numFeat != w.length){
    		System.err.println("Number of weights aren't equal to number of features.");
    		return;
    	}
    	ArrayList<FeatureWeight> arr = new ArrayList<FeatureWeight>();
    	arr.clear();
    	for(int i=0; i<numFeat; i++)
    		arr.add(new FeatureWeight((String)featMap.get(i), w[i]));
//    	sort feature weights
    	Collections.sort(arr);
    	
//    	output result
    	System.err.println();
    	System.err.println("Top 10 features: ");
    	for(int i=0; i<10; i++)
    		System.err.println(arr.get(i).description+"  "+arr.get(i).weight);
    	System.err.println();
    	
    	return;
    }// end method findTopFeatures
    
    /////////////////////////////////////////////////////
    // RUNNING THE PARSER
    ////////////////////////////////////////////////////
    public static void main (String[] args) throws FileNotFoundException, Exception
    {
	
	options = new ParserOptions(args);
	webParser = new WebParser(options.in);
	if (options.train) {
		
		DependencyParser.evaluting = false;
//		added by me
		generateFeatureVector();
		
		
	    DependencyPipe pipe = options.secondOrder ? 
		new DependencyPipe2O (options) : new DependencyPipe (options);

	    int[] instanceLengths = 
		pipe.createInstances(options.trainfile,options.trainforest);
		
	    pipe.closeAlphabets();
	    
	    DependencyParser dp = new DependencyParser(pipe, options);
	    
	    int numFeats = pipe.dataAlphabet.size();
	    int numTypes = pipe.typeAlphabet.size();
	    System.out.print("Num Feats: " + numFeats);	
	    System.out.println(".\tNum Edge Labels: " + numTypes);
	    
	    dp.train(instanceLengths,options.trainfile,options.trainforest);
	    
	    System.out.print("Saving model...");
	    dp.saveModel(options.modelName);
	    System.out.print("done.");	    

	}
		
	if (options.test) {
		
//		to load feature vectors for test data
//		if(options.train == false)
//			generateFeatureVector();
		
		DependencyParser.evaluting = true;
	    DependencyPipe pipe = options.secondOrder ? 
		new DependencyPipe2O (options) : new DependencyPipe (options);

	    DependencyParser dp = new DependencyParser(pipe, options);

	    System.out.print("\tLoading model...");
	    dp.loadModel(options.modelName);
	    System.out.println("done.");
	    
//	    added by Liang Wang
//	    dp.findTopFeatures();

	    pipe.closeAlphabets();

	    dp.outputParses();
	}
		
	System.out.println();

	if (options.eval) {
	    System.out.println("\nEVALUATION PERFORMANCE:");
	    DependencyEvaluator.evaluate(options.goldfile, 
					 options.outfile, 
					 options.format);
//	    added by Wang Liang
	    DependencyEvaluator.myEvaluate(options.goldfile, options.format);	    
	}
	if (options.fmt.equalsIgnoreCase("xml"))
		webParser.generateXML(options.out);
	else if (options.fmt.equalsIgnoreCase("json"))
		webParser.generateJson(options.out);
//	findTopFeatures();
    }

}
