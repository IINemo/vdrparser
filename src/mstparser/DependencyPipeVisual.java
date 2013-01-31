package mstparser;

import mstparser.io.*;
import mstparser.visual.Image;
import mstparser.visual.Polygon;
import mstparser.visual.SpatialRelation;

import java.awt.geom.Point2D;
import java.io.*;

import gnu.trove.*;

import java.util.*;

import com.googlecode.javacv.cpp.*;

/**
 * A DependencyPipe subclass for parsing Visual Dependency Representations.
 * 
 * The types of features needed for parsing these representations are so
 * different from language that it warranted a new class.
 * 
 * TODO: Read real visual data from a corresponding image and LabelMe XML file. 
 *
 * @author delliott
 * 
 */
public class DependencyPipeVisual extends DependencyPipe
{

    private DependencyReader correspondingReader;

    private ParserOptions options;

    private List<DependencyInstance> sourceInstances;
    private List<List<Alignment>> alignments;
    private List<Image> images;

    public DependencyPipeVisual(ParserOptions options) throws IOException
    {
        super(options);
        this.options = options;
        if (options.train && options.sourceFile != null)
        {
            this.readSourceInstances(options.sourceFile);
            this.readAlignments(options.alignmentsFile);
        }
        else if (options.test && options.testSourceFile != null)
        {
            this.readSourceInstances(options.testSourceFile);
            this.readAlignments(options.testAlignmentsFile);
        }
        if (options.train && options.imagesFile != null)
        {
            this.images = new ArrayList<Image>();
            this.readImageData(options.imagesFile, options.xmlFile);
        }
        else if (options.test && options.testImagesFile != null)
        {
            this.images = new ArrayList<Image>();
            this.readImageData(options.testImagesFile, options.testXmlFile);           
        }
        correspondingReader = DependencyReader.createDependencyReader(
                options.format, options.discourseMode);
        sourceInstances = new LinkedList<DependencyInstance>();
    }

    /*
     * protected final DependencyInstance nextInstance() throws IOException {
     * DependencyInstance instance = depReader.getNext(); if (instance == null
     * || instance.forms == null) { return null; }
     * 
     * //depReader.incCount();
     * 
     * instance.setFeatureVector(createFeatureVector(instance));
     * 
     * String[] labs = instance.deprels; int[] heads = instance.heads;
     * 
     * StringBuffer spans = new StringBuffer(heads.length * 5); for (int i = 1;
     * i < heads.length; i++) {
     * spans.append(heads[i]).append("|").append(i).append
     * (":").append(typeAlphabet.lookupIndex(labs[i])).append(" "); }
     * instance.actParseTree = spans.substring(0, spans.length() - 1);
     * 
     * return instance; }
     */

    /**
     * Reads the content of LabelMe XML files and the associated raw image data
     * into an Image object. These data structures are used to infer
     * image-level statistics and features about the data set.
     * 
     * @param imagesFile
     * @param xmlFile
     */
    private void readImageData(String imagesFile, String xmlFile) 
    {
    	try 
    	{
    	    System.out.println("Reading image data into memory");
			BufferedReader in = new BufferedReader(new FileReader(imagesFile));
			String line = null;
			while((line = in.readLine()) != null)
			{
				Image i = new Image(line);
				images.add(i);
			}
			in.close();
			in = new BufferedReader(new FileReader(xmlFile));
			line = null;
			int count = 0;
			while((line = in.readLine()) != null)
			{
				Image i = images.get(count);
				i.setXMLFile(line);
				count++;
			}
			for (Image i: images)
			{
				i.parseXMLFile();
				i.calculateSpatialRelationships();
				//System.out.println(i.toString());
			}
		} 
    	catch (FileNotFoundException e) 
    	{
			e.printStackTrace();
		} 
    	catch (IOException e) 
		{
			e.printStackTrace();
		}
    	
	}

	public int[] createInstances(String file, File featFileName)
            throws IOException
    {

        createAlphabet(file);

        System.out.println(featFileName.getAbsolutePath());

        System.out.println("Num Features: " + dataAlphabet.size());

        labeled = depReader.startReading(file);

        TIntArrayList lengths = new TIntArrayList();

        ObjectOutputStream out = options.createForest ? new ObjectOutputStream(
                new FileOutputStream(featFileName)) : null;

        DependencyInstance instance = depReader.getNext();
        depReader.resetCount();

        System.out.println("Creating Feature Vector Instances: ");
        while (instance != null)
        {            
            /*if (options.verbose)
            {
                System.out.println(instance.toString());
            }*/
            System.out.print(depReader.getCount() + " ");

            FeatureVector fv = this.createFeatureVector(instance);

            instance.setFeatureVector(fv);

            String[] labs = instance.deprels;
            int[] heads = instance.heads;

            StringBuffer spans = new StringBuffer(heads.length * 5);
            for (int i = 1; i < heads.length; i++)
            {
                spans.append(heads[i]).append("|").append(i).append(":")
                        .append(typeAlphabet.lookupIndex(labs[i])).append(" ");
            }
            instance.actParseTree = spans.substring(0, spans.length() - 1);

            lengths.add(instance.length());

            if (options.createForest)
            {
                writeInstance(instance, out);
            }

            instance = null;

            instance = depReader.getNext();
            depReader.incCount();
        }

        System.out.println();

        closeAlphabets();

        if (options.createForest)
        {
            out.close();
        }

        return lengths.toNativeArray();

    }

    private final void createAlphabet(String file) throws IOException
    {

        System.out.println("Creating Alphabet ... ");

        labeled = depReader.startReading(file);

        DependencyInstance instance = depReader.getNext();

        while (instance != null)
        {
            //System.out.println(String.format("Instance %s", depReader.getCount()));
            String[] labs = instance.deprels;
            for (int i = 0; i < labs.length; i++)
            {
                typeAlphabet.lookupIndex(labs[i]);
            }

            createFeatureVector(instance);

            instance = depReader.getNext();
            depReader.incCount();
        }

        closeAlphabets();

        System.out.println("Done.");
    }
    
    /** 
     * @param index the position of the word in the DependencyInstance
     * @param instanceHeads the array of instance heads in the Dependency Instance.
     * @return true if the word at position index has a head at -1.
     */
    public boolean checkForRootAttach(int index, int[] instanceHeads)
    {
        if (instanceHeads[index] == -1)
        {
            return true;
        }
        return false;
    }      

    /**
     * Add a set of unigram features for word i in a DependencyInstance
     * 
     * 
     * @param instance
     * @param fv
     */
    public void addLinguisticUnigramFeatures(DependencyInstance instance,
            int headIndex, int argIndex, String label, FeatureVector fv)
    {
        int[] heads = instance.heads;
        String[] forms = instance.forms;
        
        String headForm = checkForRootAttach(headIndex, heads) ? "ROOT" : forms[headIndex];
        String argForm = forms[argIndex];

        // Get features for the siblings
        int argCounter = 0;
        
        List<String> siblings = new ArrayList<String>();
        
        for (int j=0; j < instance.length(); j++)
        {
            if (heads[j] == headIndex)
            {
                argCounter++;
                if (j != headIndex)
                {
                    siblings.add(forms[j]);
                }
            }
        }
        String[] sortedSiblings = siblings.toArray(new String[0]);
        Arrays.sort(sortedSiblings);
        
        StringBuilder siblingForms = new StringBuilder();
        
        for (int k = 0; k < sortedSiblings.length; k++)
        {
            siblingForms.append(" S=" + sortedSiblings[k]);
        }
        
        StringBuilder feature;

        //1. H=Head
        feature = new StringBuilder("H=" + headForm);
        this.add(feature.toString(), fv);
        
        //3. H=Head HA=labelhead−arg
        feature = new StringBuilder("H=" + headForm + " HA=" + label);
        this.add(feature.toString(), fv);
        
        //5. H=Head A#=no. args
        feature = new StringBuilder("H=" + headForm + " #A=" + argCounter);
        this.add(feature.toString(), fv);
        
        //6. H=Head A#=no. args HA=labelhead−arg
        feature = new StringBuilder("H=" + headForm + " #A=" + argCounter + " HA=" + label);
        this.add(feature.toString(), fv);

        //2. A=Arg
        feature = new StringBuilder("A=" + forms[argIndex]);
        this.add(feature.toString(), fv);

        //4. A=Arg HA=labelhead−arg
        feature = new StringBuilder("A=" + forms[argIndex] + " HA="+ label);
        this.add(feature.toString(), fv);
     
        //7. A=Arg S#=no. siblings
        feature = new StringBuilder("A=" + argForm + " #S=" + (argCounter-1));
        this.add(feature.toString(), fv);
        
        //8. A=Arg S#=no. siblings HA=labelhead−arg
        feature = new StringBuilder("A=" + argForm + " #S=" + (argCounter-1) + " HA=" + label);
        this.add(feature.toString(), fv);
        
        feature = new StringBuilder("A=" + argForm);
  
        //9. A=Arg S=Sibling1,...,N
        feature = feature.append(siblingForms.toString());        
        this.add(feature.toString(), fv);
        
        //10. A=Arg S=Sibling1,...,N HA=labelhead−arg
        feature = new StringBuilder("A=" + argForm + " HA=" + label);
        feature = feature.append(siblingForms.toString());        
        this.add(feature.toString(), fv);
        
        //11. A=Arg S#=no. siblings S=Sibling1,...,N
        feature = new StringBuilder("A=" + argForm + " #S=" + (argCounter-1));
        feature = feature.append(siblingForms.toString());        
        this.add(feature.toString(), fv);
        
        //12. A=Arg S#=no. siblings S=Sibling1,...,N HA=labelhead−arg
        feature = new StringBuilder("A=" + argForm + " #S=" + (argCounter-1) + " HA=" + label);
        feature = feature.append(siblingForms.toString());        
        this.add(feature.toString(), fv);
    }
   
    /**
     * 
     * @param instance
     * @param headIndex
     * @param argIndex
     * @param label
     * @param fv
     */
    public void addLinguisticBigramFeatures(DependencyInstance instance,
            int headIndex, int argIndex, String label, FeatureVector fv)
    {
        int[] heads = instance.heads;
        String[] forms = instance.forms;
        String headForm = checkForRootAttach(headIndex, heads) ? "ROOT" : forms[headIndex];
        String argForm = forms[argIndex];
        StringBuilder feature;

        //13. H=Head A=Arg
        feature = new StringBuilder("H=" + headForm + " A=" + argForm);
        add(feature.toString(), fv);

        //14. H=Head A=Arg HA=labelhead−arg
        feature = new StringBuilder("H=" + headForm + " A=" + argForm + " HA=" + label);
        add(feature.toString(), fv);
        
        /*int argCounter = 0;
        
        for (int j=0; j < instance.length(); j++)
        {
            if (heads[j] == headIndex)
            {
                argCounter++;
            }
        }
        
        //15. H=Head A=Arg A#=no. args
        feature = new StringBuilder("H=" + headForm + " A=" + argForm + " #A=" + argCounter);
        add(feature.toString(), fv);

        //16. H=Head A=Arg A#=no. args HA=labelhead−arg
        feature = new StringBuilder("H=" + headForm + " A=" + argForm + " #A=" + argCounter + " HA=" + label);
        add(feature.toString(), fv);*/
    }
    
    /**
     * Add features to the model based on Grandparent-Grandchild relationships.
     *
     * TODO: Correctly implement fillFeatureVectors before re-enabling this method.
     * 
     * @param instance
     * @param i
     * @param headIndex
     * @param argIndex
     * @param label
     * @param fv
     */
    public void addLinguisticGrandparentGrandchildFeatures(DependencyInstance instance, 
                                                 int i, int headIndex, 
                                                 int argIndex, String label, 
                                                 FeatureVector fv)
    {
        int[] heads = instance.heads;
        String[] forms = instance.forms;
        
        int gpIndex = heads[headIndex];

        if (gpIndex == -1)
        {
        	// This is the dummy <root> node
        	return;
        }
        
        String headForm;
        String argForm = forms[argIndex];
        headForm = forms[headIndex];
        
        String gpForm = gpIndex == 0 ? "ROOT" : forms[gpIndex];
        String gpRel = gpIndex == 0 ? "-" : instance.deprels[headIndex];
        
        StringBuilder feature;

        //17. GP=Grandparent H=Head A=Arg
        feature = new StringBuilder("GP=" + gpForm + " H=" + headForm + " A=" + forms[argIndex]);
        add(feature.toString(), fv);
        
        //18. GP=Grandparent H=Head A=Arg GH=labelgrandparent−head
        feature = new StringBuilder("GP=" + gpForm + " H=" + headForm + " A=" + forms[argIndex] + " GH=" + gpRel);
        add(feature.toString(), fv);

        //19. GP=Grandparent H=Head A=Arg HA=labelhead−arg
        feature = new StringBuilder("GP=" + gpForm + " H=" + headForm + " A=" + forms[argIndex] + " HA=" + label);
        add(feature.toString(), fv);
                
        //20. GP=Grandparent H=Head A=Arg GH=labelgrandparent−head HA=labelhead−arg
        feature = new StringBuilder("GP=" + gpForm + " H=" + headForm + " A=" + forms[argIndex] + " GH=" + gpRel + " HA=" + label);
        add(feature.toString(), fv);
    }
    
    /**
     * Adds bigram features based on the siblings of the argument.
     * 
     *  TODO: Correctly implement fillFeatureVectors before re-enabling this method.
     * 
     * @param instance
     * @param i
     * @param headIndex
     * @param argIndex
     * @param label
     * @param fv
     */
    public void addLinguisticBigramSiblingFeatures(DependencyInstance instance, 
                                                    int i, int headIndex, 
                                                    int argIndex, String label, 
                                                    FeatureVector fv)
    {
        int[] heads = instance.heads;
        String[] forms = instance.forms;

        String headForm;
        String argForm = forms[argIndex];
        if (heads[headIndex] == -1)
        {
        	headForm = "ROOT";
        }
        else
        {
        	headForm = forms[headIndex];
        }
        
        StringBuilder feature;
        StringBuilder siblingForms = new StringBuilder();
        List<String> siblingFormsList = new ArrayList<String>();

        int argCounter = 0;
                
        for (int j=0; j < instance.length(); j++)
        {
            if (heads[j] == headIndex)
            {
                argCounter++;
                if (j != i)
                {
                	siblingFormsList.add(forms[j]);
                }
            }
        }
        
        String[] sortedSiblings = siblingFormsList.toArray(new String[0]);
        
        Arrays.sort(sortedSiblings);
        
        for (int k=0; k < sortedSiblings.length; k++)
        {
            siblingForms.append(" S=" + sortedSiblings[k]);
        }
                
        //21. H=Head A=Arg S#=no. siblings
        feature = new StringBuilder("H=" + headForm + " A=" + forms[argIndex] + " #S=" + (argCounter-1));
        add(feature.toString(), fv);
        
        //22. H=Head A=Arg S=Sibling1,...,N
        feature = new StringBuilder("H=" + headForm + " A=" + forms[argIndex]);
        feature.append(siblingForms.toString());
        add(feature.toString(), fv);
        
        //23. H=Head A=Arg S#=no. siblings S=Sibling1,...,N
        feature = new StringBuilder("H=" + headForm + " A=" + forms[argIndex] + " #S=" + (argCounter-1));
        feature.append(siblingForms.toString());
        add(feature.toString(), fv);
        
        //24. H=Head A=Arg S#=no. siblings HA=labelhead−arg
        feature = new StringBuilder("H=" + headForm + " A=" + forms[argIndex] + " #S=" + (argCounter-1) + " HA=" + label);
        add(feature.toString(), fv);
        
        //25. H=Head A=Arg S#=no. siblings S=Sibling1,...,N HA=labelhead−arg
        feature = new StringBuilder("H=" + headForm + " A=" + forms[argIndex] + " #S=" + (argCounter-1) + " HA=" + label);
        feature.append(siblingForms.toString());
        add(feature.toString(), fv);
    }
    
    /**
     * Add the Quasi-synchronous Grammar features to the model.
     * 
     * For a pair of Alignments, first and second, and the representation of the
     * target sentence and its corresponding source sentence, add a feature to
     * the model that represents the head word and argument word (in the target)
     * and syntactic configuration of the those words in the source.
     * 
     * @param theseAlignments
     * @param target
     * @param source
     * @param fv
     */
    public void addQGFeatures(List<Alignment> theseAlignments, int small,
            int large, boolean attR, String distBool,
            DependencyInstance target, DependencyInstance source,
            FeatureVector fv)
    {
        // No QG features to add.
        if (theseAlignments.size() == 0)
        {
            return;
        }

        String att = attR ? "RA" : "LA";

        for (Alignment a : theseAlignments)
        {
            for (Alignment b : theseAlignments)
            {
                if (a != b)
                {
                    if (a.getTargetIndex() == small
                            && b.getTargetIndex() == large
                            || a.getTargetIndex() == large
                            && b.getTargetIndex() == small)
                    {
                        Alignment.Configuration c = a
                                .getAlignmentConfiguration(b, target, source);
                        if (c != Alignment.Configuration.NONE)
                        {
                            int order = a.getAlignmentOrder(b, target, source);
                            String head_word, arg_word;
                            if (order == 1)
                            {
                                head_word = source.lemmas[a.getSourceIndex() + 1];
                                arg_word = source.lemmas[b.getSourceIndex() + 1];

                            }
                            else
                            {
                                head_word = source.lemmas[b.getSourceIndex() + 1];
                                arg_word = source.lemmas[a.getSourceIndex() + 1];
                            }

                            String words = String.format("QG HEAD=%s ARG=%s",
                                    head_word, arg_word);
                            String words_cfg = String.format(
                                    "QG HEAD=%s ARG=%s CFG=%s", head_word,
                                    arg_word, c.toString());
                            // String words_dir =
                            // String.format("w1=%s w2=%s dir=%s", head_word,
                            // arg_word, att);
                            // String words_cfg_dir =
                            // String.format("w1=%s w2=%s cfg=%s dir=%s",
                            // head_word, arg_word, c.toString(), att);
                            /*
                             * add(words, 1.0, fv); add(words_cfg, 10.0, fv);
                             * add(words_dir, 100.0, fv); add(words_cfg_dir,
                             * 1000.0, fv);
                             */
                            add(words, fv);
                            add(words_cfg, fv);
                            // add(words_dir, fv);
                            // add(words_cfg_dir, fv);
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds features that allow for labelled parsing.
     * 
     * TODO: Rewrite this code because it uses position-based features.
     * 
     * @param instance
     * @param wordIndex
     * @param dependencyType
     * @param attR
     * @param childFeatures
     * @param fv
     */
    public void addLabeledFeatures(DependencyInstance instance, int wordIndex,
            String dependencyType, boolean attR, boolean childFeatures, FeatureVector fv)
    {    
        /* The original implementation */
        if (!labeled)
        {
            return;
        }
        
        String[] forms = instance.forms;
        String[] pos = instance.postags;

        String att = "";
        if (attR)
        {
            att = "RA";
        }
        else
        {
            att = "LA";
        }

        att += "&" + childFeatures; // attachment direction)
        //att = "";

        String w = forms[wordIndex]; // word
        String wP = pos[wordIndex]; // postag

        String wPm1 = wordIndex > 0 ? pos[wordIndex - 1] : "STR"; // pos of proceeding word
        String wPp1 = wordIndex < pos.length - 1 ? pos[wordIndex + 1] : "END"; // pos of the next word

        add("NTS1=" + dependencyType + "&" + att, fv); // dependency relation label + direction
        add("ANTS1=" + dependencyType, fv); // dependency relation label
        for (int i = 0; i < 2; i++)
        {
            String suff = i < 1 ? "&" + att : ""; // attachment direction
            suff = "&" + dependencyType + suff; // and dependency relation label

            add("NTH=" + w + " " + wP + suff, fv); // word and pos and suff
            add("NTI=" + wP + suff, fv); // pos tag and suff
            add("NTIA=" + wPm1 + " " + wP + suff, fv); // prev pos tag and this pos tag and suff 
            add("NTIB=" + wP + " " + wPp1 + suff, fv); // this pos and prev pos and suff
            add("NTIC=" + wPm1 + " " + wP + " " + wPp1 + suff, fv); // prev pos, this pos, next pos, suff
            add("NTJ=" + w + suff, fv); // word and suff
        }
    }
    
    /**
     * Adds visual information features to the parsing model.
     * 
     * @param instance
     * @param headIndex
     * @param argIndex
     * @param label
     * @param fv
     */
    private void addVisualBigramFeatures(DependencyInstance instance,
            int headIndex, int argIndex, String label, FeatureVector fv)
    {
        if (headIndex < 1 || argIndex < 1)
        {
            // we cannot do anything with the ROOT node since there are no
            // spatial relationships between the ROOT node and any other node
            return;
        }
        
        String[][] feats = instance.feats;
        String[] forms = instance.forms;
        
        Image i = images.get(depReader.getCount());
        
        if (options.verbose)
        {
        	System.out.println(feats[headIndex][0] + " " + feats[headIndex][1]);      
        	System.out.println(feats[argIndex][0] + " " + feats[argIndex][1]);
        }
        	
        Point2D headPoint = new Point2D.Double(new Double(feats[headIndex][0].replace("\"","")), new Double(feats[headIndex][1].replace("\"","")));
        Point2D argPoint = new Point2D.Double(new Double(feats[argIndex][0].replace("\"","")), new Double(feats[argIndex][1].replace("\"","")));
        
        int h = i.findPolygon(forms[headIndex], headPoint);
        int a = i.findPolygon(forms[argIndex], argPoint);
        
        if (h > -1 &&  a > -1)
        {
            // We need to have found valid polygons for these points to continue
            
            SpatialRelation.Relations s = i.polygons[h].spatialRelations[a];
            StringBuilder feature = new StringBuilder();
            if (label == null)
            {
                // This happens at test time and we don't know which label to apply
                // so we just try all of them and believe the model will make it happy.
                for (String type: types)
                {
                    feature.append("H=" +forms[headIndex] + " A=" + forms[argIndex] + " VHA=" + s);
                    add(feature.toString(), fv);
                    feature.append(" HA=" + type);
                    add(feature.toString(), fv);
                }
            }
            else
            {
                feature.append("H=" +forms[headIndex] + " A=" + forms[argIndex] + " VHA=" + s);
                add(feature.toString(), fv);
                feature.append(" HA=" + label);
                add(feature.toString(), fv);                
            }
        }
    }    

    /**
     * This is where we calculate the features over an input, which is
     * represented as a DependencyInstance.
     */
    public FeatureVector createFeatureVector(DependencyInstance instance)
    {
        final int instanceLength = instance.length();

        String[] labs = instance.deprels;
        int[] heads = instance.heads;

        FeatureVector fv = new FeatureVector();
        for (int i = 0; i < instanceLength; i++)
        {
            if (heads[i] == -1)
            {
                continue;
            }

            /* Figure out the head and argument indices */
            int headIndex = i < heads[i] ? i : heads[i];
            int argIndex = i > heads[i] ? i : heads[i];
            boolean attR = i < heads[i] ? false : true;
            if (!attR)
            {
                int tmp = headIndex;
                headIndex = argIndex;
                argIndex = tmp;
            }

            this.addLinguisticUnigramFeatures(instance, headIndex, argIndex, labs[i], fv);
            this.addLinguisticBigramFeatures(instance, headIndex, argIndex, labs[i], fv);
            this.addVisualBigramFeatures(instance, headIndex, argIndex, labs[i], fv);
            //this.addLinguisticGrandparentGrandchildFeatures(instance, i, headIndex, argIndex, labs[i], fv);
            //this.addLinguisticBigramSiblingFeatures(instance, i, headIndex, argIndex, labs[i], fv);

            if (labeled)
            {
                addLabeledFeatures(instance, i, labs[i], attR, true, fv);
                addLabeledFeatures(instance, heads[i], labs[i], attR, false, fv);
            }
        }

        return fv;
    }

    /**
     * 
     * TODO: Rewrite all the methods that add features so they don't make naive
     *       assumptions about the data. These fill methods really need to attempt
     *       all possible combinations.
     *       
     * TODO: Make sure you never read from instance.deprels[] since this can contain
     *       the gold standard dependency relations we are trying to predict.
     * 
     * @param fvs A three-dimension array of FeatureVectors where each [i][j][k]
     *            instance represents the features calculated between word i and 
     *            word j in the DependencyInstance and k represents whether i or
     *            j was the proposed head node.
     *            
     *  @param nt_fvs A four-dimension array of FeatureVectors where each
     *                [i][j][k][l] entry represents a feature vector with
     *                features for word [i], with arc label [j], where [k=0]
     *                means [i] is a head and [k=1] means [i] is a child 
     */
    public void fillFeatureVectors(DependencyInstance instance,
            FeatureVector[][][] fvs, double[][][] probs,
            FeatureVector[][][][] nt_fvs, double[][][][] nt_probs,
            Parameters params)
    {

        final int instanceLength = instance.length();

        for (int w1 = 0; w1 < instanceLength; w1++)
        {
            for (int w2 = w1 + 1; w2 < instanceLength; w2++)
            {
                for (int ph = 0; ph < 2; ph++)
                {
                    boolean attR = ph == 0 ? true : false;

                    int childInt = attR ? w2 : w1;
                    int parInt = attR ? w1 : w2;

                    FeatureVector prodFV = new FeatureVector();

                    this.addLinguisticUnigramFeatures(instance, parInt, childInt, null, prodFV);
                    this.addLinguisticBigramFeatures(instance, parInt, childInt, null, prodFV);
                    this.addVisualBigramFeatures(instance, parInt, childInt, null, prodFV);
                    //this.addLinguisticGrandparentGrandchildFeatures(instance, w1, parInt, childInt, instance.deprels[parInt], prodFV);
                    //this.addLinguisticBigramSiblingFeatures(instance, w1, parInt, childInt, instance.deprels[parInt], prodFV);*/

                    double prodProb = params.getScore(prodFV);
                    fvs[w1][w2][ph] = prodFV;
                    probs[w1][w2][ph] = prodProb;
                }
            }
        }
        
        if (labeled)
        {
            for (int w1 = 0; w1 < instanceLength; w1++)
            {
                // For each word in the input sentence
                for (int t = 0; t < types.length; t++)
                {
                    // For each arc label type
                    String type = types[t];
                    for (int ph = 0; ph < 2; ph++)
                    {
                        // Does this attachment go left or right?
                        boolean attR = ph == 0 ? true : false;
                        for (int ch = 0; ch < 2; ch++)
                        {
                            // Do we include children features?
                            boolean child = ch == 0 ? true : false;

                            FeatureVector prodFV = new FeatureVector();
                            addLabeledFeatures(instance, w1, type, attR, child,
                                    prodFV);

                            double nt_prob = params.getScore(prodFV);
                            nt_fvs[w1][t][ph][ch] = prodFV;
                            nt_probs[w1][t][ph][ch] = nt_prob;

                        }
                    }
                }
            }
        }  
    }
         
    /**
     * Save the features in a gold standard DependencyInstance to disk.
     */
    @Override
    protected void writeInstance(DependencyInstance instance,
            ObjectOutputStream out)
    {
        try
        {
            final int instanceLength = instance.length();

            // Get production crap.

            for (int w1 = 0; w1 < instanceLength; w1++)
            {
                for (int w2 = w1 + 1; w2 < instanceLength; w2++)
                {
                    for (int ph = 0; ph < 2; ph++)
                    {
                        boolean attR = ph == 0 ? true : false;

                        int childInt = attR ? w2 : w1;
                        int parInt = attR ? w1 : w2;

                        FeatureVector prodFV = new FeatureVector();

                        this.addLinguisticUnigramFeatures(instance, parInt, childInt, instance.deprels[parInt], prodFV);
                        this.addLinguisticBigramFeatures(instance, parInt, childInt, instance.deprels[parInt], prodFV);
                        this.addVisualBigramFeatures(instance, parInt, childInt, instance.deprels[parInt], prodFV);
                        //this.addLinguisticGrandparentGrandchildFeatures(instance, w1, w1, w2, instance.deprels[parInt], prodFV);
                        //this.addLinguisticBigramSiblingFeatures(instance, w1, w1, w2, instance.deprels[parInt], prodFV);

                        out.writeObject(prodFV.keys());
                    }
                }
            }
            out.writeInt(-3);
            
            if (labeled)
            {
                for (int w1 = 0; w1 < instanceLength; w1++)
                {
                    for (int t = 0; t < types.length; t++)
                    {
                        String type = types[t];
                        for (int ph = 0; ph < 2; ph++)
                        {
                            boolean attR = ph == 0 ? true : false;
                            for (int ch = 0; ch < 2; ch++)
                            {
                                boolean child = ch == 0 ? true : false;
                                FeatureVector prodFV = new FeatureVector();
                                addLabeledFeatures(instance, w1, type, attR,
                                        child, prodFV);
                                out.writeObject(prodFV.keys());
                            }
                        }
                    }
                }
                out.writeInt(-3);
            }
            
            out.writeObject(instance.fv.keys());
            out.writeInt(-4);

            out.writeObject(instance);
            out.writeInt(-1);

            out.reset();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    
    
    /**
     * Read an instance from an input stream.
     * 
     **/
    public DependencyInstance readInstance(ObjectInputStream in, int length,
            FeatureVector[][][] fvs, double[][][] probs,
            FeatureVector[][][][] nt_fvs, double[][][][] nt_probs,
            Parameters params) throws IOException
    {

        try
        {

            // Get production crap.
            for (int w1 = 0; w1 < length; w1++)
            {
                for (int w2 = w1 + 1; w2 < length; w2++)
                {
                    for (int ph = 0; ph < 2; ph++)
                    {
                        FeatureVector prodFV = new FeatureVector(
                                (int[]) in.readObject());
                        double prodProb = params.getScore(prodFV);
                        fvs[w1][w2][ph] = prodFV;
                        probs[w1][w2][ph] = prodProb;
                    }
                }
            }
            int last = in.readInt();
            if (last != -3)
            {
                System.out.println("Error reading file.");
                System.exit(0);
            }

            if (labeled) 
            { 
                for (int w1 = 0; w1 < length; w1++) 
                { 
                    for (int t = 0; t < types.length; t++) 
                    { 
                        String type = types[t];              
                        for (int ph = 0; ph < 2; ph++) 
                        { 
                            for (int ch = 0; ch < 2; ch++) 
                            {
                                FeatureVector prodFV = new FeatureVector( (int[])in.readObject()); 
                                double nt_prob = params.getScore(prodFV);
                                nt_fvs[w1][t][ph][ch] = prodFV; 
                                nt_probs[w1][t][ph][ch] = nt_prob;
                            } 
                        }
                    } 
                } 
                last = in.readInt(); 
                if (last != -3) 
                {
                    System.out.println("Error reading file."); 
                    System.exit(0); 
                } 
            }
             
            FeatureVector nfv = new FeatureVector((int[]) in.readObject());
            last = in.readInt();
            if (last != -4)
            {
                System.out.println("Error reading file.");
                System.exit(0);
            }

            DependencyInstance marshalledDI;
            marshalledDI = (DependencyInstance) in.readObject();
            marshalledDI.setFeatureVector(nfv);

            last = in.readInt();
            if (last != -1)
            {
                System.out.println("Error reading file.");
                System.exit(0);
            }

            return marshalledDI;

        }
        catch (ClassNotFoundException e)
        {
            System.out.println("Error reading file.");
            System.exit(0);
        }

        // this won't happen, but it takes care of compilation complaints
        return null;
    }

    /**
     * Read the parsed source sentences from disk into memory.
     * 
     * @param sourceFile
     */
    public void readSourceInstances(String sourceFile)
    {
        try
        {
            correspondingReader.startReading(sourceFile);
            DependencyInstance x = correspondingReader.getNext();
            while (x != null)
            {
                sourceInstances.add(x);
                x = correspondingReader.getNext();
            }
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }

    /**
     * Read the alignments from disk into memory.
     * 
     * @param alignmentsFile
     * @throws IOException
     */
    public void readAlignments(String alignmentsFile) throws IOException
    {
        alignments = new LinkedList<List<Alignment>>();

        AlignmentsReader ar = AlignmentsReader
                .getAlignmentsReader(alignmentsFile);

        List<Alignment> thisLine = ar.getNext();

        while (thisLine != null)
        {
            alignments.add(thisLine);
            thisLine = ar.getNext();
        }
    }

    /**
     * Add a set of unigram features for word at position index in a 
     * DependencyInstance, depending on whether the word is a head or a child.
     * 
     * @param instance
     * @param fv
     */
    public void newAddLinguisticUnigramFeatures(DependencyInstance instance,
            int index, boolean isHead, FeatureVector fv)
    {   
        String wordForm = instance.forms[index];
        
        StringBuilder feature;

        if (isHead)
        {
            //1. H=Head
	        feature = new StringBuilder("H=" + wordForm);
	        this.add(feature.toString(), fv);
        }
        else
        {
            //2. A=Arg
            feature = new StringBuilder("A=" + wordForm);
            this.add(feature.toString(), fv);	
        }   
    }    


    /**
     * Adds features that allow for labelled parsing.
     * 
     * TODO: Rewrite this code because it uses position-based features.
     * 
     * @param instance
     * @param wordIndex
     * @param dependencyType
     * @param attR
     * @param childFeatures
     * @param fv
     */
    public void newAddLabeledFeatures(DependencyInstance instance, int wordIndex,
            String dependencyType, boolean attR, boolean childFeatures, FeatureVector fv)
    {    
        /* The original implementation */
        if (!labeled)
        {
            return;
        }
        
        String[] forms = instance.forms;
        String[] pos = instance.postags;

        String att = "";
        if (attR)
        {
            att = "RA";
        }
        else
        {
            att = "LA";
        }

        att += "&" + childFeatures; // attachment direction)
        //att = "";

        String w = forms[wordIndex]; // word
        String wP = pos[wordIndex]; // postag

        String wPm1 = wordIndex > 0 ? pos[wordIndex - 1] : "STR"; // pos of proceeding word
        String wPp1 = wordIndex < pos.length - 1 ? pos[wordIndex + 1] : "END"; // pos of the next word

        add("NTS1=" + dependencyType + "&" + att, fv); // dependency relation label + direction
        add("ANTS1=" + dependencyType, fv); // dependency relation label
        for (int i = 0; i < 2; i++)
        {
            String suff = i < 1 ? "&" + att : ""; // attachment direction
            suff = "&" + dependencyType + suff; // and dependency relation label

            add("NTH=" + w + " " + wP + suff, fv); // word and pos and suff
            add("NTI=" + wP + suff, fv); // pos tag and suff
            add("NTIA=" + wPm1 + " " + wP + suff, fv); // prev pos tag and this pos tag and suff 
            add("NTIB=" + wP + " " + wPp1 + suff, fv); // this pos and prev pos and suff
            add("NTIC=" + wPm1 + " " + wP + " " + wPp1 + suff, fv); // prev pos, this pos, next pos, suff
            add("NTJ=" + w + suff, fv); // word and suff
        }
    }    
    
    /**
     * This is where we calculate the features over an input, which is
     * represented as a DependencyInstance.
     */
    public FeatureVector newCreateFeatureVector(DependencyInstance instance)
    {
        final int instanceLength = instance.length();

        String[] labs = instance.deprels;
        int[] heads = instance.heads;

        FeatureVector fv = new FeatureVector();
        for (int i = 0; i < instanceLength; i++)
        {
            if (heads[i] == -1)
            {
                continue;
            }

            /* Figure out the head and argument indices */
            int headIndex = i < heads[i] ? i : heads[i];
            int argIndex = i > heads[i] ? i : heads[i];
            boolean attR = i < heads[i] ? false : true;
            if (!attR)
            {
                int tmp = headIndex;
                headIndex = argIndex;
                argIndex = tmp;
            }
            
            boolean isHead = headIndex == i? true : false;

            this.newAddLinguisticUnigramFeatures(instance, i, isHead, fv);
            //this.addLinguisticBigramFeatures(instance, i, headIndex, argIndex, labs[i], fv);
            //this.addLinguisticGrandparentGrandchildFeatures(instance, i, headIndex, argIndex, labs[i], fv);
            //this.addLinguisticBigramSiblingFeatures(instance, i, headIndex, argIndex, labs[i], fv);

            /*if (labeled)
            {
                addLabeledFeatures(instance, i, labs[i], attR, true, fv);
                addLabeledFeatures(instance, heads[i], labs[i], attR, false, fv);
            }*/
        }

        return fv;
    }

    /**
     * 
     * TODO: Rewrite all the methods that add features so they don't make naive
     *       assumptions about the data. These fill methods really need to attempt
     *       all possible combinations.
     * 
     * @param fvs A three-dimension array of FeatureVectors where each [i][j][k]
     *            instance represents the features calculated between word i and 
     *            word j in the DependencyInstance and k represents whether i or
     *            j was the proposed head node.
     */
    public void newFillFeatureVectors(DependencyInstance instance,
            FeatureVector[][][] fvs, double[][][] probs,
            FeatureVector[][][][] nt_fvs, double[][][][] nt_probs,
            Parameters params)
    {

        final int instanceLength = instance.length();

        // Get production crap.

        for (int w1 = 0; w1 < instanceLength; w1++)
        {
            for (int w2 = w1 + 1; w2 < instanceLength; w2++)
            {
                for (int ph = 0; ph < 2; ph++)
                {
                    boolean attR = ph == 0 ? true : false;

                    int childInt = attR ? w2 : w1;
                    int parInt = attR ? w1 : w2;

                    FeatureVector prodFV = new FeatureVector();

                    this.newAddLinguisticUnigramFeatures(instance, w1, parInt == w1, prodFV);
                    //this.addLinguisticBigramFeatures(instance, w1, parInt, childInt, instance.deprels[parInt], prodFV);
                    //this.addLinguisticGrandparentGrandchildFeatures(instance, w1, parInt, childInt, instance.deprels[parInt], prodFV);
                    //this.addLinguisticBigramSiblingFeatures(instance, w1, parInt, childInt, instance.deprels[parInt], prodFV);*/

                    double prodProb = params.getScore(prodFV);
                    fvs[w1][w2][ph] = prodFV;
                    probs[w1][w2][ph] = prodProb;
                }
            }
        }
        
        if (labeled)
        {
            for (int w1 = 0; w1 < instanceLength; w1++)
            {
                for (int t = 0; t < types.length; t++)
                {
                    String type = types[t];
                    for (int ph = 0; ph < 2; ph++)
                    {

                        boolean attR = ph == 0 ? true : false;
                        for (int ch = 0; ch < 2; ch++)
                        {

                            boolean child = ch == 0 ? true : false;

                            FeatureVector prodFV = new FeatureVector();
                            addLabeledFeatures(instance, w1, type, attR, child,
                                    prodFV);

                            double nt_prob = params.getScore(prodFV);
                            nt_fvs[w1][t][ph][ch] = prodFV;
                            nt_probs[w1][t][ph][ch] = nt_prob;

                        }
                    }
                }
            }
        }  
    }
    protected void newWriteInstance(DependencyInstance instance,
            ObjectOutputStream out)
    {
        try
        {
            final int instanceLength = instance.length();

            // Get production crap.

            for (int w1 = 0; w1 < instanceLength; w1++)
            {
                for (int w2 = w1 + 1; w2 < instanceLength; w2++)
                {
                    for (int ph = 0; ph < 2; ph++)
                    {
                        boolean attR = ph == 0 ? true : false;

                        int childInt = attR ? w2 : w1;
                        int parInt = attR ? w1 : w2;

                        FeatureVector prodFV = new FeatureVector();

                        this.newAddLinguisticUnigramFeatures(instance, w1, w1 == parInt, prodFV);
                        //this.addLinguisticBigramFeatures(instance, w1, w1, w2, instance.deprels[parInt], prodFV);
                        //this.addLinguisticGrandparentGrandchildFeatures(instance, w1, w1, w2, instance.deprels[parInt], prodFV);
                        //this.addLinguisticBigramSiblingFeatures(instance, w1, w1, w2, instance.deprels[parInt], prodFV);

                        out.writeObject(prodFV.keys());
                    }
                }
            }
            out.writeInt(-3);
            
            if (labeled)
            {
                for (int w1 = 0; w1 < instanceLength; w1++)
                {
                    for (int t = 0; t < types.length; t++)
                    {
                        String type = types[t];
                        for (int ph = 0; ph < 2; ph++)
                        {
                            boolean attR = ph == 0 ? true : false;
                            for (int ch = 0; ch < 2; ch++)
                            {
                                boolean child = ch == 0 ? true : false;
                                FeatureVector prodFV = new FeatureVector();
                                addLabeledFeatures(instance, w1, type, attR,
                                        child, prodFV);
                                out.writeObject(prodFV.keys());
                            }
                        }
                    }
                }
                out.writeInt(-3);
            }
            
            out.writeObject(instance.fv.keys());
            out.writeInt(-4);

            out.writeObject(instance);
            out.writeInt(-1);

            out.reset();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }        
    
}
