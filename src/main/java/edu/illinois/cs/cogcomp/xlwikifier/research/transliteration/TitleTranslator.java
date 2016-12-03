package edu.illinois.cs.cogcomp.xlwikifier.research.transliteration;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.transliteration.SPModel;
import edu.illinois.cs.cogcomp.utils.Utils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Created by ctsai12 on 9/3/16.
 */
public class TitleTranslator {


    private Map<String,Map<String, Double>> s2t2prob = new HashMap<>();
    private Map<String,Map<String, Double>> t2s2prob = new HashMap<>();

    private Map<String, Map<String, Double>> ilm2j2prob = new HashMap<>();
    private Map<String, Map<String, Double>> e2f2prob = new HashMap<>();

    private Map<String, Map<String, Double>> lm2a2prob = new HashMap<>();
    private Map<String, Map<String, Double>> lm2a2c = new HashMap<>();
    private Map<String, Double> lm2c = new HashMap<>();

    private List<Map<String, List<Pair<String, String>>>> a2pairs = new ArrayList<>();


    private Map<String, Map<String, Double>> en2fo2prob = new HashMap<>();
    private Map<String, Map<String, Double>> ilm2j2c = new HashMap<>();
    private Map<String, Double> ilm2c = new HashMap<>();
    private Map<String, Double> e2c = new HashMap<>();
    private Map<String, Map<String, Double>> e2f2c = new HashMap<>();


    private Map<String, Double> memorization;
    private Map<String, Pair<Map<String, Map<String, Double>>, Double>> memorization_update;

    private Map<String, String> word_align = new HashMap<>();
    private Map<String, String> phrase_align = new HashMap<>();
    private Map<String, Map<String, Double>> src2align = new HashMap<>();

    private List<Pair<List<String>, List<String>>> segmap = new ArrayList<>();

    private Map<Pair<List<String>, List<String>>, Integer> segmapcnt = new HashMap<>();

    private Map<String, Map<String, Double>> newprob;
    private Map<String, Map<String, Double>> newprob1;

    private Map<String, Pair<String, Double>> bestscore = new HashMap<>();

    private Map<String, List<List<String>>> segcache = new HashMap<>();

    private Map<Pair<String, String>, Double> genprobcache = new HashMap<>();

    private Map<String, Double> wcnt;

    private List<String> freq;

    private Map<Integer, List<List<Integer>>> n2perms;

    private boolean eval_align = true;

    private Map<String, SPModel> type2model;



    private LanguageModel lm;

    public TitleTranslator(){

        lm = new LanguageModel();

        n2perms = new HashMap<>();
        for(int i = 1; i < 6; i++){
            List<Integer> input = new ArrayList<>();
            for(int j = 0; j < i; j++)
                input.add(j);

            n2perms.put(i, perm(input));
        }
    }

    public void loadBaselineModels(String lang){
        type2model = new HashMap<>();
        List<String> types = Arrays.asList("loc", "org", "per");
        for(String type: types) {
            String modelfile = "/shared/corpora/ner/gazetteers/" + lang + "/model/" + type + ".naive";
            try {
                type2model.put(type, new SPModel(modelfile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadJointModels(String lang){
        type2model = new HashMap<>();
        List<String> types = Arrays.asList("loc", "org", "per");
        for(String type: types) {
            String modelfile = "/shared/corpora/ner/gazetteers/" + lang + "/model/" + type + ".joint";
            try {
                type2model.put(type, new SPModel(modelfile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void normalizeProbTGivenS(){
//        System.out.println("Normalizing Pr(T|S)...");
        for(String s: s2t2prob.keySet()){
            Map<String, Double> t2prob = s2t2prob.get(s);
            double sum = 0;
            for(String t: t2prob.keySet()){
                sum+=t2prob.get(t);
            }
            for(String t: t2prob.keySet()){
                if(sum<0.00000000001)
                    t2prob.put(t, 0.0);
                else
                    t2prob.put(t, t2prob.get(t)/sum);
            }
        }
    }

//    private String getAlignmentKey(List<String> seg1, List<String> seg2){
//        return seg1.stream().collect(joining("-"))+"-"+seg2.stream().collect(joining("-"));
//    }
//
//    public class SourceWorker implements Runnable {
//
//        private String source;
//        private List<Pair<String, String>> pairs;
//        private Map<String, Double> align2prob = new HashMap<>();
//        private List<List<String>> src_segs;
//
//        public SourceWorker(List<Pair<String, String>> pairs, String src) {
//            this.pairs = pairs;
//            this.source = src.toLowerCase();
//            src_segs = getAllSegment(source);
//        }
//
//        @Override
//        public void run() {
//            for (Pair<String, String> pair : pairs) {
//                String target = pair.getSecond().toLowerCase();
//                List<List<String>> tgt_segs = getAllSegment(target);
//
//                for (List<String> ss : src_segs) {
//                    for (List<String> ts : tgt_segs) {
//                        if (ss.size() == ts.size()) {
//
//                            double ptgivens = 1.0;
//                            for (int i = 0; i < ss.size(); i++) {
//                                String s = ss.get(i);
//                                String t = ts.get(i);
//                                ptgivens *= s2t2prob.get(s).get(t);
//                            }
////                            String akey = ss.stream().collect(joining("-"));
//                            String akey = getAlignmentKey(ss, ts);
//
//                            if (!align2prob.containsKey(akey)) align2prob.put(akey, 0.0);
//                            align2prob.put(akey, align2prob.get(akey) + ptgivens);
//                        }
//                    }
//                }
//            }
//            double sum = 0;
//            for(String align: align2prob.keySet())
//                sum += align2prob.get(align);
//
//            for(String align: align2prob.keySet())
//                align2prob.put(align, align2prob.get(align)/sum);
//
//            synchronized (src2align){
//                src2align.put(source, align2prob);
//            }
//        }
//    }
//
//    private void computeAlignProb(List<Pair<String, String>> pairs){
//        System.out.println("Computing Pr(A|S)...");
//
//        ExecutorService executor = Executors.newFixedThreadPool(30);
//        Map<String, List<Pair<String, String>>> src2pairs = pairs.stream().collect(groupingBy(x -> x.getFirst()));
//        for(String source: src2pairs.keySet()){
//            executor.execute(new SourceWorker(src2pairs.get(source), source));
//        }
//        executor.shutdown();
//        try {
//            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//    }

    public int initProdProb(String source, String target){

        if(source.isEmpty() && target.isEmpty())
            return 1;

        if(source.isEmpty() || target.isEmpty())
            return 0;

        int total = 0;
        for(int i = 1; i <= source.length(); i++){
            String src_head = source.substring(0, i);
            String src_tail = source.substring(i, source.length());
            for(int j = 1; j <=target.length(); j++){
                String tgt_head = target.substring(0, j);
                String tgt_tail = target.substring(j, target.length());

                int cnt = initProdProb(src_tail, tgt_tail);
                if(cnt > 0) {
                    TransUtils.addToMap(src_head, tgt_head, (double) cnt, s2t2prob);
                    total += cnt;
                }
            }
        }

        return total;
    }

    public double getProdProb(String source, String target){

        if(source.isEmpty() && target.isEmpty())
            return 1;

        if(source.isEmpty() || target.isEmpty())
            return 0;

        String key = source+"|"+target;
        if(memorization.containsKey(key))
            return memorization.get(key);


        double probsum = 0;
        for(int i = 1; i <= source.length(); i++){
            String src_head = source.substring(0, i);
            String src_tail = source.substring(i, source.length());
            for(int j = 1; j <=target.length(); j++){
                String tgt_head = target.substring(0, j);
                String tgt_tail = target.substring(j, target.length());

                double prob = getProdProb(src_tail, tgt_tail);
//                if(prob == 0) continue;

                if(!s2t2prob.containsKey(src_head) || !s2t2prob.get(src_head).containsKey(tgt_head))
                    continue;
//                    return 0;
                probsum += s2t2prob.get(src_head).get(tgt_head)*prob;
            }
        }

        memorization.put(key, probsum);
        return probsum;

    }

    public double updateProdProb(String source, String target, Map<String, Map<String, Double>> prodprob){

        String key = source+"|"+target;
        if(memorization_update.containsKey(key)){

            Pair<Map<String, Map<String, Double>>, Double> tmp = memorization_update.get(key);
            prodprob = copyMap(tmp.getFirst());
            return tmp.getSecond();
        }

        if(source.isEmpty() && target.isEmpty())
            return 1;

        if(source.isEmpty() || target.isEmpty())
            return 0;

        double sum = 0;
        for(int i = 1; i <= source.length(); i++){
            String src_head = source.substring(0, i);
            String src_tail = source.substring(i, source.length());
            for(int j = 1; j <=target.length(); j++){
                String tgt_head = target.substring(0, j);
                String tgt_tail = target.substring(j, target.length());

                double tail_sum = updateProdProb(src_tail, tgt_tail, prodprob);
                if(tail_sum == 0) continue;

                double p = s2t2prob.get(src_head).get(tgt_head);

                // update the production probability in the tail
                for(String s: prodprob.keySet()){
                    for(String t: prodprob.get(s).keySet()){
                        prodprob.get(s).put(t, prodprob.get(s).get(t)*p);
                    }
                }

                TransUtils.addToMap(src_head, tgt_head, tail_sum*p, prodprob);
                sum += tail_sum*p;
            }
        }

        memorization_update.put(key, new Pair<>(copyMap(prodprob), sum));

        return sum;

    }

    public Map<String, Map<String, Double>> copyMap(Map<String, Map<String, Double>> map){
        Map<String, Map<String, Double>> tmp = new HashMap<>();
        for(String s: map.keySet()){
            for(String t: map.get(s).keySet()){
                tmp.put(s, new HashMap<>());
                tmp.get(s).put(t, map.get(s).get(t));
            }
        }
        return tmp;
    }

    public class PairWorker implements Runnable {

        private Pair<String, String> pair;
        private double wordprob;

        public PairWorker(Pair<String, String> pair, double wordprob) {
            this.pair = pair;
            this.wordprob = wordprob;
        }

        @Override
        public void run() {
            String source = pair.getFirst().toLowerCase();
            String target = pair.getSecond().toLowerCase();
            List<List<String>> src_segs = getAllSegment(source);
            List<List<String>> tgt_segs = getAllSegment(target);

            double aprob_sum = 0;
//            double aprob_sum1 = 0;

            Map<String, Map<String, Double>> tmp_prob = new HashMap<>();
//            Map<String, Map<String, Double>> tmp_prob1 = new HashMap<>();

            for(List<String> ss: src_segs) {
                for (List<String> ts : tgt_segs) {
                    if (ss.size() == ts.size()) {

                        double aprob = 1.0;
//                        double aprob = 2.0/(wcnt.get(source)*Math.pow(0.5,ss.size()));
//                        double aprob = Math.pow(0.9, Math.sqrt(wcnt.get(source)));
//                        double aprob1 = 1.0;
                        for (int i = 0; i < ss.size(); i++) {
                            String s = ss.get(i);
                            String t = ts.get(i);

                            aprob *= s2t2prob.get(s).get(t);
//                            aprob1 *= t2s2prob.get(t).get(s);
                        }
                        aprob_sum += aprob;
//                        aprob_sum1 += aprob1;

                        for (int i = 0; i < ss.size(); i++) {
                            String s = ss.get(i);
                            String t = ts.get(i);
                            TransUtils.addToMap(s, t, aprob, tmp_prob);
//                            TransUtils.addToMap(t,s, aprob1, tmp_prob1);
                        }
                    }
                }
            }

            if(aprob_sum != 0){
                synchronized (newprob) {
                    for (String s : tmp_prob.keySet()) {
                        for (String t : tmp_prob.get(s).keySet()) {
                            if(!tmp_prob.get(s).get(t).isNaN() && !Double.isNaN(aprob_sum))
                                TransUtils.addToMap(s, t, tmp_prob.get(s).get(t)/aprob_sum*wordprob, newprob);
                            else {
                                System.out.println(tmp_prob.get(s).get(t) + " " + aprob_sum);
                                System.exit(-1);
                            }
                        }
                    }
                }
            }

//            if(aprob_sum1 != 0){
//                synchronized (newprob1) {
//                    for (String t : tmp_prob1.keySet()) {
//                        for (String s : tmp_prob1.get(t).keySet()) {
//                            if(!tmp_prob1.get(t).get(s).isNaN() && !Double.isNaN(aprob_sum1))
//                                TransUtils.addToMap(t, s, tmp_prob1.get(t).get(s)/aprob_sum1*wordprob, newprob1);
//                            else {
//                                System.out.println(tmp_prob1.get(t).get(s) + " " + aprob_sum1);
//                                System.exit(-1);
//                            }
//                        }
//                    }
//                }
//            }
        }
    }

    private void updateProb(List<Pair<String, String>> pairs, List<Double> wordprobs){

//        System.out.println("Updating Pr(T|S)");
        ExecutorService executor = Executors.newFixedThreadPool(20);
        newprob = new HashMap<>();
        newprob1 = new HashMap<>();
//        memorization_update = new HashMap<>();
        int cnt = 0;
        for(int i = 0; i < pairs.size(); i++){
//            if(cnt++%100 == 0)
//                System.out.print(cnt+"\r");
            executor.execute(new PairWorker(pairs.get(i), wordprobs.get(i)));
//            Map<String, Map<String, Double>> tmp = new HashMap<>();
//            double sum = updateProdProb(pairs.get(i).getFirst(), pairs.get(i).getSecond(), tmp);
//            for(String s: tmp.keySet()){
//                for(String t: tmp.get(s).keySet()){
//                    TransUtils.addToMap(s, t, tmp.get(s).get(t)/sum*wordprobs.get(i), newprob);
//                }
//            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        for(String s: s2t2prob.keySet()){
            for(String t: s2t2prob.get(s).keySet()){
                if(newprob.containsKey(s) && newprob.get(s).containsKey(t))
                    s2t2prob.get(s).put(t, newprob.get(s).get(t));
            }
        }

        for(String t: t2s2prob.keySet()){
            for(String s: t2s2prob.get(t).keySet()){
                if(newprob1.containsKey(t) && newprob1.get(t).containsKey(s))
                    t2s2prob.get(t).put(s, newprob1.get(t).get(s));
            }
        }
        normalizeProbTGivenS();
        TransUtils.normalizeProb(t2s2prob);
//        s2t2prob = newprob;
    }


    private void initJointProb(List<Pair<String[], String[]>> pairs){

        System.out.println("Initialing probabilities...");
        int cnt = 0;
        for(Pair<String[], String[]> pair: pairs){
            if(cnt++%100 == 0)
                System.out.print(cnt+"\r");
//            System.out.println(pair.getFirst());
            for(String source: pair.getFirst()){
                for(String target: pair.getSecond()){
                    List<List<String>> src_segs = getAllSegment(source.toLowerCase());
                    List<List<String>> tgt_segs = getAllSegment(target.toLowerCase());

                    for(List<String> ss: src_segs){
                        for(List<String> ts: tgt_segs){
                            if(ss.size() == ts.size()){

                                for(int i = 0; i < ss.size(); i++){
                                    String s = ss.get(i);
                                    String t = ts.get(i);
                                    TransUtils.addToMap(s, t, 1.0, s2t2prob);
                                }
                            }
                        }
                    }
                }
            }
        }

        normalizeProbTGivenS();

        System.out.println("initializing alignment probibilities");
        for(Pair<String[], String[]> pair: pairs){
            int l = pair.getSecond().length;
            int m = pair.getFirst().length;
            for(int i = 0; i < pair.getSecond().length; i++){
                String key1 = i+"_"+l+"_"+m;
//                addToMap(key1, "null", 1.0, ilm2j2prob);  // no null first
                for(int j = 0; j < pair.getFirst().length; j++) {
                    TransUtils.addToMap(key1, String.valueOf(j), 1.0, ilm2j2prob);
                    TransUtils.addToMap(pair.getSecond()[i], pair.getFirst()[j], 1.0, e2f2prob);
                }
            }
        }

        TransUtils.normalizeProb(ilm2j2prob);
        TransUtils.normalizeProb(e2f2prob);

    }

    private void initProb(List<Pair<String, String>> pairs){
        System.out.println("Initialing probabilities...");
        int cnt = 0;
        for(Pair<String, String> pair: pairs){
            if(cnt++%100 == 0)
                System.out.print(cnt+"\r");
//            System.out.println(pair.getFirst());
            String source = pair.getFirst().toLowerCase();
            String target = pair.getSecond().toLowerCase();
//            System.out.println(source+" "+target);
            List<List<String>> src_segs = getAllSegment(source);
            List<List<String>> tgt_segs = getAllSegment(target);

            for(List<String> ss: src_segs){
                for(List<String> ts: tgt_segs){
                    if(ss.size() == ts.size()){

                        for(int i = 0; i < ss.size(); i++){
                            String s = ss.get(i);
                            String t = ts.get(i);
                            TransUtils.addToMap(s, t, 1.0, s2t2prob);
                            TransUtils.addToMap(t, s, 1.0, t2s2prob);
                        }
                    }
                }
            }
        }

        System.out.println("prob size "+s2t2prob.size());
//        System.out.println("# segment mappings: "+segmapcnt.size());

    }

    private void printProbs(){
        System.out.println("========= Dumping probs =========");
        for(String s: s2t2prob.keySet()){
            for(String t: s2t2prob.get(s).keySet()){
                System.out.println(s+"\t"+t+"\t"+s2t2prob.get(s).get(t));
            }
        }
    }

    public void loadProbs(String file){
        System.out.println("Loading model..."+file);

        s2t2prob = new HashMap<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = br.readLine();
            while(line != null){

                String[] parts = line.trim().split("\t");

                if(!s2t2prob.containsKey(parts[0])) s2t2prob.put(parts[0], new HashMap<>());

                double prob = Double.parseDouble(parts[2]);
//                if(prob > 0)
                    s2t2prob.get(parts[0]).put(parts[1], prob*0.5);

                line = br.readLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Pair<Double, String>> generate(String str){

        List<List<String>> segs = getAllSegment(str);
        Map<String, Double> trans2score = new HashMap<>();

        for(List<String> seg: segs){

            long hastrans = seg.stream().filter(x -> s2t2prob.containsKey(x)).count();
            if(hastrans != seg.size())
                continue;

            Map<String, Double> t2score = new HashMap<>();
            for(String s: seg){
                Map<String, Double> tmp = new HashMap<>();
                for(String t: s2t2prob.get(s).keySet()){
                    if(t2score.size() == 0)
                        tmp.put(t, s2t2prob.get(s).get(t));
                    else {
                        for (String ct : t2score.keySet()) {
                            tmp.put(ct+t, t2score.get(ct)*s2t2prob.get(s).get(t));
                        }
                    }
                }
                t2score = tmp;
            }

            for(String t: t2score.keySet()){
                if(trans2score.containsKey(t))
                    trans2score.put(t, trans2score.get(t)+t2score.get(t));
                else
                    trans2score.put(t, t2score.get(t));
            }
        }

        List<Map.Entry<String, Double>> sorted = trans2score.entrySet().stream()
//                .sorted((x1, x2) -> Integer.compare(x1.getKey().length(), x2.getKey().length()))
                .sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
//                .map(x -> x.getKey())
                .collect(toList());

        List<Pair<Double, String>> ret = new ArrayList<>();
        for(int i = 0; i < sorted.size(); i++)
            ret.add(new Pair<>(sorted.get(i).getValue(), sorted.get(i).getKey()));
        return ret;
    }

//    public void testGenerate(String testfile, String modelfile){
//
//        loadProbs(modelfile);
//
//        List<Pair<String, String>> test_pairs = readPairs(testfile, -1);
//
//        int cnt = 0;
//        double totalf1 = 0;
//        for(Pair<String, String> pair: test_pairs){
//            if(cnt++%100 == 0) System.out.print(cnt+"\r");
//
//            List<Map.Entry<String, Double>> trans = generate(pair.getFirst());
//
//            System.out.println();
//            System.out.println(pair.getFirst()+" "+pair.getSecond());
//            trans.forEach(x -> System.out.println("\t"+x));
//            System.out.println();
//
//            List<String> refs = new ArrayList<>();
//            refs.add(pair.getSecond());
//            if(trans.size()>0) {
//                double f1 = Utils.GetFuzzyF1(trans.get(0).getKey(), refs);
////            System.out.println(pair.getFirst()+" -> "+trans.get(0)+" "+f1);
//
//                totalf1 += f1;
//            }
//
//        }
//
//        System.out.println("F1 "+totalf1/test_pairs.size());
//
//    }

    private void writeAlignProbs(String path){
        System.out.println("Writing align probs...");

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(path+".align"));
//            for(String ilm: ilm2j2prob.keySet()){
//                for(String j: ilm2j2prob.get(ilm).keySet()){
//                    bw.write(ilm+"\t"+j+"\t"+ilm2j2prob.get(ilm).get(j)+"\n");
//                }
//            }

            for(String lm: lm2a2prob.keySet()){
                for(String a: lm2a2prob.get(lm).keySet())
                    bw.write(lm+"\t"+a+"\t"+lm2a2prob.get(lm).get(a)+"\n");
            }
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void writeProbs(String path){
        System.out.println("Writing probs...");

        int idx = path.lastIndexOf("/");
        try {
            FileUtils.forceMkdir(new File(path.substring(0, idx)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(path));
            for(String s: s2t2prob.keySet()){
                for(String t: s2t2prob.get(s).keySet()){
                    if(!s.trim().isEmpty() && !t.trim().isEmpty()) {
                        Double val = s2t2prob.get(s).get(t);
                        if(val>0.0000000001)
                            bw.write(s + "\t" + t + "\t" + s2t2prob.get(s).get(t) + "\n");
                    }
                }
            }
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Re-implementation of Jeff's transliterator
     * @param pairs
     */
    public void trainTransliterateProb(List<Pair<String, String>> pairs, List<Pair<String[], String[]>> test_pairs, String modelfile){

        int n_iter = 5;
        initProb(pairs);
        normalizeProbTGivenS();
        TransUtils.normalizeProb(t2s2prob);
//        computeAlignProb(pairs);
//        printProbs();

        List<Double> wordprobs = new ArrayList<>();
        pairs.forEach(x -> wordprobs.add(1.0));

        for(int iter = 0; iter < n_iter; iter++) {
            System.out.println("========== Iteration "+iter+" ===========");
            updateProb(pairs, wordprobs);
//            computeAlignProb(pairs);
//            printProbs();

//            writeProbs(modelfile + "." + iter);
            if (test_pairs != null)
                evalModel(test_pairs);
        }

        writeProbs(modelfile);
    }


    private List<List<String>> getAllSegment(String str){


        if(segcache.containsKey(str))
            return segcache.get(str);

        List<List<String>> ret = new ArrayList<>();

        // the base case: return the only character
        if(str.length()==1){
            ret.add(Arrays.asList(str));
            return ret;
        }

        // split at one place, and recurse the rest
        for(int i = 1; i < str.length(); i++){
            String head = str.substring(0, i);
            String tail = str.substring(i, str.length());

            for(List<String> segs: getAllSegment(tail)){
                List<String> tmp = new ArrayList<>();
                tmp.add(head);
                tmp.addAll(segs);
                ret.add(tmp);
            }
        }

        // add the full string, no split
        ret.add(Arrays.asList(str));

//        System.out.println(str);
//        System.out.println(ret.size());
        segcache.put(str, ret);

        return ret;
    }



    public List<List<Integer>> perm(List<Integer> input){

        List<List<Integer>> results = new ArrayList<>();

        if(input.size() == 1){
            results.add(input);
            return results;
        }

        for(int i = 0; i < input.size(); i++){

            int a = input.get(i);

            List<Integer> rest = input.stream().filter(x -> x != a).collect(toList());

            List<List<Integer>> perms = perm(rest);

            for(List<Integer> p: perms)
                p.add(input.get(i));

            results.addAll(perms);
        }

        return results;
    }

    public List<Integer> getBsetPerm(int k){
        List<Integer> idxarray = new ArrayList<>();
        for(int i = 0; i < k; i++) idxarray.add(i);

        List<List<Integer>> perms = perm(idxarray);

        double max_a = -1;
        List<Integer> max_perm = null;
        for(List<Integer> p: perms){
            double score = 1.0;
            for( int i = 0; i < p.size(); i++){
                String ilm = i + "_" + p.size() + "_" + p.size();
                String j = String.valueOf(p.get(i));
                score *= ilm2j2prob.get(ilm).get(j);
            }
            if(score > max_a){
                max_a = score;
                max_perm = p;
            }
        }

        System.out.println(k+" "+max_perm);

        return max_perm;
    }


    public double getPairScore(String src, String tgt){
//        Pair<String, String> key = new Pair<>(src, tgt);
//        if(genprobcache.containsKey(key))
//            return genprobcache.get(key);
        List<List<String>> src_segs = getAllSegment(src);
        List<List<String>> tgt_segs = getAllSegment(tgt);

        double aprob_sum = 0;

        Map<String, Map<String, Double>> tmp_prob = new HashMap<>();

        for (List<String> ss : src_segs) {
            for (List<String> ts : tgt_segs) {
                if (ss.size() == ts.size()) {

                    double aprob = 1;
                    for (int k = 0; k < ss.size(); k++) {
                        String s = ss.get(k);
                        String t = ts.get(k);
                        if(!s2t2prob.containsKey(s)){
                            aprob = 0;
                            break;
                        }
                        if(!s2t2prob.get(s).containsKey(t)){
                            aprob = 0;
                            break;
                        }
                        aprob *= s2t2prob.get(s).get(t);
//                        aprob *= t2s2prob.get(t).get(s);
                    }
                    aprob_sum += aprob;
                }
            }
        }

//        genprobcache.put(key, aprob_sum);

        return aprob_sum;
    }

    public List<String> generatePhraseAlign(String[] parts, SPModel model){

        model.setMaxCandidates(2);

        List<String> sources = new ArrayList<>();
        List<String> targets = new ArrayList<>();

        for(String part: parts){
            List<Pair<Double, String>> prediction = null;
            try {
                 prediction = model.Generate(part).toList();
//                for(int i = 0; i <prediction.size(); i++){
//                    double p = model.Probability(part, prediction.get(i).getSecond());
//                    System.out.println(prediction.get(i)+" "+p);
//                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            sources.add(part);
            if(prediction.size() > 0)
                targets.add(prediction.get(0).getSecond());
            else
                targets.add("");
        }

        int n = sources.size();
        List<List<String>> target_cands = new ArrayList<>();
        for(List<Integer> per: n2perms.get(n)){
            List<String> t = new ArrayList<>();
            for(int idx: per)
                t.add(targets.get(idx));
            target_cands.add(t);
        }

//        System.out.println("source:");
//        System.out.println("\t"+sources);
//        System.out.println("target cands:");
//        System.out.println("\t"+target_cands);


        String lm = n+"_"+n;

        Map<String, Double> results = new HashMap<>();

        // cand phrase
        for(List<String> cand: target_cands){

            double sum = 0;
            for(String a: lm2a2prob.get(lm).keySet()){

                List<Integer> align = Arrays.asList(a.split("_")).stream()
                        .map(x -> Integer.parseInt(x)).collect(Collectors.toList());

                double score = lm2a2prob.get(lm).get(a);
//                System.out.println(score);
                for(int i = 0; i < align.size(); i++) {
                    if(!cand.get(i).isEmpty() && align.get(i) < n) {
                        double s = getProdProb(sources.get(align.get(i)), cand.get(i));
//                        System.out.println(sources.get(align.get(i))+" ||| "+cand.get(i)+" "+s);
                        score *= s;
                    }
                }
                sum += score;
            }

            results.put(cand.stream().filter(x -> !x.isEmpty()).collect(joining(" ")), sum);
        }
//        System.out.println(results);

        List<String> sorted = results.entrySet().stream()
                .sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
                .map(x -> x.getKey())
                .collect(Collectors.toList());

        return sorted;
    }

    /**
     * For candidate generation in the wikifier
     * @param source
     * @param type
     * @return
     */
    public List<String> generatePhrase(String source, String type){

        return generatePhrase(source.toLowerCase().split("\\s+"), type2model.get(type));

    }

    public List<String> generatePhrase(String[] parts, SPModel model){
        model.setMaxCandidates(20);
//        if(model1!=null)
//            model1.setMaxCandidates(10);

        List<String> preds = new ArrayList<>();
        List<List<String>> predsl = new ArrayList<>();

        String phrase = Arrays.asList(parts).stream().collect(joining(" "));
        if(phrase_align.containsKey(phrase)){
            List<String> ret = new ArrayList<>();
            ret.add(phrase_align.get(phrase));
            return ret;
        }

        for(String part: parts) {
            try {
                List<Pair<Double, String>> prediction = new ArrayList<>();
                if(word_align.containsKey(part))
                    prediction.add(new Pair<>(1.0, word_align.get(part)));
                prediction.addAll(model.Generate(part).toList());


//                prediction = tmp1;


//                List<Pair<Double, String>> newscore =  new ArrayList<>();
//                for(Pair<Double, String> p: prediction){
//                    newscore.add(new Pair<>(model.Probability(part, p.getSecond()), p.getSecond()));
//                }
//
//                prediction = newscore.stream().sorted((x1, x2) -> Double.compare(x2.getFirst(), x1.getFirst()))
//                        .collect(Collectors.toList());

//                    if(model1 != null){
//                        Map<String, Double> t2prob = new HashMap<>();
//                        for(Pair<Double, String> p: prediction) {
//                            List<Pair<Double, String>> tmp = model1.Generate(p.getSecond()).toList();
//                            for(Pair<Double, String> t: tmp){
//                                if(t.getSecond().equals(part)){
//                                    t2prob.put(p.getSecond(), t.getFirst());
//                                }
//                            }
//                        }
//
//                        List<Map.Entry<String, Double>> sorted = t2prob.entrySet().stream()
//                                .sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue())).collect(Collectors.toList());
//
//                        prediction = new ArrayList<>();
//                        for(Map.Entry<String, Double> s: sorted)
//                            prediction.add(new Pair<>(s.getValue(), s.getKey()));
//                    }


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

        predsl = predsl.stream().filter(x -> x.size() == parts.length).collect(toList());


        // no reorder
        preds = predsl.stream().map(x -> x.stream().collect(joining(" "))).collect(toList());

        return preds;

    }


    public double evalModel(List<Pair<String[], String[]>> pairs, SPModel model, SPModel model1){

        memorization = new HashMap<>();

        double correctmrr = 0;
        double correctacc = 0;
        double totalf1 = 0;

        if(model1!=null)
            model1.setMaxCandidates(10);

        int cnt = 0;
        for(Pair<String[], String[]> pair: pairs){
//            if(cnt++%10 == 0) System.out.print(cnt+" "+pairs.size()+"\r");

            List<String> preds;

            if(eval_align)
                preds = generatePhraseAlign(pair.getFirst(), model);
            else
                preds = generatePhrase(pair.getFirst(), model);

            preds = preds.stream().filter(x -> !x.isEmpty()).collect(Collectors.toList());

            int bestindex = -1;

            String gold = null;
            if(preds.size()>0) {
                gold = Arrays.asList(pair.getSecond()).stream().collect(joining(" "));
                List<String> refs = Arrays.asList(gold);
                totalf1 += Utils.GetFuzzyF1(preds.get(0), refs);
//                System.out.println(totalf1+" "+refs.get(0)+" ||| "+preds.get(0));
            }

            int index = preds.indexOf(gold);
            if(bestindex == -1 || index < bestindex){
                bestindex = index;
            }

            if (bestindex >= 0) {
                correctmrr += 1.0 / (bestindex + 1);
                if(bestindex == 0){
                    correctacc += 1.0;
                }
            }
        }

        double mrr = correctmrr / (double)pairs.size();
        double acc = correctacc / (double)pairs.size();
        double f1 = totalf1 / (double)pairs.size();

        System.out.println("AVGMRR=" + mrr);
        System.out.println("AVGACC=" + acc);
        System.out.printf("AVGF1 = %.2f\n", f1*100);

        return f1;
    }

    public double evalModel(List<Pair<String[], String[]>> pairs){

        Map<String, Map<String, Double>> s2t2prob1 = new HashMap<>();

//        for(String t: t2s2prob.keySet()){
//            for(String s: t2s2prob.get(t).keySet()){
//                TransUtils.addToMap(s, t, t2s2prob.get(t).get(s), s2t2prob1);
//            }
//        }
//        TransUtils.normalizeProb(s2t2prob1);
//        SPModel model1 = new SPModel(s2t2prob1);
//        evalModel(pairs, model1, null);

        SPModel model = new SPModel(s2t2prob);
        return evalModel(pairs, model, null);

//        SPModel model2 = new SPModel(t2s2prob);
//        evalModel(pairs, model, model2);

    }

    public void evalModel(List<Pair<String[], String[]>> test_pairs, String modelfile){

        SPModel model = null;
        try {
            model = new SPModel(modelfile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        evalModel(test_pairs, model, null);
    }




    public void evalModel(String testfile, String modelfile){
        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile);

        SPModel model = null;
        try {
            model = new SPModel(modelfile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        evalModel(test_pairs, model, null);
    }


    public void calGenerateProb(List<Pair<String, String>> pairs){

        memorization = new HashMap<>();

        en2fo2prob = new HashMap<>();

//        ExecutorService executor = Executors.newFixedThreadPool(20);
        for(Pair<String, String> pair: pairs) {
            double score = getProdProb(pair.getFirst(), pair.getSecond());
//            System.out.println(pair+" "+score);
            TransUtils.addToMap(pair.getFirst(), pair.getSecond(), score, en2fo2prob);
//            executor.execute(new GenProbWorker(pair));
        }
//        executor.shutdown();
//        try {
//            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        TransUtils.normalizeProb(en2fo2prob);
    }

    public class GenProbWorker implements Runnable {

        private Pair<String, String> pair;

        public GenProbWorker(Pair<String, String> pair) {
            this.pair = pair;
        }

        @Override
        public void run() {

            double score = getPairScore(pair.getFirst(), pair.getSecond());

            synchronized (en2fo2prob) {
//                TransUtils.addToMap(pair.getSecond(), pair.getFirst(), score, en2fo2prob);
                TransUtils.addToMap(pair.getFirst(), pair.getSecond(), score, en2fo2prob);
//                        addToMap(en_word, fo_word, e2f2prob.get(en_word).get(fo_word), en2fo2prob);
            }
        }
    }

    private List<TitlePair> initJointProb1(List<Pair<String[], String[]>> pairs){

        wcnt = new HashMap<>();
        for(Pair<String[], String[]> p: pairs){
            for(String s: p.getFirst()){
                if(wcnt.containsKey(s))
                    wcnt.put(s, wcnt.get(s)+1.0);
                else
                    wcnt.put(s,1.0);
            }
        }

        freq = wcnt.entrySet().stream().sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
                .map(x -> x.getKey())
                .collect(Collectors.toList())
                .subList(0, Math.min(wcnt.size(),20));


//        TransUtils.normalizeProb1(wcnt);


        List<TitlePair> ret = new ArrayList<>();
        System.out.println("Initialing probabilities...");
        int cnt = 0;
        for(Pair<String[], String[]> pair: pairs) {
//            System.out.print((cnt++) + " "+pair.getFirst().length+" "+pair.getSecond().length+"\r");

            // for looking up in prediction
            phrase_align.put(Arrays.asList(pair.getFirst()).stream().collect(joining(" ")), Arrays.asList(pair.getSecond()).stream().collect(joining(" ")));

            TitlePair tpair = new TitlePair(pair);
            tpair.populateAllAssignments();   // only for this new method
            ret.add(tpair);

            // initialize alignment and word generation probs
            for(String a: tpair.align2pairs.keySet()){
                TransUtils.addToMap(tpair.lm, a, 1.0, lm2a2prob);
                for(Pair<String, String> wpair: tpair.align2pairs.get(a)){
                    TransUtils.addToMap(wpair.getFirst(), wpair.getSecond(), 1.0, e2f2prob);
                }
            }

            // initialize transliteration probs
            for(String a: tpair.align2pairs.keySet()) {
                for(Pair<String, String> p: tpair.align2pairs.get(a)) {
                    initProdProb(p.getFirst(), p.getSecond());
                }
            }
        }
        normalizeProbTGivenS();
//        TransUtils.normalizeProb(t2s2prob);
        TransUtils.normalizeProb(lm2a2prob);
        TransUtils.normalizeProb(e2f2prob);
        return ret;

    }

    public void updateJointProbs1(List<TitlePair> tpairs){

        List<Pair<String, String>> allpairs = tpairs.stream()
                .flatMap(x -> x.align2pairs.values().stream().flatMap(y -> y.stream()))
                .collect(toList());
        calGenerateProb(allpairs);

        List<Pair<String, String>> train_pairs = new ArrayList<>();  // transliteration training data
        List<Double> wordprobs = new ArrayList<>();
//        System.out.println("Updating q...");
        for(TitlePair tpair: tpairs){

            Map<String, Double> a2prob = new HashMap<>();
            double pas_sum = 0;
            for(String a: tpair.align2pairs.keySet()){
                double pairprob = 1;
                for(Pair<String, String> wpair: tpair.align2pairs.get(a)){

//                    if(!en2fo2prob.containsKey(wpair.getSecond()))
//                        System.out.println(wpair.getSecond());
//                    pairprob *= en2fo2prob.get(wpair.getSecond()).get(wpair.getFirst());
                    pairprob *= en2fo2prob.get(wpair.getFirst()).get(wpair.getSecond());

                }

                double pa = lm2a2prob.get(tpair.lm).get(a) * pairprob;
                a2prob.put(a, pa);
                pas_sum += pa;
            }

            for (String a : a2prob.keySet()) {
                // P(alignment | this title pair) under the current parameters
                double ap = a2prob.get(a) / pas_sum;
                TransUtils.addToMap(tpair.lm, a, ap, lm2a2c);
                TransUtils.addToMap(tpair.lm, ap, lm2c);

                for(Pair<String, String> wpair: tpair.align2pairs.get(a)){

                    // update the word generation prob based on alignments
                    TransUtils.addToMap(wpair.getFirst(), wpair.getSecond(), ap, e2f2c);
                    TransUtils.addToMap(wpair.getFirst(), ap, e2c);

                    if(!freq.contains(wpair.getFirst())) {
                        train_pairs.add(wpair);
////                    train_pairs.add(new Pair<>(wpair.getSecond(), wpair.getFirst()));
                        wordprobs.add(ap);
                    }
//                    wordprobs.add(1.0);
                }
            }
        }

        // Update q(a|l,m) = c(a|l,m)/c(l,m)
        for(String lm: lm2a2prob.keySet()){
            for(String a: lm2a2prob.get(lm).keySet()){
                lm2a2prob.get(lm).put(a, lm2a2c.get(lm).get(a)/lm2c.get(lm));
            }
        }

        // Update t(f|e)
        for(String e: e2c.keySet()){
            for(String f: e2f2c.get(e).keySet()){
                e2f2prob.get(e).put(f, e2f2c.get(e).get(f)/e2c.get(e));
            }
        }

//        int cnt = 0;
//        for(TitlePair tpair: tpairs) {
//            for (String a : tpair.align2pairs.keySet()) {
//                for (Pair<String, String> wpair : tpair.align2pairs.get(a)) {
//                    wordprobs.set(cnt, wordprobs.get(cnt)*e2f2prob.get(wpair.getFirst()).get(wpair.getSecond()));
//                    cnt++;
//                }
//            }
//        }

        updateProb(train_pairs, wordprobs);
    }

    public void updateJointProbs(List<Pair<String[], String[]>> pairs){

        List<Pair<String, String>> allpairs = new ArrayList<>();
        for(Pair<String[], String[]> pair: pairs) {
            for (int i = 0; i < pair.getSecond().length; i++) {
                String en_word = pair.getSecond()[i];
                for (int j = 0; j < pair.getFirst().length; j++) {
                    String fo_word = pair.getFirst()[j];
                    allpairs.add(new Pair<>(fo_word, en_word));
                }
            }
        }
        calGenerateProb(allpairs);

        List<Double> wordprobs = new ArrayList<>();
        System.out.println("Updating q...");
        for(Pair<String[], String[]> pair: pairs){
            String[] e = pair.getSecond();
            String[] f = pair.getFirst();
            int l = e.length;
            int m = f.length;
            for(int i = 0; i < l; i++){
                String ilm = i + "_" + l + "_" + m;

                double sum_pij = 0;
                List<Double> pijs = new ArrayList<>();
                for(int j = 0; j < m; j++){
                    Double q = ilm2j2prob.get(ilm).get(String.valueOf(j));
                    Double wordprob = en2fo2prob.get(e[i]).get(f[j]);
//                    Double wordprob = e2f2prob.get(e[i]).get(f[j]);
                    double pij = q * wordprob;
                    pijs.add(pij);
                    sum_pij += pij;
                }

                // Updating counts for c(j|i,l,m) and c(i,l,m)
                for(int j = 0; j < m; j++){
                    double pij = pijs.get(j) / sum_pij;
                    wordprobs.add(pij);
                    TransUtils.addToMap(ilm, String.valueOf(j), pij, ilm2j2c);
                    TransUtils.addToMap(ilm, pij, ilm2c);

                    TransUtils.addToMap(e[i], f[j], pij, e2f2c);
                    TransUtils.addToMap(e[i], pij, e2c);

                }
            }
        }

        // Update q(j|i,l,m) = c(j|i,l,m)/c(i,l,m)
        for(String ilm: ilm2j2prob.keySet()){
            for(String j: ilm2j2prob.get(ilm).keySet()){
                ilm2j2prob.get(ilm).put(j, ilm2j2c.get(ilm).get(j)/ilm2c.get(ilm));
            }
        }

        // Update t(f|e)
        for(String e: e2c.keySet()){
            for(String f: e2f2c.get(e).keySet()){
                e2f2prob.get(e).put(f, e2f2c.get(e).get(f)/e2c.get(e));
            }
        }

        // pick the most probable alignments to generate training pairs for transliteration

        List<Pair<String, String>> train_pairs = new ArrayList<>();
        for(Pair<String[], String[]> pair: pairs){
            String[] e = pair.getSecond();
            String[] f = pair.getFirst();
            int l = e.length;
            int m = f.length;
            for(int i = 0; i < l; i++) {
                String ilm = i + "_" + l + "_" + m;

                for(int j = 0; j < m; j++){
                    train_pairs.add(new Pair<>(f[j], e[i]));
                }

//                List<Map.Entry<String, Double>> sort_prob = ilm2j2prob.get(ilm).entrySet().stream()
//                        .sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
//                        .collect(Collectors.toList());
//
//                int topidx = Integer.parseInt(sort_prob.get(0).getKey());
//
//                train_pairs.add(new Pair<>(f[topidx], e[i]));
//                wordprobs.add(1.0);
            }
        }

        updateProb(train_pairs, wordprobs);
        normalizeProbTGivenS();
    }






    public Pair<Double, Integer> jointTrainAlignTrans(String infile, String testfile, String devfile, String modelfile){

        List<Pair<String[], String[]>> test_pairs = TransUtils.readPairs(testfile);
        List<Pair<String[], String[]>> train_pairs = TransUtils.readPairs(infile);
        List<Pair<String[], String[]>> dev_pairs = TransUtils.readPairs(devfile);



//        alignAndLearn(pairs, test_pairs,"tmp");

        // initialize all probabilities
//        initJointProb(part_pairs);
        List<TitlePair> tpairs = initJointProb1(train_pairs);

//        int degree_sum = 0;
//        for(String s: s2t2prob.keySet()){
//            degree_sum += s2t2prob.get(s).size();
//        }
//        System.out.println((double)degree_sum/s2t2prob.size());
//        System.exit(-1);


        int iter = 10;
        double max_f1 = 0, tf = 0;
        int max_iter = 0;
        for(int i = 0; i < iter; i++) {
            System.out.println("---------------- Iteration "+i+"---------------");
//            updateJointProbs(part_pairs);
            updateJointProbs1(tpairs);
//            writeProbs(modelfile+".iter"+i);
//            writeAlignProbs(modelfile+".iter"+i);

            for(String src: e2f2prob.keySet()){
                List<Map.Entry<String, Double>> p = e2f2prob.get(src).entrySet().stream()
                        .sorted((x1, x2) -> Double.compare(x2.getValue(), x1.getValue()))
                        .collect(Collectors.toList());
                word_align.put(src, p.get(0).getKey());
            }
            double f1 = evalModel(dev_pairs);
            double test_f1 = evalModel(test_pairs);
            if(f1 > max_f1){
                max_f1 = f1;
                max_iter = i;
                tf = test_f1;
            }
            System.out.println("Best iteration: "+max_iter+" f1: dev "+max_f1+" test "+tf);
        }
        writeProbs(modelfile);
        return new Pair<Double, Integer>(tf, test_pairs.size());

//        evalModel(test_pairs, modelfile);
//        writeAlignProbs(modelfile+".align");

    }



    public static void main(String[] args) {

        TitleTranslator tt = new TitleTranslator();

        String lang = args[0];
        if(lang.equals("zh"))
            TransUtils.del = "·";

        List<String> types = Arrays.asList("loc", "org", "per");

        String dir = "/shared/corpora/ner/transliteration/"+lang+"/";


        double total_f1 = 0;
        int total_pair = 0;

        String suffix = "";

        for(String type: types) {
            String infile = dir+type+"/train.select"+suffix;
            String testfile = dir + type + "/test.select"+suffix;
            String devfile = dir + type + "/dev.select"+suffix;
//            tt.printJanusData(infile, testfile, lang, type);
//        String modelfile = "/shared/corpora/ner/gazetteers/"+lang+"/model/probsss";
            String modelfile = dir + type + "/models/new";
//            tt.alignAndLearn(infile, testfile, modelfile);

            Pair<Double, Integer> results = tt.jointTrainAlignTrans(infile, testfile, devfile, modelfile);
            total_f1 += results.getFirst()*results.getSecond();
            total_pair += results.getSecond();
        }

        System.out.printf("%.2f\n", total_f1*100/total_pair);

//        tt.evalModel(testfile, modelfile);
    }
}
