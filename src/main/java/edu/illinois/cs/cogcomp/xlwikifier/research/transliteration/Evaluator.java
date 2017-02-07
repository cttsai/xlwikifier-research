package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.transliteration.Example;
import edu.illinois.cs.cogcomp.transliteration.SPModel;
import edu.illinois.cs.cogcomp.utils.Utils;
import org.h2.store.fs.FileUtils;

import java.io.FileNotFoundException;
import java.util.*;
import java.io.*;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Created by ctsai12 on 11/13/16.
 */
public class Evaluator {

	public static Map<String, List<String>> readSequiturOutput(String predfile){
        Map<String, List<String>> preds = new HashMap<>();

        try {
            for(String line: LineIO.read(predfile)){

                String[] parts = line.split("\t");
                if(parts.length<4) {
//                    System.out.println(line);
                    continue;
                }
                String token = parts[0];
                String trans = parts[3].replaceAll("\\s+", "").trim();

                if(!preds.containsKey(token))
                    preds.put(token, new ArrayList<>());
                preds.get(token).add(trans);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
		return preds;
	}

    public static Map<String, List<String>> readDirecTLOutput(String predfile){
        Map<String, List<String>> preds = new HashMap<>();

        try {
            for(String line: LineIO.read(predfile)){

                String[] parts = line.split("\t");
                if(parts.length<2) {
                    continue;
                }
                String token = parts[0];
                String trans = parts[1].replaceAll("\\s+", "").trim();

                if(!preds.containsKey(token))
                    preds.put(token, new ArrayList<>());
                preds.get(token).add(trans);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return preds;
    }
    public static Map<String, List<String>> readJanusOutput(String predfile, String tokenfile){
        Map<String, List<String>> preds = new HashMap<>();

        ArrayList<String> predlines = null;
        ArrayList<String> tokenlines = null;
        try {
            predlines = LineIO.read(predfile);
            tokenlines = LineIO.read(tokenfile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (predlines.size() != tokenlines.size()){
            System.out.println("Line # don't match "+predlines.size()+" "+tokenlines.size());
            System.exit(-1);
        }
        for(int i = 0; i < predlines.size(); i++){
            String pred = predlines.get(i);
            String token = tokenlines.get(i);
            String[] parts = pred.split("\\|\\|\\|");
            if(parts.length < 2) continue;

            token = token.replaceAll("\\s+", "");
            String trans = parts[1].replaceAll("\\s+", "");

            if (!preds.containsKey(token))
                preds.put(token, new ArrayList<>());
            preds.get(token).add(trans);

        }
        return preds;

    }

    /**
     * Read word translation from a file, and evaluate it
     * @param testfile
     * @param predfile
     */
    public static Pair<Double, Integer> evalSequitur(String testfile, String predfile){

        System.out.println("Evaluating Sequitur prediction: "+predfile);
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile);

		Map<String, List<String>> preds = readSequiturOutput(predfile);

        return evalModelPred(test_pairs, preds, predfile+".test_scores");
    }

    public static Pair<Double, Integer> evalDirecTL(String testfile, String predfile){

        System.out.println("Evaluating DirecTL prediction: "+predfile);
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile);

        Map<String, List<String>> preds = readDirecTLOutput(predfile);

        return evalModelPred(test_pairs, preds, predfile+".test_scores");
    }

    public static Pair<Double, Integer> evalJanus(String testfile, String tokenfile, String predfile) {

        System.out.println("Evaluating JANUS prediction: " + predfile);
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile);

        Map<String, List<String>> preds = readJanusOutput(predfile, tokenfile);

        return evalModelPred(test_pairs, preds, predfile+".test_scores");
    }

    public static Pair<Double, Integer> evalPhrasePred(String testfile, String predfile) {

        ArrayList<String> testlines = null;
        ArrayList<String> predlines = null;
        try {
            testlines = LineIO.read(testfile);
            predlines = LineIO.read(predfile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (testlines.size() != predlines.size()){
            System.out.println("# lines don't match");
            System.exit(-1);
        }

        double totalf1 = 0;

        for(int i = 0; i < testlines.size(); i++){

            String gold = testlines.get(i);
            String pred = predlines.get(i);

            List<String> refs = Arrays.asList(gold);
            totalf1 += Utils.GetFuzzyF1(pred, refs);

        }
        double f1 = totalf1 / (double)predlines.size();
        System.out.println("AVGF1 =" + f1);

        return new Pair<>(totalf1, predlines.size());
    }

    /**
     * Evaluate the output from other transliterators, e.g., sequiter
     * @param pairs
     * @param predictions
     */
    public static Pair<Double, Integer> evalModelPred(List<Pair<String[], String[]>> pairs, Map<String, List<String>> predictions, String scorefile){


        List<Double> test_scores = new ArrayList<>();
        double correctmrr = 0;
        double correctacc = 0;
        double totalf1 = 0;

        LanguageModel lm = new LanguageModel();
        lm.loadDB(true);

        int cnt = 0;
        for(Pair<String[], String[]> pair: pairs){
            if(cnt++%10 == 0) System.out.print(cnt+" "+pairs.size()+"\r");

            List<List<String>> preds = generatePhrase(pair.getFirst(), predictions);

            int bestindex = -1;

            String gold = null;
            if(preds.size()>0) {

                Map<List<String>, Double> phrase2score = new HashMap<>();
                for(List<String> pred: preds){

                    for(List<String> p: TransUtils.perm(pred, pred.size())){
                        double prob = lm.getPhraseProb(p);
                        if(p.equals(pred))
                            prob += 1e-9;
                        phrase2score.put(p, prob);
                    }
                    break;
                }

                List<List<String>> sorted = phrase2score.entrySet().stream().sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
                        .map(x -> x.getKey()).collect(toList());

                String pred_phrase = sorted.get(0).stream().collect(joining(" "));
                gold = Arrays.asList(pair.getSecond()).stream().collect(joining(" "));
                List<String> refs = Arrays.asList(gold);
                double f1 = Utils.GetFuzzyF1(pred_phrase, refs);
                totalf1 += f1;
                test_scores.add(f1);
            }
            else
                test_scores.add(0.0);

//            int index = preds.indexOf(gold);
//            if(bestindex == -1 || index < bestindex){
//                bestindex = index;
//            }
//
//            if (bestindex >= 0) {
//                correctmrr += 1.0 / (bestindex + 1);
//                if(bestindex == 0){
//                    correctacc += 1.0;
//                }
//            }
        }
//        double mrr = correctmrr / (double)pairs.size();
//        double acc = correctacc / (double)pairs.size();
        double f1 = totalf1 / (double)pairs.size();

//        System.out.println("AVGMRR=" + mrr);
//        System.out.println("AVGACC=" + acc);
        System.out.printf("AVGF1 = %.2f\n", f1*100);

        try {
            org.apache.commons.io.FileUtils.writeStringToFile(new File(scorefile),
                    test_scores.stream().map(x -> x.toString()).collect(joining("\n")), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Pair<Double, Integer>(f1, pairs.size());

    }

    public static List<List<String>> generatePhrase(String[] parts, Map<String, List<String>> predictions){

        List<String> preds = new ArrayList<>();
        List<List<String>> predsl = new ArrayList<>();


        for(String part: parts) {
            try {
                List<Pair<Double, String>> prediction = new ArrayList<>();

                if(predictions.containsKey(part)){
                    for(String p: predictions.get(part))
                        prediction.add(new Pair<>(1.0, p));
                }

                for(int i = 0; i < prediction.size(); i++){
                    if(predsl.size() > i)
                        predsl.get(i).add(prediction.get(i).getSecond());
                    else {
                        List<String> tmp = new ArrayList<>();
                        tmp.add(prediction.get(i).getSecond());
                        predsl.add(tmp);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

//        predsl = predsl.stream().filter(x -> x.size() == parts.length).collect(toList());

        // no reorder
        preds = predsl.stream().map(x -> x.stream().collect(joining(" "))).collect(toList());

        return predsl;

    }


    public static Pair<Double, Integer> trainAndTestJeff(String trainfile, String devfile, String testfile, String modelpath){

        TitleTranslator tt = new TitleTranslator();
        tt.eval_align = false;
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile);
        List<Pair<String[], String[]>> dev_pairs = TransUtils.readPairs(devfile);

        List<Pair<String[], String[]>> pairs = TransUtils.readPairs(trainfile);
        List<Example> training = new ArrayList<>();
        for(Pair<String[], String[]> pair: pairs) {
            Example exp = new Example(pair.getFirst()[0], pair.getSecond()[0]);
            training.add(exp);
        }
	
        double max_f1 = 0;
        double best_test = 0;
        int best_iter = 0;
        for(int iter = 1; iter <=5; iter++) {
            SPModel model = new SPModel(training);
            model.Train(iter);
			try{
				model.WriteProbs(modelpath+"-iter"+iter);
			}catch(IOException e){
            	e.printStackTrace();
			}
			JointModel jm = new JointModel();
			jm.spmodel = model;
			tt.current_model = jm;
            double df1 = tt.evalModel(dev_pairs, model, null);
            double tf1 = tt.evalModel(test_pairs, model, null);
            if(df1 > max_f1){
                max_f1 = df1;
                best_test = tf1;
                best_iter = iter;
            }
        }

        System.out.printf("Test F1 at best dev iter: %.2f\n", best_test*100);

        SPModel model = new SPModel(training);
        model.Train(best_iter);
        try{
            model.WriteProbs(modelpath+".best");
        }catch(IOException e){
            e.printStackTrace();
        }
        JointModel jm = new JointModel();
        jm.spmodel = model;
        tt.current_model = jm;
        double tf1 = tt.evalModel(test_pairs, model, null);
        tt.printTestScores(modelpath+".test_scores");
        System.out.println("=============================");
        System.out.printf("Best test F1: %.2f\n", tf1*100);
        System.out.println("=============================");

        return new Pair(tf1, test_pairs.size());
    }

    public static void main(String[] args) {

        String system = args[0];
//        String lang = args[1];
//        String type = args[2];
        List<String> types = Arrays.asList("loc", "org", "per");
//        List<String> langs = Arrays.asList(args[1]);
        List<String> langs = Arrays.asList("tl");
//        List<String> types = Arrays.asList("loc");

        for(String lang: langs) {
            System.out.println("========== "+lang+" ============");
            double f1_sum = 0;
            double n_test = 0;
            String dir = "/shared/corpora/ner/transliteration/" + lang + "/";
            for (String type : types) {
                String testfile = dir + type + "/test.select";

                if (system.equals("seq")) {
                    String predfile = dir + type + "/naive-align/test.tokens.seq";
                    if (!FileUtils.exists(predfile)) {
                        System.out.println("skipping " + predfile);
                        continue;
                    }
                    Pair<Double, Integer> results = evalSequitur(testfile, predfile);
                    f1_sum += results.getFirst() * results.getSecond();
                    n_test += results.getSecond();
                } else if (system.equals("dir")) {
                        String predfile = dir + type + "/naive-align/test.tokens.dir";
                        if (!FileUtils.exists(predfile)) {
                            System.out.println("skipping " + predfile);
                            continue;
                        }
                        Pair<Double, Integer> results = evalDirecTL(testfile, predfile);
                        f1_sum += results.getFirst()*results.getSecond();
                        n_test += results.getSecond();
                } else if (system.equals("janus")) {
//                    String predfile = "/shared/experiments/ctsai12/workspace/Agtarbidir/"+lang+".500.100/src.res.agm." + type;
                    String predfile = dir+"/janus-results/naive/src.res.agm."+type;

                    if (!FileUtils.exists(predfile)) {
                        System.out.println("skipping " + predfile);
                        continue;
                    }
                    String tokenfile = dir + type + "/janus/test.tokens." + lang;
                    Pair<Double, Integer> results = evalJanus(testfile, tokenfile, predfile);
                    System.out.println(results.getSecond());
                    f1_sum += results.getFirst()*results.getSecond();
                    n_test += results.getSecond();
                } else if (system.equals("nmt")) {
//                String predfile = "/scratch/ctsai12/transliteration/"+lang+"/"+type+"/"+lang+"en/pred";
//                String goldfile = "/scratch/ctsai12/transliteration/"+lang+"/"+type+"/"+lang+"en/test/all_"+lang+"-en.en";
//                evalPhrasePred(goldfile, predfile);
                } else if (system.equals("jeff")) {
                    String trainfile = dir + type + "/naive-align/train.4";
//                    String trainfile = dir + type + "/fast-align/train";
                    String devfile = dir + type + "/dev.select";

					try{
						File f = new File(dir+type+"/models");
						if(!f.isDirectory())
							f.mkdir();
					}catch(Exception e){
						e.printStackTrace();
					}

					String modelpath = dir+type+"/models/jeff.palign.new1";

                    Pair<Double, Integer> results = trainAndTestJeff(trainfile, devfile, testfile, modelpath);
                    f1_sum += results.getFirst() * results.getSecond();
                    n_test += results.getSecond();
                }
            }

            System.out.printf("%.2f\n", f1_sum*100/n_test);
        }
    }
}
